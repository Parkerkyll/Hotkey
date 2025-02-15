package com.parker.hotkey.domain.usecase.marker

import com.parker.hotkey.data.local.entity.MarkerEntity
import com.parker.hotkey.data.repository.MarkerRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreateMarkerUseCaseTest {
    private lateinit var repository: MarkerRepository
    private lateinit var useCase: CreateMarkerUseCase
    
    @Before
    fun setup() {
        repository = mockk()
        useCase = CreateMarkerUseCase(repository)
    }
    
    @Test
    fun `invoke should return success with marker when creation succeeds`() = runTest {
        // Given
        val latitude = 37.5665
        val longitude = 126.9780
        val geohash = "wydm9q"
        val marker = MarkerEntity(
            id = "1",
            geohash = geohash,
            latitude = latitude,
            longitude = longitude,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis(),
            syncedAt = null,
            syncState = com.parker.hotkey.data.local.entity.SyncState.PENDING,
            version = 1
        )
        
        coEvery { repository.createMarker(latitude, longitude, geohash) } returns marker
        
        // When
        val result = useCase(latitude, longitude, geohash)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(marker, result.getOrNull())
    }
    
    @Test
    fun `invoke should return failure when creation fails`() = runTest {
        // Given
        val latitude = 37.5665
        val longitude = 126.9780
        val geohash = "wydm9q"
        val exception = RuntimeException("Creation failed")
        
        coEvery { repository.createMarker(latitude, longitude, geohash) } throws exception
        
        // When
        val result = useCase(latitude, longitude, geohash)
        
        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }
} 