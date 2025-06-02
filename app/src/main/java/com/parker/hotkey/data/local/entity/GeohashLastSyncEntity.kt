package com.parker.hotkey.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "geohash_last_sync")
data class GeohashLastSyncEntity(
    @PrimaryKey
    val geohash: String,
    val lastSyncTimestamp: Long
) 