package com.parker.hotkey.data.repository

import com.parker.hotkey.data.local.dao.MarkerDao
import com.parker.hotkey.data.local.entity.MarkerEntity
import com.parker.hotkey.data.mapper.MarkerEntityMapper
import com.parker.hotkey.domain.manager.MarkerStateAdapter
import com.parker.hotkey.domain.model.LastSync
import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.model.MarkerState
import com.parker.hotkey.domain.repository.SyncRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID
import javax.inject.Named
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import dagger.Lazy

class MarkerRepositoryImplTest {
    // 테스트 대상
    private lateinit var repository: MarkerRepositoryImpl
    
    // Mock 객체들
    private lateinit var markerDao: MarkerDao
    private lateinit var markerMapper: MarkerEntityMapper
    private lateinit var cacheAdapter: MarkerRepositoryCacheAdapter
    private lateinit var syncRepository: SyncRepository
    private lateinit var markerStateAdapter: MarkerStateAdapter
    private lateinit var lazyMarkerStateAdapter: Lazy<MarkerStateAdapter>
    
    @Before
    fun setup() {
        // Mock 객체 생성
        markerDao = mockk(relaxed = true)
        markerMapper = mockk(relaxed = true)
        cacheAdapter = mockk(relaxed = true)
        syncRepository = mockk(relaxed = true)
        markerStateAdapter = mockk(relaxed = true)
        
        // Lazy 래퍼 설정
        lazyMarkerStateAdapter = mockk()
        every { lazyMarkerStateAdapter.get() } returns markerStateAdapter
        
        // Repository 생성
        repository = MarkerRepositoryImpl(
            markerDao = markerDao,
            markerMapper = markerMapper,
            cacheAdapter = cacheAdapter,
            syncRepository = syncRepository,
            markerStateAdapter = lazyMarkerStateAdapter
        )
        
        // 기본 Stub 설정
        val sampleEntity = MarkerEntity(
            id = "marker-1",
            userId = "user-1",
            latitude = 37.5665,
            longitude = 126.9780,
            geohash = "wydm9q",
            syncStatus = 0,
            syncTimestamp = 0L,
            syncError = null,
            modifiedAt = System.currentTimeMillis()
        )
        
        val sampleMarker = Marker(
            id = "marker-1",
            userId = "user-1",
            latitude = 37.5665,
            longitude = 126.9780,
            geohash = "wydm9q"
        )
        
        // Mapper 설정
        every { markerMapper.toEntity(any()) } returns sampleEntity
        every { markerMapper.toDomain(any()) } returns sampleMarker
        
        // 네트워크 연결 상태
        every { syncRepository.isNetworkConnected() } returns true
    }
    
    @Test
    fun `saveMarkerWithState with TEMPORARY marker should only save locally`() = runTest {
        // Given
        val temporaryMarker = Marker(
            id = UUID.randomUUID().toString(),
            userId = "user-1",
            latitude = 37.5665,
            longitude = 126.9780,
            geohash = "wydm9q",
            state = MarkerState.TEMPORARY
        )
        
        // When
        val result = repository.saveMarkerWithState(temporaryMarker)
        
        // Then
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { markerDao.insertMarker(any()) }
        coVerify(exactly = 0) { syncRepository.createMarker(any()) }
    }
    
    @Test
    fun `saveMarkerWithState with PERSISTED marker should save locally and remotely`() = runTest {
        // Given
        val persistedMarker = Marker(
            id = UUID.randomUUID().toString(),
            userId = "user-1",
            latitude = 37.5665,
            longitude = 126.9780,
            geohash = "wydm9q",
            state = MarkerState.PERSISTED
        )
        
        val remoteMarker = persistedMarker.copy(
            lastSync = LastSync.createSuccess(1L)
        )
        
        coEvery { syncRepository.createMarker(any()) } returns remoteMarker
        
        // When
        val result = repository.saveMarkerWithState(persistedMarker)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(remoteMarker, result.getOrNull())
        coVerify(exactly = 1) { markerDao.insertMarker(any()) }
        coVerify(exactly = 1) { syncRepository.createMarker(any()) }
    }
    
