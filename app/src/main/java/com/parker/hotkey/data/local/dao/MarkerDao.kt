package com.parker.hotkey.data.local.dao

import androidx.room.*
import com.parker.hotkey.data.local.entity.MarkerEntity
import com.parker.hotkey.data.local.entity.SyncState
import kotlinx.coroutines.flow.Flow

@Dao
interface MarkerDao {
    @Query("SELECT * FROM markers WHERE isDeleted = 0")
    fun getAllMarkers(): Flow<List<MarkerEntity>>
    
    @Query("SELECT * FROM markers WHERE geohash LIKE :geohashPrefix || '%' AND isDeleted = 0")
    fun getMarkersByGeohash(geohashPrefix: String): Flow<List<MarkerEntity>>
    
    @Query("SELECT * FROM markers WHERE id = :markerId AND isDeleted = 0")
    suspend fun getMarkerById(markerId: String): MarkerEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarker(marker: MarkerEntity)
    
    @Update
    suspend fun updateMarker(marker: MarkerEntity)
    
    @Query("UPDATE markers SET isDeleted = 1, modifiedAt = :timestamp, syncState = :syncState WHERE id = :markerId")
    suspend fun softDeleteMarker(markerId: String, timestamp: Long, syncState: SyncState = SyncState.PENDING)
    
    @Query("SELECT COUNT(*) FROM markers WHERE isDeleted = 0")
    suspend fun getMarkerCount(): Int
    
    @Query("SELECT * FROM markers WHERE syncState = :syncState AND isDeleted = 0")
    suspend fun getMarkersBySyncState(syncState: SyncState): List<MarkerEntity>
    
    @Transaction
    suspend fun deleteMarkerIfNoMemos(markerId: String, timestamp: Long) {
        val memoCount = getMemoCountForMarker(markerId)
        if (memoCount == 0) {
            softDeleteMarker(markerId, timestamp)
        }
    }
    
    @Transaction
    suspend fun forceDeleteMarker(markerId: String, timestamp: Long) {
        softDeleteMarker(markerId, timestamp)
    }
    
    @Query("SELECT COUNT(*) FROM memos WHERE markerId = :markerId AND isDeleted = 0")
    suspend fun getMemoCountForMarker(markerId: String): Int

    @Query("SELECT * FROM markers WHERE geohash IN (:geohashList)")
    fun getMarkersInGeohashList(geohashList: List<String>): Flow<List<MarkerEntity>>
} 