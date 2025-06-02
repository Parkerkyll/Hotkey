package com.parker.hotkey.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.parker.hotkey.data.local.entity.VisitedGeohashEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitedGeohashDao {
    @Query("SELECT * FROM visited_geohash WHERE geohash = :geohash")
    suspend fun getVisitedGeohash(geohash: String): VisitedGeohashEntity?
    
    @Query("SELECT * FROM visited_geohash ORDER BY lastVisitTime DESC")
    fun getVisitedGeohashesFlow(): Flow<List<VisitedGeohashEntity>>
    
    @Query("SELECT * FROM visited_geohash ORDER BY lastVisitTime DESC")
    suspend fun getVisitedGeohashes(): List<VisitedGeohashEntity>
    
    @Query("SELECT COUNT(*) FROM visited_geohash")
    suspend fun getVisitedGeohashCount(): Int
    
    @Query("SELECT * FROM visited_geohash WHERE hasSyncedWithServer = 0")
    suspend fun getUnsyncedGeohashes(): List<VisitedGeohashEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisitedGeohash(entity: VisitedGeohashEntity)
    
    @Update
    suspend fun updateVisitedGeohash(entity: VisitedGeohashEntity)
    
    @Query("UPDATE visited_geohash SET lastVisitTime = :lastVisitTime, visitCount = visitCount + 1 WHERE geohash = :geohash")
    suspend fun updateVisitInfo(geohash: String, lastVisitTime: Long)
    
    @Query("UPDATE visited_geohash SET hasSyncedWithServer = 1 WHERE geohash = :geohash")
    suspend fun markAsSynced(geohash: String)
    
    @Query("UPDATE visited_geohash SET hasLocalData = 1 WHERE geohash = :geohash")
    suspend fun markAsHavingLocalData(geohash: String)
} 