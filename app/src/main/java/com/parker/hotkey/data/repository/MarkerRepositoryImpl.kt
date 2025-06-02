package com.parker.hotkey.data.repository

import com.parker.hotkey.data.local.dao.MarkerDao
import com.parker.hotkey.data.local.entity.MarkerEntity
import com.parker.hotkey.data.mapper.MarkerEntityMapper
import com.parker.hotkey.domain.model.LastSync
import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.repository.MarkerRepository
import com.parker.hotkey.domain.repository.MarkerQueryOptions
import com.parker.hotkey.domain.constants.GeohashConstants
import com.parker.hotkey.util.GeoHashUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import javax.inject.Inject
import javax.inject.Singleton
import javax.inject.Named
import timber.log.Timber
import java.util.UUID
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import com.parker.hotkey.domain.model.MarkerState
import com.parker.hotkey.domain.repository.SyncRepository
import com.parker.hotkey.domain.manager.MarkerStateAdapter
import dagger.Lazy

@Singleton
class MarkerRepositoryImpl @Inject constructor(
    private val markerDao: MarkerDao,
    private val markerMapper: MarkerEntityMapper,
    // 캐시 어댑터는 옵셔널로 주입 (Nullable 타입)
    @Named("optionalCacheAdapter") private val cacheAdapter: MarkerRepositoryCacheAdapter?,
    private val syncRepository: SyncRepository,
    private val markerStateAdapter: Lazy<MarkerStateAdapter>
) : MarkerRepository, BaseCrudRepositoryHelper<Marker, MarkerEntity>() {
    
    // BaseCrudRepositoryHelper 구현
    override protected fun getAllEntities(): Flow<List<MarkerEntity>> = markerDao.getAllMarkers()
    
    override protected suspend fun getEntityById(id: String): MarkerEntity? = markerDao.getMarkerById(id)
    
    override protected fun mapToDomain(entity: MarkerEntity): Marker = markerMapper.toDomain(entity)
    
    override protected fun mapToEntity(domain: Marker): MarkerEntity = markerMapper.toEntity(domain)
    
    override protected suspend fun insertEntity(entity: MarkerEntity) {
        markerDao.insertMarker(entity)
    }
    
    override protected suspend fun updateEntity(entity: MarkerEntity) {
        markerDao.updateMarker(entity)
    }
    
    override protected suspend fun deleteEntity(id: String) {
        markerDao.getMarkerById(id)?.let { markerDao.deleteMarker(it) }
    }
    
    override protected suspend fun getUnsyncedEntities(): List<MarkerEntity> = markerDao.getUnsyncedMarkers()
    
    override protected suspend fun updateEntitiesLastSync(ids: List<String>, syncStatus: Int, syncTimestamp: Long, syncError: String?) {
        markerDao.updateMarkersLastSync(ids, syncStatus, syncTimestamp, syncError)
    }
    
    /**
     * 모든 마커 조회 방식을 통합한 단일 메서드
     * 
     * @param geohash 기준 geohash
     * @param neighbors 이웃 geohash 목록 (선택 사항)
     * @param options 조회 옵션 (정밀도, 제한, 동기화 등)
     * @return 마커 목록 (Flow)
     */
    override fun getMarkers(
        geohash: String,
        neighbors: List<String>,
        options: MarkerQueryOptions
    ): Flow<List<Marker>> {
        Timber.d("통합 마커 쿼리 실행: geohash=$geohash, 이웃=${neighbors.size}개, 정밀도=${options.precision}, 제한=${options.limit}")
        
        // 빈 리스트 확인
        if (geohash.isBlank() && neighbors.isEmpty()) {
            Timber.w("geohash와 이웃이 모두 비어있음, 빈 결과 반환")
            return flowOf(emptyList())
        }
        
        // 안전을 위해 빈 리스트 확인
        val safeNeighbors = neighbors.ifEmpty { listOf(geohash) }
        
        // 정밀도에 맞게 geohash 접두사 길이 조정
        val adjustedGeohashPrefix = if (geohash.length > options.precision) {
            geohash.substring(0, options.precision).also { 
                Timber.d("geohash 길이 조정: $geohash -> $it (${options.precision}자리로 조정)") 
            }
        } else {
            geohash.also { 
                Timber.d("geohash 길이 유지: $it (${it.length}자리 < ${options.precision}자리 정밀도)") 
            }
        }
        
        // 이웃들도 정밀도에 맞게 조정
        val adjustedNeighbors = safeNeighbors.map { neighbor ->
            if (neighbor.length > options.precision) {
                neighbor.substring(0, options.precision)
            } else {
                neighbor
            }
        }.distinct()
        
        val allGeohashes = (adjustedNeighbors + adjustedGeohashPrefix).distinct()
        Timber.d("최종 geohash 목록: ${allGeohashes.joinToString()}")
        
        // 옵션에 따라 DAO 호출 분기
        val markersFlow = if (options.precision == 6) {
            // 정밀도가 6인 경우 최적화된 메서드 사용
            Timber.d("정밀도 6 최적화 쿼리 사용")
            markerDao.getMarkersInGeohash6AreaOptimized(
                allGeohashes,
                adjustedGeohashPrefix,
                options.limit
            )
        } else {
            // 다른 정밀도는 일반 메서드 사용
            Timber.d("일반 geohash 쿼리 사용 (정밀도: ${options.precision})")
            markerDao.getMarkersInGeohashArea(
                allGeohashes,
                adjustedGeohashPrefix,
                options.limit
            )
        }
        
        // 엔티티를 도메인 모델로 변환
        return markersFlow.map { entities -> 
            val markers = entities.map { markerMapper.toDomain(it) }
            Timber.d("${markers.size}개 마커 로드됨 (정밀도: ${options.precision}, 제한: ${options.limit})")
            
            if (markers.isNotEmpty()) {
                Timber.d("첫 번째 마커: id=${markers.first().id}, geohash=${markers.first().geohash}")
            }
            
            markers
        }
    }
    
    /**
     * 동기식 버전의 마커 조회 (즉시 결과 반환)
     * 
     * @param geohash 기준 geohash
     * @param neighbors 이웃 geohash 목록 (선택 사항)
     * @param options 조회 옵션 (정밀도, 제한, 동기화 등)
     * @return 마커 목록
     */
    override suspend fun getMarkersSync(
        geohash: String,
        neighbors: List<String>,
        options: MarkerQueryOptions
    ): List<Marker> = withContext(Dispatchers.IO) {
        Timber.d("동기식 통합 마커 쿼리 실행: geohash=$geohash, 이웃=${neighbors.size}개")
        
        try {
            // 1. 캐시에서 먼저 데이터 조회 시도
            val cachedMarkers = cacheAdapter?.getMarkersFromCache(geohash, neighbors, options)
            if (cachedMarkers != null) {
                Timber.d("캐시에서 마커 ${cachedMarkers.size}개 조회됨")
                return@withContext cachedMarkers
            }
            
            // 2. 캐시 미스 시 DB 쿼리 수행
            // 빈 리스트 확인
            if (geohash.isBlank() && neighbors.isEmpty()) {
                Timber.w("geohash와 이웃이 모두 비어있음, 빈 결과 반환")
                return@withContext emptyList<Marker>()
            }
            
            // 안전을 위해 빈 리스트 확인
            val safeNeighbors = neighbors.ifEmpty { listOf(geohash) }
            
            // 정밀도에 맞게 geohash 접두사 길이 조정
            val adjustedGeohashPrefix = if (geohash.length > options.precision) {
                geohash.substring(0, options.precision)
            } else {
                geohash
            }
            
            // 이웃들도 정밀도에 맞게 조정
            val adjustedNeighbors = safeNeighbors.map { neighbor ->
                if (neighbor.length > options.precision) {
                    neighbor.substring(0, options.precision)
                } else {
                    neighbor
                }
            }.distinct()
            
            val allGeohashes = (adjustedNeighbors + adjustedGeohashPrefix).distinct()
            
            // DB에서 마커 조회
            val markerEntities = markerDao.getMarkersByGeohash(allGeohashes, adjustedGeohashPrefix)
            
            // Entity -> Domain 변환
            val markers = markerEntities.map { markerMapper.toDomain(it) }.take(options.limit)
            Timber.d("DB에서 마커 ${markers.size}개 조회됨 (geohash=$geohash)")
            
            // 3. 결과를 캐시에 저장 
            if (markers.isNotEmpty()) {
                cacheAdapter?.cacheMarkers(geohash, neighbors, options, markers)
            }
            
            markers
        } catch (e: Exception) {
            Timber.e(e, "동기식 마커 조회 중 오류 발생: geohash=$geohash")
            emptyList()
        }
    }
    
    // ========= 레거시 메서드 (새로운 통합 메서드를 사용하도록 수정) =========
    
    @Deprecated("Use getMarkers() instead", ReplaceWith("getMarkers(geohashPrefix, neighbors)"))
    override fun getMarkersByGeohash(geohashPrefix: String, neighbors: List<String>): Flow<List<Marker>> {
        Timber.d("Deprecated getMarkersByGeohash 호출 - 대신 통합 메서드 사용")
        return getMarkers(geohashPrefix, neighbors)
    }
    
    @Deprecated("Use getMarkers() instead", ReplaceWith("getMarkers(geohashPrefix, neighbors, MarkerQueryOptions(limit = limit))"))
    override fun getMarkersByGeohashOptimized(
        geohashPrefix: String,
        neighbors: List<String>,
        zoomLevel: Double,
        limit: Int
    ): Flow<List<Marker>> {
        Timber.d("Deprecated getMarkersByGeohashOptimized 호출 - 대신 통합 메서드 사용")
        return getMarkers(geohashPrefix, neighbors, MarkerQueryOptions(limit = if (limit <= 0) 200 else limit))
    }
    
    @Deprecated("Use getMarkers() instead", ReplaceWith("getMarkers(geohashPrefix, neighbors, MarkerQueryOptions(precision = precision, limit = limit))"))
    override fun getMarkersByGeohashWithLimit(
        geohashPrefix: String,
        neighbors: List<String>,
        precision: Int,
        limit: Int
    ): Flow<List<Marker>> {
        Timber.d("Deprecated getMarkersByGeohashWithLimit 호출 - 대신 통합 메서드 사용")
        
        if (precision <= 0 || precision > 12) {
            Timber.w("정밀도 값이 유효하지 않음: $precision (1~12 범위여야 함). 정밀도 6으로 기본값 사용")
            return getMarkers(geohashPrefix, neighbors, MarkerQueryOptions(limit = if (limit <= 0) 200 else limit))
        }
        
        return getMarkers(
            geohashPrefix, 
            neighbors, 
            MarkerQueryOptions(
                precision = precision, 
                limit = if (limit <= 0) 200 else limit
            )
        )
    }
    
    @Deprecated("Use getMarkers() instead", ReplaceWith("getMarkers(geohashPrefix, neighbors, MarkerQueryOptions(limit = limit))"))
    override fun getMarkersByGeohash6WithLimit(
        geohashPrefix: String,
        neighbors: List<String>,
        limit: Int
    ): Flow<List<Marker>> {
        Timber.d("Deprecated getMarkersByGeohash6WithLimit 호출 - 대신 통합 메서드 사용")
        return getMarkers(
            geohashPrefix, 
            neighbors, 
            MarkerQueryOptions(limit = if (limit <= 0) 200 else limit)
        )
    }
    
    override fun getMarkersInGeohashList(geohashes: List<String>): Flow<List<Marker>> =
        markerDao.getMarkersInGeohashArea(geohashes, geohashes.firstOrNull() ?: "").map { entities ->
            entities.map { markerMapper.toDomain(it) }
        }
    
    override suspend fun getMarkerCount(): Int =
        markerDao.getMarkerCount()
    
    override suspend fun createMarker(userId: String, latitude: Double, longitude: Double): Marker {
        val geohash = GeoHashUtil.encode(latitude, longitude, GeohashConstants.GEOHASH_PRECISION)
        val marker = Marker(
            id = UUID.randomUUID().toString(),
            userId = userId,
            latitude = latitude,
            longitude = longitude,
            geohash = geohash,
            lastSync = LastSync.createInitial(),
            modifiedAt = System.currentTimeMillis()
        )
        
        insert(marker)
        return marker
    }
    
    override suspend fun deleteMarkerIfNoMemos(markerId: String) {
        val memoCount = markerDao.getMemoCount(markerId)
        if (memoCount == 0) {
            delete(markerId)
        }
    }
    
    @Deprecated("Use getMarkersSync() instead", ReplaceWith("getMarkersSync(geohash, neighbors)"))
    override suspend fun getMarkersByGeohashArea(
        geohash: String,
        neighbors: List<String>
    ): List<Marker> {
        Timber.d("Deprecated getMarkersByGeohashArea 호출 - 대신 통합 메서드 사용")
        return getMarkersSync(geohash, neighbors)
    }
    
    @Deprecated("Use getMarkersSync() instead", ReplaceWith("getMarkersSync(geohashPrefix, neighbors)"))
    override suspend fun getMarkersByGeohashSync(neighbors: List<String>, geohashPrefix: String): List<Marker> {
        Timber.d("Deprecated getMarkersByGeohashSync 호출 - 대신 통합 메서드 사용")
        return getMarkersSync(geohashPrefix, neighbors)
    }
    
    override suspend fun verifyDatabaseIntegrity() {
        // 현재 빈 구현 (향후 구현 예정)
        Timber.i("데이터베이스 무결성 검증 호출됨")
    }
    
    override suspend fun getCurrentLocationGeohash(): String? {
        // 실제 구현이 필요합니다
        Timber.d("현재 위치 geohash 요청됨")
        return null
    }
    
    override suspend fun getNeighborGeohashes(geohash: String): List<String> {
        // 기존 GeohashUtil 활용
        return GeoHashUtil.getNeighbors(geohash)
    }
    
    override suspend fun getAllCachedMarkers(): List<Marker> {
        // 캐시된 모든 마커 반환 로직
        val markers = mutableListOf<Marker>()
        markerDao.getAllMarkers().collect { entities ->
            markers.clear()
            markers.addAll(entities.map { markerMapper.toDomain(it) })
        }
        return markers
    }
    
    /**
     * 상태 기반 마커 저장 메서드
     * 마커의 상태(임시/영구/삭제)에 따라 적절한 저장 전략을 선택합니다.
     * 
     * @param marker 저장할 마커
     * @return 저장된 마커 또는 오류
     */
    override suspend fun saveMarkerWithState(marker: Marker): Result<Marker> = withContext(Dispatchers.IO) {
        Timber.d("상태 기반 마커 저장: id=${marker.id}, state=${marker.state}")
        
        try {
            // 상태에 따라 로컬/원격 저장 처리
            when (marker.state) {
                MarkerState.TEMPORARY -> {
                    // 임시 마커는 로컬에만 저장
                    Timber.d("임시 마커 로컬 저장: id=${marker.id}")
                    val entity = mapToEntity(marker)
                    markerDao.insertMarker(entity)
                    Result.success(marker)
                }
                
                MarkerState.PERSISTED -> {
                    // 영구 마커는 로컬 저장 후 네트워크 상태에 따라 원격 저장 시도
                    Timber.d("영구 마커 저장 (로컬+원격): id=${marker.id}")
                    val entity = mapToEntity(marker)
                    markerDao.insertMarker(entity)
                    
                    // 네트워크 연결 확인
                    if (syncRepository.isNetworkConnected()) {
                        try {
                            // 서버에 저장 시도
                            val remoteResult = syncRepository.createMarker(marker)
                            if (remoteResult != null) {
                                // 서버 응답으로 로컬 데이터 업데이트
                                val updatedEntity = mapToEntity(remoteResult)
                                markerDao.updateMarker(updatedEntity)
                                Timber.d("마커 서버 저장 성공: id=${marker.id}")
                                Result.success(remoteResult)
                            } else {
                                Timber.w("마커 서버 저장 실패 (null 응답): id=${marker.id}")
                                Result.success(marker) // 로컬 버전 반환
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "마커 서버 저장 중 오류 발생: id=${marker.id}")
                            Result.success(marker) // 로컬 버전 반환
                        }
                    } else {
                        Timber.d("네트워크 연결 없음, 로컬만 저장: id=${marker.id}")
                        Result.success(marker)
                    }
                }
                
                MarkerState.DELETED -> {
                    // 삭제 상태 마커는 저장하지 않음
                    Timber.w("삭제 상태 마커 저장 시도 무시: id=${marker.id}")
                    Result.success(marker)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "마커 저장 중 예외 발생: id=${marker.id}")
            Result.failure(e)
        }
    }
    
    /**
     * 상태 기반 마커 삭제 메서드
     * 마커의 상태(임시/영구/삭제)에 따라 적절한 삭제 전략을 선택합니다.
     * 임시 마커의 경우 API 호출 없이 로컬에서만 삭제됩니다.
     * 
     * @param markerId 삭제할 마커 ID
     * @return 삭제 결과
     */
    override suspend fun deleteMarkerWithState(markerId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        Timber.d("상태 기반 마커 삭제: id=$markerId")
        
        try {
            // 마커 상태 확인 (Lazy 사용)
            val state = markerStateAdapter.get().getMarkerState(markerId)
            Timber.d("마커 상태 확인: id=$markerId, state=$state")
            
            // 로컬에서 마커 삭제
            val entity = markerDao.getMarkerById(markerId)
            if (entity != null) {
                markerDao.deleteMarker(entity)
                Timber.d("마커 로컬 삭제 완료: id=$markerId")
            } else {
                Timber.w("로컬에서 마커를 찾을 수 없음: id=$markerId")
            }
            
            // 상태에 따른 서버 삭제 처리
            when (state) {
                MarkerState.TEMPORARY -> {
                    // 임시 마커는 서버 API 호출 없이 로컬에서만 삭제
                    Timber.d("임시 마커 삭제 완료 (API 호출 없음): id=$markerId")
                    Result.success(true)
                }
                
                MarkerState.PERSISTED -> {
                    // 영구 마커는 네트워크 상태에 따라 서버 삭제 시도
                    if (syncRepository.isNetworkConnected()) {
                        try {
                            // 서버에서 삭제 시도
                            val deleteResult = syncRepository.deleteMarker(markerId)
                            if (deleteResult) {
                                Timber.d("마커 서버 삭제 성공: id=$markerId")
                            } else {
                                Timber.w("마커 서버 삭제 실패: id=$markerId")
                            }
                            Result.success(true) // 로컬 삭제는 이미 완료됨
                        } catch (e: Exception) {
                            Timber.e(e, "마커 서버 삭제 중 오류 발생: id=$markerId")
                            // 오류가 발생해도 로컬 삭제는 성공으로 간주
                            Result.success(true)
                        }
                    } else {
                        Timber.d("네트워크 연결 없음, 로컬만 삭제: id=$markerId")
                        Result.success(true)
                    }
                }
                
                MarkerState.DELETED -> {
                    // 이미 삭제된 상태
                    Timber.d("이미 삭제 상태인 마커: id=$markerId")
                    Result.success(true)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "마커 삭제 중 예외 발생: id=$markerId")
            Result.failure(e)
        }
    }
} 