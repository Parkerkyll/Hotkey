package com.parker.hotkey.domain.usecase.marker

/*
import com.parker.hotkey.domain.repository.MarkerRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreateMarkerUseCaseTest {
    private lateinit var markerRepository: MarkerRepository
    private lateinit var createMarkerUseCase: CreateMarkerUseCase
    
    @Before
    fun setup() {
        markerRepository = mockk(relaxed = true)
        createMarkerUseCase = CreateMarkerUseCase(markerRepository)
    }
    
    @Test
    fun `invoke should return success with marker when repository succeeds`() = runTest {
        // Given
        val latitude = 37.5665
        val longitude = 126.9780
        val geohash = "wydm9q"
        
        val marker = Marker(
            id = "1",
            geohash = geohash,
            latitude = latitude,
            longitude = longitude,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis(),
            syncedAt = null,
            syncState = SyncState.PENDING,
            version = 1
        )
        
        coEvery { markerRepository.createMarker(latitude, longitude, geohash) } returns marker
        
        // When
        val result = createMarkerUseCase(37.5665, "126.9780", "wydm9q")
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(marker, result.getOrNull())
    }
    
    @Test
    fun `invoke should return failure when repository throws exception`() = runTest {
        // Given
        val latitude = 37.5665
        val longitude = 126.9780
        val geohash = "wydm9q"
        val exception = RuntimeException("Test exception")
        
        coEvery { markerRepository.createMarker(latitude, longitude, geohash) } throws exception
        
        // When
        val result = createMarkerUseCase(37.5665, "126.9780", "wydm9q")
        
        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }
}
*/ 