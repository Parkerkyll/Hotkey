package com.parker.hotkey.domain.repository

import com.parker.hotkey.domain.repository.BaseRepository
import com.parker.hotkey.domain.model.Marker
import kotlinx.coroutines.flow.Flow

/**
 * 마커 조회 옵션 데이터 클래스
 */
data class MarkerQueryOptions(
    val precision: Int = 6,           // geohash 정밀도 (1-12)
    val limit: Int = 200,            // 최대 마커 수 제한
    val orderByDistance: Boolean = true,  // 거리 기준 정렬 여부
    val zoom: Double? = null,         // 지도 줌 레벨 (캐시 키 생성에 사용)
)

interface MarkerRepository : BaseRepository<Marker> {
    /**
     * 모든 마커 조회 방식을 통합한 단일 메서드
     * 
     * @param geohash 기준 geohash
     * @param neighbors 이웃 geohash 목록 (선택 사항)
     * @param options 조회 옵션 (정밀도, 제한, 동기화 등)
     * @return 마커 목록 (Flow)
     */
    fun getMarkers(
        geohash: String,
        neighbors: List<String> = emptyList(),
        options: MarkerQueryOptions = MarkerQueryOptions()
    ): Flow<List<Marker>>
    
    /**
     * 동기식 버전의 마커 조회 (즉시 결과 반환)
     */
    suspend fun getMarkersSync(
        geohash: String,
        neighbors: List<String> = emptyList(),
        options: MarkerQueryOptions = MarkerQueryOptions()
    ): List<Marker>

    /**
     * 기본 geohash 기반 마커 조회
     * @deprecated 대신 getMarkers() 사용 권장
     */
    @Deprecated("Use getMarkers() instead", ReplaceWith("getMarkers(geohashPrefix, neighbors)"))
    fun getMarkersByGeohash(geohashPrefix: String, neighbors: List<String>): Flow<List<Marker>>
    
    /**
     * geohash6 전용 최적화된 마커 조회 메서드
     * 항상 정밀도 6을 사용하고 모든 마커를 가져옵니다.
     * 
     * @param geohashPrefix 현재 위치의 geohash6
     * @param neighbors 이웃 geohash6 목록
     * @param zoomLevel 지도 줌 레벨 (화면 표시용으로만 사용, 쿼리에 영향 없음)
     * @param limit 더 이상 사용하지 않음 (호환성을 위해 유지)
     * @deprecated 대신 getMarkers() 사용 권장
     */
    @Deprecated("Use getMarkers() instead", ReplaceWith("getMarkers(geohashPrefix, neighbors, MarkerQueryOptions(limit = limit))"))
    fun getMarkersByGeohashOptimized(
        geohashPrefix: String, 
        neighbors: List<String>,
        zoomLevel: Double,
        limit: Int = Int.MAX_VALUE
    ): Flow<List<Marker>>
    
    /**
     * 정밀도와 제한을 지정하여 geohash 기반 마커 조회
     * 
     * @param geohashPrefix 현재 위치의 geohash
     * @param neighbors 주변 geohash 목록
     * @param precision geohash 정밀도 (1-12)
     * @param limit 최대 마커 수 제한
     * @return 마커 목록
     * @deprecated 대신 getMarkers() 사용 권장
     */
    @Deprecated("Use getMarkers() instead", ReplaceWith("getMarkers(geohashPrefix, neighbors, MarkerQueryOptions(precision = precision, limit = limit))"))
    fun getMarkersByGeohashWithLimit(
        geohashPrefix: String,
        neighbors: List<String>,
        precision: Int,
        limit: Int
    ): Flow<List<Marker>>
    
    /**
     * geohash6 정밀도로 마커 수를 제한하여 조회
     * 
     * @param geohashPrefix 현재 위치의 geohash6
     * @param neighbors 주변 geohash6 목록
     * @param limit 최대 마커 수 제한
     * @return 마커 목록
     * @deprecated 대신 getMarkers() 사용 권장
     */
    @Deprecated("Use getMarkers() instead", ReplaceWith("getMarkers(geohashPrefix, neighbors, MarkerQueryOptions(limit = limit))"))
    fun getMarkersByGeohash6WithLimit(
        geohashPrefix: String,
        neighbors: List<String>,
        limit: Int
    ): Flow<List<Marker>>
    
    fun getMarkersInGeohashList(geohashes: List<String>): Flow<List<Marker>>
    suspend fun getMarkerCount(): Int
    suspend fun createMarker(userId: String, latitude: Double, longitude: Double): Marker
    suspend fun deleteMarkerIfNoMemos(markerId: String)
    
    /**
     * 지정된 geohash와 그 주변 영역에 있는 마커들을 가져옵니다.
     * geohash6 기반으로 모든 마커를 조회합니다.
     * 
     * @param geohash 중심 geohash
     * @param neighbors 이웃 geohash 목록
     * @return geohash 영역 내 마커 목록
     * @deprecated 대신 getMarkersSync() 사용 권장
     */
    @Deprecated("Use getMarkersSync() instead", ReplaceWith("getMarkersSync(geohash, neighbors)"))
    suspend fun getMarkersByGeohashArea(
        geohash: String,
        neighbors: List<String>
    ): List<Marker>

    /**
     * 특정 geohash에 해당하는 마커를 동기식으로 조회합니다.
     * 디버깅 목적으로 사용됩니다.
     * 
     * @param neighbors 이웃 geohash 목록
     * @param geohashPrefix 중심 geohash
     * @return 마커 목록
     * @deprecated 대신 getMarkersSync() 사용 권장
     */
    @Deprecated("Use getMarkersSync() instead", ReplaceWith("getMarkersSync(geohashPrefix, neighbors)"))
    suspend fun getMarkersByGeohashSync(neighbors: List<String>, geohashPrefix: String): List<Marker>

    /**
     * 데이터베이스 무결성 검증 (필요시 복구)
     */
    suspend fun verifyDatabaseIntegrity()
    
    /**
     * 현재 위치의 geohash 가져오기
     */
    suspend fun getCurrentLocationGeohash(): String?
    
    /**
     * 지정된 geohash의 이웃 geohash 목록 가져오기
     */
    suspend fun getNeighborGeohashes(geohash: String): List<String>
    
    /**
     * 캐시된 모든 마커 가져오기
     */
    suspend fun getAllCachedMarkers(): List<Marker>

    /**
     * 상태 기반 마커 저장 메서드
     * 마커의 상태(임시/영구/삭제)에 따라 적절한 저장 전략을 선택합니다.
     * 
     * @param marker 저장할 마커
     * @return 저장된 마커 또는 오류
     */
    suspend fun saveMarkerWithState(marker: Marker): Result<Marker>
    
    /**
     * 상태 기반 마커 삭제 메서드
     * 마커의 상태(임시/영구/삭제)에 따라 적절한 삭제 전략을 선택합니다.
     * 임시 마커의 경우 API 호출 없이 로컬에서만 삭제됩니다.
     * 
     * @param markerId 삭제할 마커 ID
     * @return 삭제 결과
     */
    suspend fun deleteMarkerWithState(markerId: String): Result<Boolean>
} 