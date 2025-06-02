package com.parker.hotkey.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.parker.hotkey.data.local.entity.GeohashLastSyncEntity

@Dao
interface GeohashLastSyncDao {
    @Query("SELECT lastSyncTimestamp FROM geohash_last_sync WHERE geohash = :geohash")
    suspend fun getLastSyncTimestamp(geohash: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateLastSyncTimestamp(entity: GeohashLastSyncEntity)
} 