    @Test
    fun `deleteMarkerWithState with TEMPORARY marker should only delete locally`() = runTest {
        // Given
        val markerId = UUID.randomUUID().toString()
        every { markerStateAdapter.getMarkerState(markerId) } returns MarkerState.TEMPORARY
        
        val entity = MarkerEntity(
            id = markerId,
            userId = "user-1",
            latitude = 37.5665,
            longitude = 126.9780,
            geohash = "wydm9q",
            syncStatus = 0,
            syncTimestamp = 0L,
            syncError = null,
            modifiedAt = System.currentTimeMillis()
        )
        
        coEvery { markerDao.getMarkerById(markerId) } returns entity
        
        // When
        val result = repository.deleteMarkerWithState(markerId)
        
        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
        coVerify(exactly = 1) { markerDao.deleteMarker(any()) }
        coVerify(exactly = 0) { syncRepository.deleteMarker(any()) }
    }
    
    @Test
    fun `deleteMarkerWithState with PERSISTED marker should delete locally and remotely`() = runTest {
        // Given
        val markerId = UUID.randomUUID().toString()
        every { markerStateAdapter.getMarkerState(markerId) } returns MarkerState.PERSISTED
        
        val entity = MarkerEntity(
            id = markerId,
            userId = "user-1",
            latitude = 37.5665,
            longitude = 126.9780,
            geohash = "wydm9q",
            syncStatus = 0,
            syncTimestamp = 0L,
            syncError = null,
            modifiedAt = System.currentTimeMillis()
        )
        
        coEvery { markerDao.getMarkerById(markerId) } returns entity
        coEvery { syncRepository.deleteMarker(markerId) } returns true
        
        // When
        val result = repository.deleteMarkerWithState(markerId)
        
        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
        coVerify(exactly = 1) { markerDao.deleteMarker(any()) }
        coVerify(exactly = 1) { syncRepository.deleteMarker(markerId) }
    }
    
    @Test
    fun `deleteMarkerWithState should handle non-existent marker gracefully`() = runTest {
        // Given
        val markerId = UUID.randomUUID().toString()
        every { markerStateAdapter.getMarkerState(markerId) } returns MarkerState.DELETED
        coEvery { markerDao.getMarkerById(markerId) } returns null
        
        // When
        val result = repository.deleteMarkerWithState(markerId)
        
        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
        coVerify(exactly = 0) { markerDao.deleteMarker(any()) }
        coVerify(exactly = 0) { syncRepository.deleteMarker(any()) }
    }
    
    @Test
    fun `saveMarkerWithState with PERSISTED marker should handle network errors gracefully`() = runTest {
        // Given
        val persistedMarker = Marker(
            id = UUID.randomUUID().toString(),
            userId = "user-1",
            latitude = 37.5665,
            longitude = 126.9780,
            geohash = "wydm9q",
            state = MarkerState.PERSISTED
        )
        
        coEvery { syncRepository.createMarker(any()) } throws RuntimeException("네트워크 오류")
        
        // When
        val result = repository.saveMarkerWithState(persistedMarker)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(persistedMarker, result.getOrNull())
        coVerify(exactly = 1) { markerDao.insertMarker(any()) }
        coVerify(exactly = 1) { syncRepository.createMarker(any()) }
    }
    
    @Test
    fun `deleteMarkerWithState with PERSISTED marker should handle network errors gracefully`() = runTest {
        // Given
        val markerId = UUID.randomUUID().toString()
        every { markerStateAdapter.getMarkerState(markerId) } returns MarkerState.PERSISTED
        
        val entity = MarkerEntity(
            id = markerId,
            userId = "user-1",
            latitude = 37.5665,
            longitude = 126.9780,
            geohash = "wydm9q",
            syncStatus = 0,
            syncTimestamp = 0L,
            syncError = null,
            modifiedAt = System.currentTimeMillis()
        )
        
        coEvery { markerDao.getMarkerById(markerId) } returns entity
        coEvery { syncRepository.deleteMarker(markerId) } throws RuntimeException("네트워크 오류")
        
        // When
        val result = repository.deleteMarkerWithState(markerId)
        
        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
        coVerify(exactly = 1) { markerDao.deleteMarker(any()) }
        coVerify(exactly = 1) { syncRepository.deleteMarker(markerId) }
    }
} 