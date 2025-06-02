package com.parker.hotkey.data.remote.util

import com.parker.hotkey.di.qualifier.ApplicationScope
import com.parker.hotkey.domain.util.AppStateManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * API 요청 관리를 위한 인터페이스
 * API 중복 호출을 방지하고 우선순위 기반 처리를 지원합니다.
 */
interface ApiRequestManager {
    /**
     * API 요청을 실행하되, 짧은 시간 내에 동일한 요청이 있을 경우 기존 요청의 결과를 반환합니다.
     * 네비게이션 복귀 상태일 경우 API 호출을 방지하고, 로컬 데이터 사용을 권장합니다.
     *
     * @param requestKey 요청을 식별하는 고유 키 (URL + 주요 파라미터 기반)
     * @param priority 요청의 우선순위 (기본값: FOREGROUND_NORMAL)
     * @param debounceTime 중복 요청으로 간주할 시간 간격 (밀리초)
     * @param request 실제 API 호출 함수
     * @return API 호출 결과, 네비게이션 복귀 상태에서는 NavigationReturnException 예외 발생
     * @throws NavigationReturnException 네비게이션 복귀 상태에서 skipNavigationCheck가 false인 경우
     * @throws SyncDebouncedException 동기화 요청이 글로벌 디바운싱에 의해 차단된 경우
     */
    @Throws(NavigationReturnException::class, SyncDebouncedException::class)
    suspend fun <T> executeRequest(
        requestKey: String,
        priority: ApiPriority = ApiPriority.FOREGROUND_NORMAL,
        debounceTime: Long = 500,
        request: suspend () -> T
    ): T
    
    /**
     * 현재 네비게이션 복귀 상태인지 확인합니다.
     * @return 네비게이션 복귀 상태면 true, 아니면 false
     */
    fun isNavigationReturn(): Boolean
    
    /**
     * API 성능 보고서를 생성합니다.
     * @return 성능 보고서 문자열
     */
    fun getPerformanceReport(): String
    
    /**
     * 요청 캐시를 초기화합니다.
     * 앱 업데이트나 로그인 상태 변경 후 호출해야 합니다.
     */
    suspend fun clearRequestCache()
}

/**
 * 네비게이션 복귀 상태에서 API 호출 시도 시 발생하는 예외
 */
class NavigationReturnException : Exception {
    constructor() : super("네비게이션 복귀 상태에서는 API 호출이 금지됩니다. 로컬 데이터를 사용하세요.")
    constructor(message: String) : super(message)
}

/**
 * 동기화 디바운싱에 의해 API 호출이 차단된 경우 발생하는 예외
 */
class SyncDebouncedException : Exception {
    constructor() : super("최근 3분 내 동기화 요청이 있어 요청이 차단되었습니다.")
    constructor(message: String) : super(message)
}

/**
 * API 요청을 중앙에서 관리하는 구현 클래스.
 * 짧은 시간 내에 동일한 API 요청이 중복 호출되는 것을 방지하고
 * 우선순위에 따라 요청을 다르게 처리합니다.
 * 네비게이션 복귀 상태에서의 불필요한 API 호출을 방지합니다.
 */
