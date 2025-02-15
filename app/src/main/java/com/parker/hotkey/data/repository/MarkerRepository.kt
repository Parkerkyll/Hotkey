package com.parker.hotkey.data.repository

import com.parker.hotkey.data.local.entity.MarkerEntity
import kotlinx.coroutines.flow.Flow

interface MarkerRepository {
    fun getAllMarkers(): Flow<List<MarkerEntity>>
    fun getMarkersByGeohash(geohashPrefix: String): Flow<List<MarkerEntity>>
    suspend fun getMarkerById(markerId: String): MarkerEntity?
    suspend fun createMarker(latitude: Double, longitude: Double, geohash: String): MarkerEntity
    suspend fun updateMarker(marker: MarkerEntity)
    suspend fun deleteMarker(markerId: String)
    suspend fun getMarkerCount(): Int
    suspend fun getUnsyncedMarkers(): List<MarkerEntity>
} 