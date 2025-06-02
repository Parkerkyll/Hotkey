package com.parker.hotkey.data.repository

import com.parker.hotkey.data.local.dao.GeohashLastSyncDao
import com.parker.hotkey.data.local.dao.MarkerDao
import com.parker.hotkey.data.local.dao.MemoDao
import com.parker.hotkey.data.local.entity.GeohashLastSyncEntity
import com.parker.hotkey.data.mapper.MarkerMapper
import com.parker.hotkey.data.mapper.MemoMapper
import com.parker.hotkey.data.remote.network.ConnectionStateMonitor
import com.parker.hotkey.data.remote.sync.api.HotkeyApi
import com.parker.hotkey.data.remote.sync.dto.request.CreateMarkerRequestDto
import com.parker.hotkey.data.remote.sync.dto.request.CreateMemoRequestDto
import com.parker.hotkey.data.remote.sync.dto.request.DeleteMarkerRequestDto
import com.parker.hotkey.data.remote.sync.dto.request.DeleteMemoRequestDto
import com.parker.hotkey.data.remote.sync.dto.request.MarkerSyncInfo
import com.parker.hotkey.data.remote.sync.dto.request.MemoSyncInfo
import com.parker.hotkey.data.remote.sync.dto.request.SyncRequestDto
import com.parker.hotkey.data.remote.sync.dto.response.GeohashResponseDto
import com.parker.hotkey.data.remote.sync.util.RetryUtil
import com.parker.hotkey.data.remote.sync.util.SyncException
import com.parker.hotkey.data.remote.util.ApiPriority
import com.parker.hotkey.data.remote.util.ApiRequestManager
import com.parker.hotkey.data.remote.util.NavigationReturnException
import com.parker.hotkey.data.remote.util.SyncDebouncedException
import com.parker.hotkey.domain.manager.GeohashManager
import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.model.Memo
import com.parker.hotkey.domain.repository.AuthRepository
import com.parker.hotkey.domain.repository.SyncRepository
import com.parker.hotkey.domain.util.AppStateManager
import com.parker.hotkey.domain.util.AppStatus
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.IOException
import retrofit2.Response
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.DelicateCoroutinesApi
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * SyncRepository 인터페이스 구현체
 */
