package com.parker.hotkey.data.repository

import com.parker.hotkey.data.local.dao.MarkerDao
import com.parker.hotkey.data.local.entity.MarkerEntity
import com.parker.hotkey.data.local.entity.SyncState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MarkerRepositoryTest {
    private lateinit var markerDao: MarkerDao
    private lateinit var repository: MarkerRepository
    
    @Before
    fun setup() {
        markerDao = mockk(relaxed = true)
        repository = MarkerRepositoryImpl(markerDao)
    }
    
    @Test
    fun `createMarker should create and return a new marker`() = runTest {
        // Given
        val latitude = 37.5665
        val longitude = 126.9780
        val geohash = "wydm9q"
        
        // When
        val result = repository.createMarker(latitude, longitude, geohash)
        
        // Then
        assertNotNull(result)
        assertEquals(geohash, result.geohash)
        assertEquals(latitude, result.latitude)
        assertEquals(longitude, result.longitude)
        assertEquals(SyncState.PENDING, result.syncState)
        coVerify { markerDao.insertMarker(any()) }
    }
    
    @Test
    fun `getMarkersByGeohash should return markers for given geohash`() = runTest {
        // Given
        val geohash = "wydm9q"
        val markers = listOf(
            MarkerEntity(
                id = "1",
                geohash = geohash,
                latitude = 37.5665,
                longitude = 126.9780,
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis(),
                syncedAt = null,
                syncState = SyncState.PENDING,
                version = 1
            )
        )
        coEvery { markerDao.getMarkersByGeohash(geohash) } returns flowOf(markers)
        
        // When
        val result = repository.getMarkersByGeohash(geohash)
        
        // Then
        result.collect { resultMarkers ->
            assertEquals(markers, resultMarkers)
        }
    }
    
    @Test
    fun `deleteMarker should call dao deleteMarkerIfNoMemos`() = runTest {
        // Given
        val markerId = "1"
        
        // When
        repository.deleteMarker(markerId)
        
        // Then
        coVerify { markerDao.deleteMarkerIfNoMemos(markerId, any()) }
    }
} 