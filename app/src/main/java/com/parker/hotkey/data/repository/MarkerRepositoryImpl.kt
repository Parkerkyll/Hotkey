package com.parker.hotkey.data.repository

import com.parker.hotkey.data.local.dao.MarkerDao
import com.parker.hotkey.data.local.entity.MarkerEntity
import com.parker.hotkey.data.local.entity.SyncState
import com.parker.hotkey.domain.repository.MarkerRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarkerRepositoryImpl @Inject constructor(
    private val markerDao: MarkerDao
) : MarkerRepository {
    
    override fun getAllMarkers(): Flow<List<MarkerEntity>> {
        return markerDao.getAllMarkers()
    }
    
    override fun getMarkersByGeohash(geohashPrefix: String): Flow<List<MarkerEntity>> {
        return markerDao.getMarkersByGeohash(geohashPrefix)
    }
    
    override fun getMarkersInGeohashRange(centerGeohash: String, neighbors: List<String>): Flow<List<MarkerEntity>> {
        val validGeohashes = neighbors + centerGeohash
        return markerDao.getMarkersInGeohashList(validGeohashes)
    }
    
    override suspend fun getMarkerById(markerId: String): MarkerEntity? {
        return markerDao.getMarkerById(markerId)
    }
    
    override suspend fun createMarker(latitude: Double, longitude: Double, geohash: String): MarkerEntity {
        val now = System.currentTimeMillis()
        val marker = MarkerEntity(
            id = UUID.randomUUID().toString(),
            geohash = geohash,
            latitude = latitude,
            longitude = longitude,
            createdAt = now,
            modifiedAt = now,
            syncedAt = null,
            syncState = SyncState.PENDING,
            version = 1
        )
        markerDao.insertMarker(marker)
        return marker
    }
    
    override suspend fun updateMarker(marker: MarkerEntity) {
        markerDao.updateMarker(marker.copy(
            modifiedAt = System.currentTimeMillis(),
            syncState = SyncState.PENDING
        ))
    }
    
    override suspend fun deleteMarker(markerId: String) {
        markerDao.deleteMarkerIfNoMemos(markerId, System.currentTimeMillis())
    }
    
    override suspend fun getMarkerCount(): Int {
        return markerDao.getMarkerCount()
    }
    
    override suspend fun getUnsyncedMarkers(): List<MarkerEntity> {
        return markerDao.getMarkersBySyncState(SyncState.PENDING)
    }
} 