@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val api: HotkeyApi,
    private val markerDao: MarkerDao,
    private val memoDao: MemoDao,
    private val geohashLastSyncDao: GeohashLastSyncDao,
    private val markerMapper: MarkerMapper,
    private val memoMapper: MemoMapper,
    private val connectionStateMonitor: ConnectionStateMonitor,
    private val authRepository: AuthRepository,
    private val geohashManager: GeohashManager,
    private val apiRequestManager: ApiRequestManager,
    private val appStateManager: AppStateManager
) : SyncRepository {
    
    // 진행 중인 요청을 저장하는 맵
    private val ongoingRequests = ConcurrentHashMap<String, Deferred<Response<GeohashResponseDto>>>()
    
    // 동시 API 호출 방지를 위한 Mutex
    private val apiMutex = Mutex()
    
    // 마지막으로 처리된 요청 추적을 위한 맵 (중복 호출 추가 방지)
    private val lastProcessedRequest = ConcurrentHashMap<String, Long>()
    
    override suspend fun loadInitialData(geohash: String): Boolean {
        // 고유 식별자로 API 호출 추적
        val callId = UUID.randomUUID().toString().take(6)
        
        // API 호출 전 Mutex로 동기화
        return apiMutex.withLock {
            // 로깅
            Timber.tag("API_FLOW").d("[$callId] 초기 데이터 로드 요청 시작 - geohash: $geohash")
            Timber.tag("API_FLOW").d("[$callId] 호출 스택: ${getCallStack()}")
            
            // 앱 업데이트 상태 확인
            if (appStateManager.currentAppStatus.value == AppStatus.AFTER_UPDATE) {
                Timber.tag("API_FLOW").d("[$callId] 앱 업데이트 후 상태 감지 - 포그라운드 API 호출 방지")
                // WorkManager를 통한 백그라운드 동기화가 이미 진행 중이므로 API 호출 없이 로컬 데이터만 반환
                val localMarkers = markerDao.getMarkersByGeohash(listOf(geohash), geohash)
                if (localMarkers.isNotEmpty()) {
                    Timber.tag("API_FLOW").d("[$callId] 앱 업데이트 후 상태: 로컬 데이터 사용 (${localMarkers.size}개 마커)")
                    return@withLock true
                }
                // 로컬 데이터가 없을 경우에만 네트워크 호출 진행
                Timber.tag("API_FLOW").d("[$callId] 앱 업데이트 후 상태: 로컬 데이터 없음, 네트워크 호출 진행")
            }
            
            // 네비게이션 복귀 상태 확인 (기존 코드)
            if (appStateManager.currentAppStatus.value == AppStatus.NAVIGATION_RETURN) {
                Timber.tag("API_FLOW").d("[$callId] 네비게이션 복귀 상태 감지 - API 호출 방지")
                val localMarkers = markerDao.getMarkersByGeohash(listOf(geohash), geohash)
                if (localMarkers.isNotEmpty()) {
                    Timber.tag("API_FLOW").d("[$callId] 네비게이션 복귀 상태: 로컬 데이터 사용 (${localMarkers.size}개 마커)")
                    return@withLock true
                }
                // 로컬 데이터가 없으면 예외 발생
                Timber.tag("API_FLOW").w("[$callId] 네비게이션 복귀 상태: 로컬 데이터 없음")
                throw NavigationReturnException("네비게이션 복귀 상태에서 로컬 데이터를 찾을 수 없습니다.")
            }
            
            // 최근 요청 확인 (300ms 이내 동일 geohash 요청은 중복으로 간주)
            val now = System.currentTimeMillis()
            val lastProcessed = lastProcessedRequest[geohash] ?: 0L
            
            if (now - lastProcessed < 300) {
                Timber.tag("API_FLOW").d("[$callId] 최근(${now-lastProcessed}ms 이내)에 동일한 요청이 처리됨 - 중복 요청 무시: $geohash")
                return@withLock true
            }
            
            // 현재 요청 기록
            lastProcessedRequest[geohash] = now
            
            // 지오해시 방문 기록 저장
            geohashManager.recordVisit(geohash)
            
            // 네트워크 연결 여부 확인 - 연결이 없어도 로컬 데이터 반환
            val isConnected = isNetworkConnected()
            if (!isConnected) {
                Timber.tag("API_FLOW").w("[$callId] 네트워크 연결이 없음 - 로컬 데이터만 사용")
            }
            
            try {
                // 1. 로컬 DB에서 먼저 데이터 확인 (있으면 바로 성공 반환)
                val loadStartTime = System.currentTimeMillis()
                val localMarkers = markerDao.getMarkersByGeohash(listOf(geohash), geohash)
                val loadDuration = System.currentTimeMillis() - loadStartTime
                
                if (localMarkers.isNotEmpty()) {
                    Timber.tag("API_FLOW").d("[$callId] 로컬 DB에서 ${localMarkers.size}개 마커 데이터 발견 (${loadDuration}ms) - 즉시 반환")
                    
                    // 로컬 데이터가 있음을 표시
                    geohashManager.markAsHavingLocalData(geohash)
                    
                    // 네트워크 연결되어 있으면 백그라운드에서 동기화 시작
                    if (isConnected) {
                        Timber.tag("API_FLOW").d("[$callId] 백그라운드에서 데이터 동기화 시작 - geohash: $geohash")
                        // 비동기로 네트워크 호출하여 백그라운드 동기화
                        backgroundSyncGeohash(geohash, callId)
                    }
                    
                    return@withLock true
                }
                
                // 2. 로컬 DB에 데이터가 없고 네트워크 연결 안 되어 있으면 실패
                if (!isConnected) {
                    Timber.tag("API_FLOW").w("[$callId] 로컬 데이터 없음 & 네트워크 연결 없음 - 초기 데이터 로드 실패")
                    return@withLock false
                }
                
                // 3. 네트워크에서 데이터 로드 (로컬 데이터 없는 경우)
                Timber.tag("API_FLOW").d("[$callId] 로컬 데이터 없음 - 네트워크에서 데이터 로드 시작")
                val apiStartTime = System.currentTimeMillis()
                
                // ApiRequestManager를 사용하여 API 호출
                val requestKey = "initialData:$geohash"
                try {
                    val response = apiRequestManager.executeRequest(
                        requestKey = requestKey,
                        priority = ApiPriority.FOREGROUND_NORMAL,
                        request = {
                            RetryUtil.retryApiCall {
                                Timber.tag("API_FLOW").d("[$callId] API 호출 실행: getInitialData($geohash)")
                                api.getInitialData(geohash)
                            }
                        }
                    )
                    
                    val apiDuration = System.currentTimeMillis() - apiStartTime
                    Timber.tag("API_FLOW").d("[$callId] API 호출 완료: ${apiDuration}ms, 상태코드: ${response.code()}")
                    
                    if (response.isSuccessful) {
                        val data = response.body()
                        
                        if (data != null && data.success) {
                            // 응답 데이터 세부 정보 로깅
                            Timber.tag("API_FLOW").d("[$callId] 서버 응답 성공: 마커 ${data.markers.size}개, 메모 ${data.memos.size}개")
                            
                            // 데이터 변환 및 DB 저장
                            val markers = data.markers.map(markerMapper::fromDto)
                            val memos = data.memos.map(memoMapper::fromDto)
                            
                            try {
                                val dbStartTime = System.currentTimeMillis()
                                
                                // 로컬 DB에 저장
                                val markerEntities = markers.map(markerMapper::toEntity)
                                markerDao.insertMarkers(markerEntities)
                                
                                val memoEntities = memos.map(memoMapper::toEntity)
                                memoDao.insertMemos(memoEntities)
                                
                                val dbDuration = System.currentTimeMillis() - dbStartTime
                                Timber.tag("API_FLOW").d("[$callId] DB 저장 완료: ${dbDuration}ms")
                                
                                // 마지막 동기화 시간 업데이트
                                val serverTime = data.serverTime ?: System.currentTimeMillis()
                                updateLastSyncTimestamp(geohash, serverTime)
                                
                                // 데이터가 있고 서버와 동기화 완료 표시
                                if (markerEntities.isNotEmpty() || memoEntities.isNotEmpty()) {
                                    geohashManager.markAsHavingLocalData(geohash)
                                }
                                geohashManager.markAsSynced(geohash)
                                
                                Timber.tag("API_FLOW").d("[$callId] 서버 데이터 처리 완료: 총 소요시간 ${System.currentTimeMillis() - apiStartTime}ms")
                                return@withLock true
                            } catch (e: Exception) {
                                Timber.tag("API_FLOW").e(e, "[$callId] DB 저장 중 오류 발생")
                            }
                        } else {
                            Timber.tag("API_FLOW").e("[$callId] 지역 데이터 로드 실패: ${response.code()}, 메시지: ${response.message()}")
                        }
                    } else {
                        // 오류 처리
                        Timber.tag("API_FLOW").e("[$callId] 지역 데이터 로드 실패: ${response.code()}, 메시지: ${response.message()}")
                        handleApiError(response.code(), response.message(), response.errorBody()?.string())
                    }
                } catch (e: SyncDebouncedException) {
                    // 최근 3분내 동기화 요청이 있어 디바운싱된 경우
                    Timber.tag("API_FLOW").d("[$callId] 동기화 디바운싱 적용됨 - 로컬 데이터만 사용: ${e.message}")
                    
                    // 로컬 데이터가 있는지 확인
                    if (geohashManager.hasLocalData(geohash)) {
                        Timber.tag("API_FLOW").d("[$callId] 로컬 데이터 존재 - 디바운싱 후 로컬 데이터만 사용")
                        return@withLock true
                    } else {
                        // 로컬 데이터가 없고 디바운싱 적용된 경우 - 이전 데이터가 없으므로 실패 처리
                        Timber.tag("API_FLOW").w("[$callId] 로컬 데이터 없음 & 디바운싱 적용 - 초기 데이터 로드 실패")
                        return@withLock false
                    }
                } catch (e: NavigationReturnException) {
                    Timber.tag("API_FLOW").d("[$callId] 네비게이션 복귀 상태에서 API 호출 방지됨 - 로컬 데이터만 사용")
                    
                    // 로컬 데이터가 있을 경우 성공으로 간주
                    if (geohashManager.hasLocalData(geohash)) {
                        Timber.tag("API_FLOW").d("[$callId] 로컬 데이터 존재 - 네비게이션 복귀 시 로컬 데이터만 사용")
                        return@withLock true
                    } else {
                        Timber.tag("API_FLOW").w("[$callId] 로컬 데이터 없음 - 네비게이션 복귀 실패")
                        return@withLock false
                    }
                } catch (e: Exception) {
                    Timber.tag("API_FLOW").e(e, "[$callId] API 호출 중 오류 발생: ${e.message}")
                }
            } catch (e: Exception) {
                Timber.tag("API_FLOW").e(e, "[$callId] 지역 데이터 로드 중 오류 발생")
            }
            
            return@withLock false
        }
    }
    
    /**
     * 백그라운드에서 지역 데이터를 동기화합니다 (오프라인 우선 전략)
     */
    private fun backgroundSyncGeohash(geohash: String, callId: String = UUID.randomUUID().toString().take(6)) {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            try {
                Timber.tag("API_FLOW").d("[$callId] 백그라운드 동기화 시작: geohash=$geohash")
                
                // 최근 동기화 시간 확인 및 디바운싱 적용
                val lastSyncTime = geohashLastSyncDao.getLastSyncTimestamp(geohash) ?: 0
                val now = System.currentTimeMillis()
                val timeSinceLastSync = now - lastSyncTime
                
                // 10분(600,000ms) 이내에 동기화한 경우 스킵
                if (lastSyncTime > 0 && timeSinceLastSync < 600_000) {
                    Timber.tag("API_FLOW").d("[$callId] 최근에 동기화됨 (${timeSinceLastSync/1000}초 전) - 백그라운드 동기화 스킵")
                    return@launch
                }
                
                val apiStartTime = System.currentTimeMillis()
                val response = RetryUtil.retryApiCall {
                    Timber.tag("API_FLOW").d("[$callId] 백그라운드 API 호출: getInitialData($geohash)")
                    api.getInitialData(geohash)
                }
                
                val apiDuration = System.currentTimeMillis() - apiStartTime
                Timber.tag("API_FLOW").d("[$callId] 백그라운드 API 응답 수신: ${apiDuration}ms, 상태코드: ${response.code()}")
                
                if (response.isSuccessful) {
                    val data = response.body()
                    
                    if (data != null && data.success) {
                        Timber.tag("API_FLOW").d("[$callId] 백그라운드 동기화 성공: 마커 ${data.markers.size}개, 메모 ${data.memos.size}개")
                        
                        // 마커 및 메모 데이터 처리
                        val markers = data.markers.map(markerMapper::fromDto)
                        val memos = data.memos.map(memoMapper::fromDto)
                        
                        val dbStartTime = System.currentTimeMillis()
                        
                        // 로컬 DB에 저장
                        val markerEntities = markers.map(markerMapper::toEntity)
                        markerDao.insertMarkers(markerEntities)
                        
                        val memoEntities = memos.map(memoMapper::toEntity)
                        memoDao.insertMemos(memoEntities)
                        
                        val dbDuration = System.currentTimeMillis() - dbStartTime
                        Timber.tag("API_FLOW").d("[$callId] 백그라운드 DB 저장 완료: ${dbDuration}ms")
                        
                        // 마지막 동기화 시간 업데이트
                        val serverTime = data.serverTime ?: System.currentTimeMillis()
                        updateLastSyncTimestamp(geohash, serverTime)
                        
                        // 서버와 동기화 완료 표시
                        geohashManager.markAsSynced(geohash)
                        
                        Timber.tag("API_FLOW").d("[$callId] 백그라운드 동기화 완료: 총 소요시간 ${System.currentTimeMillis() - apiStartTime}ms")
                    } else {
                        Timber.tag("API_FLOW").e("[$callId] 백그라운드 동기화 실패: ${data?.success ?: false}, 알 수 없는 오류")
                    }
                } else {
                    Timber.tag("API_FLOW").e("[$callId] 백그라운드 동기화 실패: ${response.code()}, ${response.message()}")
                }
            } catch (e: Exception) {
                Timber.tag("API_FLOW").e(e, "[$callId] 백그라운드 동기화 중 오류 발생")
            }
        }
    }
    
    /**
     * LastSync 기반 개별 동기화 수행
     */
    override suspend fun syncDataWithLastSync(
        geohash: String, 
        markers: List<Marker>,
        memos: List<Memo>
    ): Boolean {
        if (!isNetworkConnected()) {
            Timber.w("네트워크 연결이 없어 동기화를 수행할 수 없습니다.")
            return false
        }
        
        try {
            // Room DB에서 지역별 마지막 동기화 타임스탬프 가져오기
            val lastSyncTimestamp = geohashLastSyncDao.getLastSyncTimestamp(geohash) ?: 0
            
            Timber.d("지역 기반 동기화 시작 - geohash: $geohash, lastSyncTimestamp: $lastSyncTimestamp")
            
            // 지역 기반 동기화 요청 생성
            val request = SyncRequestDto(
                geohash = geohash,
                syncType = "GEOHASH",
                lastSyncTimestamp = lastSyncTimestamp
            )
            
            Timber.d("지역 기반 동기화 요청: $request")
            
            try {
                // API 호출 (재시도 전략 적용)
                val requestKey = "syncData:$geohash:$lastSyncTimestamp"
                val response = apiRequestManager.executeRequest(
                    requestKey = requestKey,
                    priority = ApiPriority.FOREGROUND_NORMAL,
                    request = {
                        RetryUtil.retryApiCall {
                            api.syncData(request)
                        }
                    }
                )
                
                if (response.isSuccessful) {
                    val data = response.body()
                    
                    if (data != null && data.success) {
                        // 마커 및 메모 데이터 처리
                        val updatedMarkers = data.markers.map(markerMapper::fromDto)
                        val updatedMemos = data.memos.map(memoMapper::fromDto)
                        
                        // 삭제된 항목 처리
                        val deletedMarkerIds = data.deletedItems
                            .filter { it.type == "MARKER" }
                            .map { it.id }
                        
                        val deletedMemoIds = data.deletedItems
                            .filter { it.type == "MEMO" }
                            .map { it.id }
                        
                        // 로컬 DB 업데이트
                        markerDao.insertMarkers(updatedMarkers.map(markerMapper::toEntity))
                        memoDao.insertMemos(updatedMemos.map(memoMapper::toEntity))
                        
                        if (deletedMarkerIds.isNotEmpty()) {
                            markerDao.deleteByIds(deletedMarkerIds)
                        }
                        
                        if (deletedMemoIds.isNotEmpty()) {
                            memoDao.deleteByIds(deletedMemoIds)
                        }
                        
                        // 성공 시 마지막 동기화 타임스탬프 업데이트
                        val serverTime = data.serverTime ?: System.currentTimeMillis()
                        updateLastSyncTimestamp(geohash, serverTime)
                        
                        // 서버와 동기화 완료 표시
                        geohashManager.markAsSynced(geohash)
                        
                        Timber.d("지역 기반 동기화 성공: 마커 ${updatedMarkers.size}개, 메모 ${updatedMemos.size}개, " +
                                "삭제된 마커 ${deletedMarkerIds.size}개, 삭제된 메모 ${deletedMemoIds.size}개")
                        Timber.d("새로운 lastSyncTimestamp 저장: $serverTime")
                        
                        return true
                    } else {
                        Timber.e("지역 기반 동기화 실패: ${response.code()}, 메시지: ${response.message()}")
                        Timber.e("응답 데이터: ${data?.toString() ?: "null"}")
                    }
                } else {
                    // 오류 처리
                    Timber.e("지역 기반 동기화 실패: ${response.code()}, 메시지: ${response.message()}")
                    Timber.e("오류 바디: ${response.errorBody()?.string() ?: "null"}")
                    handleApiError(response.code(), response.message(), response.errorBody()?.string())
                }
            } catch (e: SyncDebouncedException) {
                // 최근 3분내 동기화 요청이 있어 디바운싱된 경우
                Timber.d("동기화 디바운싱 적용됨 - 로컬 데이터만 사용: ${e.message}")
                
                // 로컬 데이터가 있으면 성공으로 간주
                if (geohashManager.hasLocalData(geohash)) {
                    Timber.d("로컬 데이터 존재 - 디바운싱 적용 시 로컬 데이터 사용")
                    return true
                } else {
                    // 동기화가 디바운싱되었지만 로컬 데이터가 없는 경우 - 성공으로 간주
                    // (초기 데이터 로드와 달리 동기화는 필수가 아님)
                    Timber.d("로컬 데이터 없음 & 디바운싱 적용 - 작업은 성공으로 간주")
                    return true
                }
            } catch (e: NavigationReturnException) {
                Timber.d("네비게이션 복귀 상태에서 동기화 API 호출 방지됨 - 로컬 데이터만 사용")
                
                // 로컬 데이터가 있는지 확인하고 있으면 성공으로 처리
                if (geohashManager.hasLocalData(geohash)) {
                    Timber.d("로컬 데이터 존재 - 네비게이션 복귀 시 로컬 데이터만 사용")
                    return true
                } else {
                    Timber.d("로컬 데이터 없음 - 작업은 성공으로 간주 (네비게이션 복귀 상태)")
                    // 로컬 데이터가 없어도 네비게이션 복귀 상태에서는 성공으로 간주
                    return true
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "지역 기반 동기화 중 오류 발생")
        }
        
        return false
    }
    
    /**
     * 마지막 동기화 타임스탬프를 Room DB에 업데이트합니다.
     */
    private suspend fun updateLastSyncTimestamp(geohash: String, timestamp: Long) {
        try {
            geohashLastSyncDao.updateLastSyncTimestamp(
                GeohashLastSyncEntity(
                    geohash = geohash,
                    lastSyncTimestamp = timestamp
                )
            )
            Timber.d("마지막 동기화 타임스탬프 업데이트: geohash=$geohash, timestamp=$timestamp")
        } catch (e: Exception) {
            Timber.e(e, "마지막 동기화 타임스탬프 업데이트 실패")
        }
    }
    
    /**
     * 마커 생성 API 호출
     */
    override suspend fun createMarker(marker: Marker): Marker? {
        try {
            val callId = UUID.randomUUID().toString().take(6)
            Timber.tag("API_FLOW").d("[$callId] 마커 생성 API 호출 시작: markerId=${marker.id}")
            
            // 네트워크 연결 확인
            if (!isNetworkConnected()) {
                Timber.tag("API_FLOW").w("[$callId] 네트워크 연결 없음 - 마커 생성 API 호출 실패")
                return null
            }
            
            // 해시된 사용자 ID 가져오기
            val hashedUserId = authRepository.getHashedUserId()
            
            // 요청 데이터 구성
            val request = CreateMarkerRequestDto(
                id = marker.id,
                userId = hashedUserId,
                geohash = marker.geohash,
                latitude = marker.latitude,
                longitude = marker.longitude
            )
            
            // 요청 키 생성
            val requestKey = "createMarker:${marker.id}"
            
            try {
                // ApiRequestManager를 사용하여 API 호출
                val response = apiRequestManager.executeRequest(
                    requestKey = requestKey,
                    priority = ApiPriority.FOREGROUND_NORMAL,
                    request = {
                        RetryUtil.retryApiCall {
                            Timber.tag("API_FLOW").d("[$callId] API 호출 실행: createMarker(${marker.id})")
                            api.createMarker(request)
                        }
                    }
                )
                
                if (response.isSuccessful) {
                    val result = response.body()
                    
                    if (result != null && result.success) {
                        Timber.tag("API_FLOW").d("[$callId] 마커 생성 API 성공: markerId=${marker.id}")
                        
                        // 서버에서 반환한 마커 정보로 업데이트
                        val updatedMarker = markerMapper.fromDto(result.marker)
                        
                        // 로컬 DB 업데이트
                        markerDao.insertMarker(markerMapper.toEntity(updatedMarker))
                        
                        return updatedMarker
                    } else {
                        Timber.tag("API_FLOW").w("[$callId] 마커 생성 API 실패: ${result?.success}")
                    }
                } else {
                    Timber.tag("API_FLOW").e(
                        "[$callId] 마커 생성 API 오류: ${response.code()}, ${response.message()}"
                    )
                    handleApiError(response.code(), response.message(), response.errorBody()?.string())
                }
            } catch (e: NavigationReturnException) {
                Timber.tag("API_FLOW").d("[$callId] 네비게이션 복귀 상태에서 마커 생성 API 호출 방지됨")
                
                // 로컬 DB에서 마커 조회 (이미 저장된 경우)
                val localMarker = markerDao.getMarkerById(marker.id)
                if (localMarker != null) {
                    Timber.tag("API_FLOW").d("[$callId] 로컬 DB에서 마커 조회됨: ${marker.id}")
                    return markerMapper.fromEntity(localMarker)
                }
                
                // 로컬 데이터베이스에 마커 추가 시도 (네비게이션 복귀 상태에서도 로컬에서만 작업)
                try {
                    val markerEntity = markerMapper.toEntity(marker)
                    markerDao.insertMarker(markerEntity)
                    Timber.tag("API_FLOW").d("[$callId] 네비게이션 복귀 상태에서 마커를 로컬에만 저장: ${marker.id}")
                    return marker
                } catch (dbEx: Exception) {
                    Timber.tag("API_FLOW").e(dbEx, "[$callId] 로컬 DB에 마커 저장 실패: ${marker.id}")
                }
            }
        } catch (e: Exception) {
            Timber.tag("API_FLOW").e(e, "마커 생성 API 호출 중 예외 발생: markerId=${marker.id}")
        }
        
        return null
    }
    
    /**
     * 마커 삭제 API 호출
     */
    override suspend fun deleteMarker(markerId: String): Boolean {
        try {
            val callId = UUID.randomUUID().toString().take(6)
            Timber.tag("API_FLOW").d("[$callId] 마커 삭제 API 호출 시작: markerId=$markerId")
            
            // 네트워크 연결 확인
            if (!isNetworkConnected()) {
                Timber.tag("API_FLOW").w("[$callId] 네트워크 연결 없음 - 마커 삭제 API 호출 실패")
                return false
            }
            
            // 마커 정보 가져오기
            val marker = markerDao.getMarkerById(markerId)
            
            if (marker == null) {
                Timber.tag("API_FLOW").w("[$callId] 삭제할 마커가 DB에 존재하지 않습니다: $markerId")
                return false
            }
            
            // 해시된 사용자 ID 가져오기
            val hashedUserId = authRepository.getHashedUserId()
            
            // 요청 데이터 구성
            val request = DeleteMarkerRequestDto(
                id = markerId,
                userId = hashedUserId,
                geohash = marker.geohash
            )
            
            // 요청 키 생성
            val requestKey = "deleteMarker:$markerId"
            
            try {
                // ApiRequestManager를 사용하여 API 호출
                val response = apiRequestManager.executeRequest(
                    requestKey = requestKey,
                    priority = ApiPriority.FOREGROUND_NORMAL,
                    request = {
                        RetryUtil.retryApiCall {
                            Timber.tag("API_FLOW").d("[$callId] API 호출 실행: deleteMarker($markerId)")
                            api.deleteMarker(request)
                        }
                    }
                )
                
                if (response.isSuccessful) {
                    val result = response.body()
                    
                    if (result != null && result.success) {
                        Timber.tag("API_FLOW").d("[$callId] 마커 삭제 API 성공: markerId=$markerId")
                        
                        // 로컬 DB에서 마커 삭제
                        markerDao.deleteMarkerById(markerId)
                        
                        return true
                    } else {
                        Timber.tag("API_FLOW").w("[$callId] 마커 삭제 API 실패: ${result?.message ?: "알 수 없는 오류"}")
                    }
                } else {
                    // 404 에러인 경우 이미 서버에서 삭제된 것으로 간주하고 로컬만 삭제
                    if (response.code() == 404) {
                        // 로컬 DB에서도 마커 삭제 처리
                        markerDao.deleteMarkerById(markerId)
                        
                        Timber.tag("API_FLOW").d("[$callId] 마커가 서버에 이미 존재하지 않아 로컬에서만 삭제: $markerId")
                        return true
                    }
                    
                    Timber.tag("API_FLOW").e(
                        "[$callId] 마커 삭제 API 오류: ${response.code()}, ${response.message()}"
                    )
                    handleApiError(response.code(), response.message(), response.errorBody()?.string())
                }
            } catch (e: NavigationReturnException) {
                Timber.tag("API_FLOW").d("[$callId] 네비게이션 복귀 상태에서 마커 삭제 API 호출 방지됨 - 로컬에서만 삭제")
                
                // 로컬 DB에서 마커 삭제
                markerDao.deleteMarkerById(markerId)
                return true
            }
        } catch (e: Exception) {
            // SyncException.NotFoundError나 SyncException.MarkerNotFoundError 예외라면 이미 서버에 없는 것으로 간주
            if (e is SyncException.NotFoundError || e is SyncException.MarkerNotFoundError) {
                // 로컬에서 마커 삭제 처리
                markerDao.deleteMarkerById(markerId)
                
                Timber.tag("API_FLOW").d("마커를 찾을 수 없어 로컬에서만 삭제: $markerId (${e.message})")
                return true
            }
            
            Timber.tag("API_FLOW").e(e, "마커 삭제 API 호출 중 예외 발생: markerId=$markerId")
        }
        
        return false
    }
    
    /**
     * 메모 생성 API 호출
     */
    override suspend fun createMemo(memo: Memo): Memo? {
        try {
            val callId = UUID.randomUUID().toString().take(6)
            Timber.tag("API_FLOW").d("[$callId] 메모 생성 API 호출 시작: memoId=${memo.id}")
            
            // 네트워크 연결 확인
            if (!isNetworkConnected()) {
                Timber.tag("API_FLOW").w("[$callId] 네트워크 연결 없음 - 메모 생성 API 호출 실패")
                return null
            }
            
            // 해시된 사용자 ID 가져오기
            val hashedUserId = authRepository.getHashedUserId()
            
            // 요청 데이터 구성
            val request = CreateMemoRequestDto(
                id = memo.id,
                userId = hashedUserId,
                markerId = memo.markerId,
                content = memo.content
            )
            
            // 요청 키 생성
            val requestKey = "createMemo:${memo.id}"
            
            // ApiRequestManager를 사용하여 API 호출
            val response = apiRequestManager.executeRequest(
                requestKey = requestKey,
                priority = ApiPriority.FOREGROUND_NORMAL,
                request = {
                    RetryUtil.retryApiCall {
                        Timber.tag("API_FLOW").d("[$callId] API 호출 실행: createMemo(${memo.id})")
                        api.createMemo(request)
                    }
                }
            )
            
            if (response.isSuccessful) {
                val result = response.body()
                
                if (result != null && result.success) {
                    Timber.tag("API_FLOW").d("[$callId] 메모 생성 API 성공: memoId=${memo.id}")
                    
                    // 서버에서 반환한 메모 정보로 업데이트
                    val updatedMemo = memoMapper.fromDto(result.memo)
                    
                    // 로컬 DB 업데이트
                    memoDao.insertMemo(memoMapper.toEntity(updatedMemo))
                    
                    return updatedMemo
                } else {
                    Timber.tag("API_FLOW").w("[$callId] 메모 생성 API 실패: ${result?.success}")
                }
            } else {
                Timber.tag("API_FLOW").e(
                    "[$callId] 메모 생성 API 오류: ${response.code()}, ${response.message()}"
                )
                handleApiError(response.code(), response.message(), response.errorBody()?.string())
            }
        } catch (e: Exception) {
            Timber.tag("API_FLOW").e(e, "메모 생성 API 호출 중 예외 발생: memoId=${memo.id}")
        }
        
        return null
    }
    
    /**
     * 메모 삭제 API 호출
     */
    override suspend fun deleteMemo(memoId: String, markerId: String): Boolean {
        try {
            val callId = UUID.randomUUID().toString().take(6)
            Timber.tag("API_FLOW").d("[$callId] 메모 삭제 API 호출 시작: memoId=$memoId, markerId=$markerId")
            
            // 네트워크 연결 확인
            if (!isNetworkConnected()) {
                Timber.tag("API_FLOW").w("[$callId] 네트워크 연결 없음 - 메모 삭제 API 호출 실패")
                return false
            }
            
            // 해시된 사용자 ID 가져오기
            val hashedUserId = authRepository.getHashedUserId()
            
            // 요청 데이터 구성
            val request = DeleteMemoRequestDto(
                id = memoId,
                userId = hashedUserId,
                markerId = markerId
            )
            
            // 요청 키 생성
            val requestKey = "deleteMemo:$memoId"
            
            // ApiRequestManager를 사용하여 API 호출
            val response = apiRequestManager.executeRequest(
                requestKey = requestKey,
                priority = ApiPriority.FOREGROUND_NORMAL,
                request = {
                    RetryUtil.retryApiCall {
                        Timber.tag("API_FLOW").d("[$callId] API 호출 실행: deleteMemo($memoId)")
                        api.deleteMemo(request)
                    }
                }
            )
            
            if (response.isSuccessful) {
                val result = response.body()
                
                if (result != null && result.success) {
                    Timber.tag("API_FLOW").d("[$callId] 메모 삭제 API 성공: memoId=$memoId")
                    
                    // 로컬 DB에서 메모 삭제
                    memoDao.deleteMemoById(memoId)
                    
                    return true
                } else {
                    Timber.tag("API_FLOW").w("[$callId] 메모 삭제 API 실패: ${result?.message ?: "알 수 없는 오류"}")
                }
            } else {
                // 404 에러인 경우 이미 서버에서 삭제된 것으로 간주하고 로컬만 삭제
                if (response.code() == 404) {
                    // 로컬 DB에서도 메모 삭제 처리
                    memoDao.deleteMemoById(memoId)
                    
                    Timber.tag("API_FLOW").d("[$callId] 메모가 서버에 이미 존재하지 않아 로컬에서만 삭제: $memoId")
                    return true
                }
                
                Timber.tag("API_FLOW").e(
                    "[$callId] 메모 삭제 API 오류: ${response.code()}, ${response.message()}"
                )
                handleApiError(response.code(), response.message(), response.errorBody()?.string())
            }
        } catch (e: Exception) {
            Timber.tag("API_FLOW").e(e, "메모 삭제 API 호출 중 예외 발생: memoId=$memoId")
        }
        
        return false
    }
    
    override fun observeNetworkState(): Flow<Boolean> {
        return connectionStateMonitor.observeNetworkState()
    }
    
    override fun isNetworkConnected(): Boolean {
        return connectionStateMonitor.isConnected()
    }
    
    /**
     * API 오류 처리
     */
    private fun handleApiError(code: Int, message: String, errorBody: String? = null) {
        // 에러 본문 파싱 시도
        val errorMessage = try {
            if (!errorBody.isNullOrBlank()) {
                // 에러 본문에서 메시지 추출 (단순 파싱)
                val pattern = "\"message\":\"([^\"]+)\"".toRegex()
                val matchResult = pattern.find(errorBody)
                matchResult?.groupValues?.get(1) ?: message
            } else {
                message
            }
        } catch (e: Exception) {
            Timber.e(e, "에러 본문 파싱 실패")
            message
        }
        
        // 마커 관련 오류 특별 처리
        if (code == 404 && errorMessage.contains("마커") && errorMessage.contains("존재하지 않습니다")) {
            // 마커 ID 추출 시도
            val markerId = try {
                val pattern = "ID: ([0-9a-f-]+)".toRegex()
                val matchResult = pattern.find(errorMessage)
                matchResult?.groupValues?.get(1)
            } catch (e: Exception) {
                null
            }
            
            throw SyncException.MarkerNotFoundError(errorMessage, markerId)
        }
        
        // 일반 에러 처리
        when (code) {
            in 400..499 -> {
                when (code) {
                    404 -> throw SyncException.NotFoundError(errorMessage)
                    409 -> throw SyncException.ConflictError(errorMessage)
                    else -> throw SyncException.BadRequestError(errorMessage)
                }
            }
            in 500..599 -> throw SyncException.ServerError(errorMessage)
            else -> throw SyncException.UnknownError(errorMessage)
        }
    }
    
    /**
     * 호출 스택 추적을 위한 유틸리티 함수
     */
    private fun getCallStack(): String {
        val stackTrace = Thread.currentThread().stackTrace
        return stackTrace.slice(3..minOf(8, stackTrace.size - 1))
            .joinToString(" <- ") { "${it.className.substringAfterLast('.')}.${it.methodName}(${it.lineNumber})" }
    }
    
    /**
     * 데이터 동기화 API 호출
     */
    suspend fun syncData(geohash: String, lastSyncTimestamp: Long): Boolean {
        try {
            val callId = UUID.randomUUID().toString().take(6)
            Timber.tag("API_FLOW").d("[$callId] 데이터 동기화 API 호출 시작: geohash=$geohash, lastSync=$lastSyncTimestamp")
            
            // 네트워크 연결 확인
            if (!isNetworkConnected()) {
                Timber.tag("API_FLOW").w("[$callId] 네트워크 연결 없음 - 데이터 동기화 API 호출 실패")
                return false
            }
            
            // 요청 데이터 구성
            val request = SyncRequestDto(
                geohash = geohash,
                syncType = "GEOHASH",
                lastSyncTimestamp = lastSyncTimestamp
            )
            
            // 요청 키 생성
            val requestKey = "syncData:$geohash:$lastSyncTimestamp"
            
            try {
                // ApiRequestManager를 사용하여 API 호출
                val response = apiRequestManager.executeRequest(
                    requestKey = requestKey,
                    priority = ApiPriority.BACKGROUND_SYNC,
                    request = {
                        RetryUtil.retryApiCall {
                            Timber.tag("API_FLOW").d("[$callId] API 호출 실행: syncData($geohash, $lastSyncTimestamp)")
                            api.syncData(request)
                        }
                    }
                )
                
                if (response.isSuccessful) {
                    val data = response.body()
                    
                    if (data != null && data.success) {
                        Timber.tag("API_FLOW").d("[$callId] 데이터 동기화 API 성공: 마커 ${data.markers.size}개, 메모 ${data.memos.size}개")
                        
                        // 마커 및 메모 데이터 처리
                        val updatedMarkers = data.markers.map(markerMapper::fromDto)
                        val updatedMemos = data.memos.map(memoMapper::fromDto)
                        
                        // 삭제된 항목 처리
                        val deletedMarkerIds = data.deletedItems
                            .filter { it.type == "MARKER" }
                            .map { it.id }
                        
                        val deletedMemoIds = data.deletedItems
                            .filter { it.type == "MEMO" }
                            .map { it.id }
                        
                        // 로컬 DB 업데이트
                        markerDao.insertMarkers(updatedMarkers.map(markerMapper::toEntity))
                        memoDao.insertMemos(updatedMemos.map(memoMapper::toEntity))
                        
                        if (deletedMarkerIds.isNotEmpty()) {
                            markerDao.deleteByIds(deletedMarkerIds)
                        }
                        
                        if (deletedMemoIds.isNotEmpty()) {
                            memoDao.deleteByIds(deletedMemoIds)
                        }
                        
                        // 성공 시 마지막 동기화 타임스탬프 업데이트
                        val serverTime = data.serverTime ?: System.currentTimeMillis()
                        updateLastSyncTimestamp(geohash, serverTime)
                        
                        // 서버와 동기화 완료 표시
                        geohashManager.markAsSynced(geohash)
                        
                        return true
                    } else {
                        Timber.tag("API_FLOW").w("[$callId] 데이터 동기화 API 실패: ${data?.success}")
                    }
                } else {
                    Timber.tag("API_FLOW").e(
                        "[$callId] 데이터 동기화 API 오류: ${response.code()}, ${response.message()}"
                    )
                    handleApiError(response.code(), response.message(), response.errorBody()?.string())
                }
            } catch (e: SyncDebouncedException) {
                // 최근 3분내 동기화 요청이 있어 디바운싱된 경우
                Timber.d("동기화 디바운싱 적용됨 - 로컬 데이터만 사용: ${e.message}")
                
                // 로컬 데이터가 있으면 성공으로 간주
                if (geohashManager.hasLocalData(geohash)) {
                    Timber.d("로컬 데이터 존재 - 디바운싱 적용 시 로컬 데이터 사용")
                    return true
                } else {
                    // 동기화가 디바운싱되었지만 로컬 데이터가 없는 경우 - 성공으로 간주
                    // (초기 데이터 로드와 달리 동기화는 필수가 아님)
                    Timber.d("로컬 데이터 없음 & 디바운싱 적용 - 작업은 성공으로 간주")
                    return true
                }
            } catch (e: NavigationReturnException) {
                Timber.d("네비게이션 복귀 상태에서 데이터 동기화 API 호출 방지됨 - 로컬 데이터만 사용")
                
                // 로컬 데이터가 있는지 확인
                if (geohashManager.hasLocalData(geohash)) {
                    Timber.d("로컬 데이터 존재 - 네비게이션 복귀 시 로컬 데이터만 사용")
                    return true
                } else {
                    Timber.d("로컬 데이터 없음 - 작업은 성공으로 간주 (네비게이션 복귀 상태)")
                    // 로컬 데이터가 없어도 네비게이션 복귀 상태에서는 성공으로 간주
                    return true
                }
            }
        } catch (e: Exception) {
            Timber.tag("API_FLOW").e(e, "데이터 동기화 API 호출 중 예외 발생: geohash=$geohash")
        }
        
        return false
    }
} 