@Singleton
class ApiRequestManagerImpl @Inject constructor(
    @ApplicationScope private val coroutineScope: CoroutineScope,
    private val appStateManager: AppStateManager,
    private val performanceMonitor: ApiPerformanceMonitor
) : ApiRequestManager {
    private val ongoingRequests = ConcurrentHashMap<String, Deferred<*>>()
    private val requestTimestamps = ConcurrentHashMap<String, Long>()
    private val requestLock = Mutex() // 요청 관리를 위한 뮤텍스 추가
    
    // 글로벌 동기화 디바운싱을 위한 변수들
    private val syncDebounceTime = 3 * 60 * 1000L // 3분
    private var lastSyncRequestTime = 0L
    private val syncLock = Mutex()
    
    /**
     * 현재 네비게이션 복귀 상태인지 확인합니다.
     * @return 네비게이션 복귀 상태면 true, 아니면 false
     */
    override fun isNavigationReturn(): Boolean {
        return appStateManager.isNavigationReturn()
    }
    
    /**
     * API 성능 보고서를 생성합니다.
     * @return 성능 보고서 문자열
     */
    override fun getPerformanceReport(): String {
        return performanceMonitor.generateReport()
    }
    
    /**
     * 요청 캐시를 초기화합니다.
     * 앱 업데이트나 로그인 상태 변경 후 호출해야 합니다.
     */
    override suspend fun clearRequestCache() {
        requestLock.withLock {
            ongoingRequests.clear()
            requestTimestamps.clear()
            lastSyncRequestTime = 0L
            Timber.d("API 요청 캐시가 초기화되었습니다.")
        }
    }
    
    @Throws(NavigationReturnException::class, SyncDebouncedException::class)
    override suspend fun <T> executeRequest(
        requestKey: String,
        priority: ApiPriority,
        debounceTime: Long,
        request: suspend () -> T
    ): T {
        // 네비게이션 복귀 상태 확인
        if (isNavigationReturn()) {
            Timber.tag("API_FLOW").w("[NAV_RETURN] 네비게이션 복귀 상태에서 API 요청 차단: $requestKey (우선순위: $priority)")
            
            // 네비게이션 복귀 상태에서는 모든 API 호출을 하지 않고 예외 발생
            throw NavigationReturnException()
        }
        
        // 사용자 액션이 아닌 동기화 요청 식별
        val isSyncRequest = !isUserActionRequest(priority, requestKey)
        
        // 동기화 요청이면 글로벌 디바운싱 적용
        if (isSyncRequest) {
            syncLock.withLock {
                val currentTime = System.currentTimeMillis()
                val timeSinceLastSync = currentTime - lastSyncRequestTime
                
                // 마지막 동기화 요청 후 3분이 지나지 않았으면 차단
                if (lastSyncRequestTime > 0 && timeSinceLastSync < syncDebounceTime) {
                    Timber.tag("API_FLOW").d(
                        "글로벌 동기화 디바운싱 적용: $requestKey (${timeSinceLastSync/1000}초 경과, 3분 미만)"
                    )
                    throw SyncDebouncedException("최근 3분 내 동기화 요청이 있어 요청이 차단되었습니다.")
                }
                
                // 현재 시간을 마지막 동기화 요청 시간으로 업데이트 (동기화 요청만)
                lastSyncRequestTime = currentTime
            }
        }
        
        // 요청 키 정규화
        val normalizedKey = normalizeRequestKey(requestKey)
        
        // 뮤텍스로 동시 접근 제어
        return requestLock.withLock {
            val currentTime = System.currentTimeMillis()
            
            // 백그라운드 요청은 별도 키 사용 (포그라운드와 충돌 방지)
            val actualKey = when (priority) {
                ApiPriority.BACKGROUND_SYNC, ApiPriority.BACKGROUND_PREFETCH -> "bg_$normalizedKey"
                else -> normalizedKey
            }
            
            val lastRequestTime = requestTimestamps[actualKey]
            
            // 중복 요청 확인 (디바운싱)
            ongoingRequests[actualKey]?.let { existingRequest ->
                if (lastRequestTime != null && (currentTime - lastRequestTime) < debounceTime) {
                    Timber.d("중복 요청 감지: $actualKey (우선순위: $priority), 기존 결과 재사용")
                    @Suppress("UNCHECKED_CAST")
                    return@withLock existingRequest.await() as T
                }
            }
            
            // 디스패처 선택 (백그라운드 vs 포그라운드)
            val dispatcher = when (priority) {
                ApiPriority.BACKGROUND_SYNC, ApiPriority.BACKGROUND_PREFETCH -> Dispatchers.IO
                ApiPriority.FOREGROUND_CRITICAL -> Dispatchers.Main.immediate
                else -> Dispatchers.Default
            }
            
            // 새 요청 시작 시간 기록
            val startTime = System.currentTimeMillis()
            
            // 새 요청 생성 및 실행
            val newRequest = coroutineScope.async(dispatcher) {
                Timber.d("API 요청 시작: $actualKey (우선순위: $priority)")
                request()
            }
            
            ongoingRequests[actualKey] = newRequest
            requestTimestamps[actualKey] = currentTime
            
            try {
                val result = newRequest.await()
                // 성공적인 응답 시간 측정 및 기록
                val duration = System.currentTimeMillis() - startTime
                Timber.d("API 요청 완료: $actualKey (우선순위: $priority), 소요시간: ${duration}ms")
                performanceMonitor.recordApiCall(actualKey, duration)
                return@withLock result
            } catch (e: CancellationException) {
                Timber.w("API 요청 취소됨: $actualKey (우선순위: $priority)")
                ongoingRequests.remove(actualKey)
                throw e
            } catch (e: Exception) {
                // 실패한 응답 시간 측정 및 기록 (오류 플래그 true)
                val duration = System.currentTimeMillis() - startTime
                Timber.e(e, "API 요청 실패: $actualKey (우선순위: $priority), 소요시간: ${duration}ms")
                performanceMonitor.recordApiCall(actualKey, duration, true)
                ongoingRequests.remove(actualKey)
                throw e
            } finally {
                if (!newRequest.isCancelled && newRequest.isCompleted) {
                    ongoingRequests.remove(actualKey)
                }
            }
        }
    }
    
    /**
     * 사용자 액션에 의한 요청인지 판단
     */
    private fun isUserActionRequest(priority: ApiPriority, requestKey: String): Boolean {
        // 사용자 액션 관련 우선순위는 항상 제외
        if (priority == ApiPriority.FOREGROUND_CRITICAL) {
            return true
        }
        
        // 키 기반으로 사용자 액션 식별
        val userActionPatterns = listOf(
            "createMarker", 
            "deleteMarker", 
            "createMemo", 
            "deleteMemo",
            "userAction"
        )
        
        return userActionPatterns.any { requestKey.contains(it, ignoreCase = true) }
    }
    
    /**
     * 요청 키 정규화 - 다양한 소스에서 동일 API에 같은 키가 사용되도록 함
     */
    private fun normalizeRequestKey(key: String): String {
        // 키에서 geohash 부분만 추출 (wydkks와 같은 값)
        val geohashPattern = "([a-z0-9]{6,8})".toRegex()
        val geohashMatch = geohashPattern.find(key)
        
        return if (geohashMatch != null) {
            // geohash가 있는 경우 표준화된 키 형식으로 변환
            "initialData:${geohashMatch.value}"
        } else {
            // geohash가 없는 경우 원래 키 사용
            key
        }
    }
} 