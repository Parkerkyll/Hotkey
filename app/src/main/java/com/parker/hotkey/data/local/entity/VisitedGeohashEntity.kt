package com.parker.hotkey.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "visited_geohash")
data class VisitedGeohashEntity(
    @PrimaryKey
    val geohash: String,
    val firstVisitTime: Long,
    val lastVisitTime: Long,
    val visitCount: Int = 1,
    val hasLocalData: Boolean = false,
    val hasSyncedWithServer: Boolean = false
) 