package com.parker.hotkey.data.local.dao

import androidx.room.*
import com.parker.hotkey.data.local.entity.MarkerEntity
import com.parker.hotkey.domain.model.LastSync
import kotlinx.coroutines.flow.Flow

@Dao
interface MarkerDao {
    @Query("SELECT * FROM markers")
    fun getAllMarkers(): Flow<List<MarkerEntity>>
    
    /**
     * 최적화된 Geohash 쿼리 (여러 geohash 메소드 통합)
     * 
     * @param geohashes 검색할 geohash 목록
     * @param baseGeohash 기준이 되는 geohash (정렬 기준점으로 사용)
     * @param limit 결과 제한 (기본값 Int.MAX_VALUE)
     * @return 지정된 geohash 영역의 마커 목록 Flow
     */
    @Query("""
        SELECT * FROM markers 
        WHERE geohash IN (:geohashes)
        ORDER BY 
            CASE WHEN geohash = :baseGeohash THEN 0 ELSE 1 END, 
            modifiedAt DESC
        LIMIT :limit
    """)
    fun getMarkersInGeohashArea(
        geohashes: List<String>,
        baseGeohash: String,
        limit: Int = Int.MAX_VALUE
    ): Flow<List<MarkerEntity>>
    
    /**
     * Geohash6 영역에 최적화된 마커 조회 (LIKE 쿼리 제거)
     * 
     * @param geohashes 검색할 geohash 목록 (정밀도 6 수준의 geohash)
     * @param baseGeohash 기준이 되는 geohash (정렬 기준점으로 사용)
     * @param limit 결과 제한 (기본값 200)
     * @return 지정된 geohash 영역의 마커 목록 Flow
     */
    @Query("""
        SELECT * FROM markers 
        WHERE geohash IN (:geohashes)
        ORDER BY 
            CASE WHEN geohash = :baseGeohash THEN 0 ELSE 1 END, 
            modifiedAt DESC
        LIMIT :limit
    """)
    fun getMarkersInGeohash6AreaOptimized(
        geohashes: List<String>,
        baseGeohash: String,
        limit: Int = 200
    ): Flow<List<MarkerEntity>>
    
    /**
     * 거리 기반 마커 필터링 - 성능 최적화 버전
     *
     * @param geohashes 검색할 geohash 목록 (이웃 geohash 포함)
     * @param baseGeohash 기준이 되는 geohash (정렬 기준점으로 사용)
     * @param baseLat 기준 위도
     * @param baseLng 기준 경도
     * @param radiusKm 검색 반경 (킬로미터)
     * @param limit 결과 제한
     * @return 지정된 반경 내의 마커 목록 Flow
     */
    @Query("""
        SELECT * FROM markers 
        WHERE geohash IN (:geohashes) AND 
        ((:baseLat - latitude) * (:baseLat - latitude) + 
         (:baseLng - longitude) * (:baseLng - longitude)) <= (:radiusKm * :radiusKm / 12756.2) 
        ORDER BY 
            CASE WHEN geohash = :baseGeohash THEN 0 ELSE 1 END,
            ((:baseLat - latitude) * (:baseLat - latitude) + 
             (:baseLng - longitude) * (:baseLng - longitude)) ASC, 
            modifiedAt DESC
        LIMIT :limit
    """)
    fun getMarkersInRadiusOptimized(
        geohashes: List<String>,
        baseGeohash: String,
        baseLat: Double,
        baseLng: Double,
        radiusKm: Double,
        limit: Int = 100
    ): Flow<List<MarkerEntity>>
    
    /**
     * ID로 마커 조회
     */
    @Query("SELECT * FROM markers WHERE id = :id")
    suspend fun getMarkerById(id: String): MarkerEntity?
    
    /**
     * 마커 삽입
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarker(marker: MarkerEntity)
    
    /**
     * 여러 마커 삽입
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarkers(markers: List<MarkerEntity>)
    
    @Update
    suspend fun updateMarker(marker: MarkerEntity)
    
    /**
     * 마커 객체 삭제
     */
    @Delete
    suspend fun deleteMarker(marker: MarkerEntity)
    
    /**
     * ID로 마커 삭제
     */
    @Query("DELETE FROM markers WHERE id = :id")
    suspend fun deleteMarkerById(id: String)
    
    @Query("SELECT COUNT(*) FROM markers")
    suspend fun getMarkerCount(): Int
    
    @Query("""
        SELECT * FROM markers 
        WHERE syncStatus = :syncStatus
    """)
    suspend fun getUnsyncedMarkers(syncStatus: Int = LastSync.SyncStatus.NONE.ordinal): List<MarkerEntity>
    
    @Query("UPDATE markers SET syncStatus = :syncStatus, syncTimestamp = :syncTimestamp, syncError = :syncError WHERE id IN (:markerIds)")
    suspend fun updateMarkersLastSync(markerIds: List<String>, syncStatus: Int, syncTimestamp: Long, syncError: String?)
    
    @Transaction
    suspend fun deleteMarkerWithMemos(markerId: String) {
        deleteMarkerById(markerId)
        // CASCADE로 연관 메모 자동 삭제됨
    }

    @Query("SELECT COUNT(*) FROM memos WHERE markerId = :markerId")
    suspend fun getMemoCount(markerId: String): Int
    
    @Query("DELETE FROM markers WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
    
    @Query("UPDATE markers SET syncStatus = :status, syncTimestamp = :timestamp, syncError = :error WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: Int, timestamp: Long, error: String?)

    /**
     * 주어진 geohash 목록에 해당하는 마커를 가져옵니다. (suspend 함수)
     * 
     * @param geohashes 조회할 geohash 목록
     * @param baseGeohash 기준이 되는 geohash (정렬용)
     * @return 해당 geohash 영역의 마커 목록
     */
    @Query("""
        SELECT * FROM markers 
        WHERE geohash IN (:geohashes) 
        ORDER BY 
            CASE WHEN geohash = :baseGeohash THEN 0 ELSE 1 END, 
            modifiedAt DESC
    """)
    suspend fun getMarkersByGeohash(
        geohashes: List<String>,
        baseGeohash: String
    ): List<MarkerEntity>
} 