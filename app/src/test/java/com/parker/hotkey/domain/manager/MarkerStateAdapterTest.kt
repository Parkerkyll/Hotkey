package com.parker.hotkey.domain.manager

import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.model.MarkerState
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class MarkerStateAdapterTest {

    @MockK
    private lateinit var markerManager: MarkerManager
    
    @MockK
    private lateinit var temporaryMarkerManager: TemporaryMarkerManager
    
    private val testCoroutineScope = TestCoroutineScope()
    
    private lateinit var markerStateAdapter: MarkerStateAdapter
    
    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        markerStateAdapter = MarkerStateAdapter(
            markerManager = markerManager,
            temporaryMarkerManager = temporaryMarkerManager,
            coroutineScope = testCoroutineScope
        )
    }
    
    @Test
    fun `isTemporaryMarker가 TemporaryMarkerManager와 올바르게 연동되는지 확인`() {
        // Given
        val markerId = "test-marker-1"
        every { temporaryMarkerManager.isTemporaryMarker(markerId) } returns true
        
        // When
        val result = markerStateAdapter.isTemporaryMarker(markerId)
        
        // Then
        assertTrue(result)
        verify { temporaryMarkerManager.isTemporaryMarker(markerId) }
    }
    
    @Test
    fun `getMarkerState가 임시 마커일 경우 TEMPORARY 반환하는지 확인`() {
        // Given
        val markerId = "test-marker-1"
        every { temporaryMarkerManager.isTemporaryMarker(markerId) } returns true
        
        // When
        val state = markerStateAdapter.getMarkerState(markerId)
        
        // Then
        assertEquals(MarkerState.TEMPORARY, state)
        verify { temporaryMarkerManager.isTemporaryMarker(markerId) }
        verify(exactly = 0) { markerManager.getMarkerById(any()) }
    }
    
    @Test
    fun `getMarkerState가 영구 마커일 경우 PERSISTED 반환하는지 확인`() {
        // Given
        val markerId = "test-marker-1"
        val marker = mockk<Marker>()
        every { temporaryMarkerManager.isTemporaryMarker(markerId) } returns false
        every { markerManager.getMarkerById(markerId) } returns marker
        
        // When
        val state = markerStateAdapter.getMarkerState(markerId)
        
        // Then
        assertEquals(MarkerState.PERSISTED, state)
        verify { temporaryMarkerManager.isTemporaryMarker(markerId) }
        verify { markerManager.getMarkerById(markerId) }
    }
    
    @Test
    fun `getMarkerState가 존재하지 않는 마커일 경우 DELETED 반환하는지 확인`() {
        // Given
        val markerId = "test-marker-1"
        every { temporaryMarkerManager.isTemporaryMarker(markerId) } returns false
        every { markerManager.getMarkerById(markerId) } returns null
        
        // When
        val state = markerStateAdapter.getMarkerState(markerId)
        
        // Then
        assertEquals(MarkerState.DELETED, state)
        verify { temporaryMarkerManager.isTemporaryMarker(markerId) }
        verify { markerManager.getMarkerById(markerId) }
    }
    
    @Test
    fun `makeMarkerPersisted가 TemporaryMarkerManager를 올바르게 호출하는지 확인`() {
        // Given
        val markerId = "test-marker-1"
        every { temporaryMarkerManager.isTemporaryMarker(markerId) } returns true
        every { temporaryMarkerManager.makeMarkerPermanent(markerId) } just runs
        
        // When
        markerStateAdapter.makeMarkerPersisted(markerId)
        
        // Then
        verify { temporaryMarkerManager.isTemporaryMarker(markerId) }
        verify { temporaryMarkerManager.makeMarkerPermanent(markerId) }
    }
    
    @Test
    fun `makeMarkerPersisted가 이미 영구 마커일 경우 아무 작업도 하지 않는지 확인`() {
        // Given
        val markerId = "test-marker-1"
        every { temporaryMarkerManager.isTemporaryMarker(markerId) } returns false
        
        // When
        markerStateAdapter.makeMarkerPersisted(markerId)
        
        // Then
        verify { temporaryMarkerManager.isTemporaryMarker(markerId) }
        verify(exactly = 0) { temporaryMarkerManager.makeMarkerPermanent(any()) }
    }
    
    @Test
    fun `enrichMarkerWithState가 마커에 올바른 상태를 추가하는지 확인`() {
        // Given
        val markerId = "test-marker-1"
        val marker = mockk<Marker> {
            every { id } returns markerId
            every { copy(state = any()) } returns mockk()
        }
        
        every { temporaryMarkerManager.isTemporaryMarker(markerId) } returns true
        
        // When
        markerStateAdapter.enrichMarkerWithState(marker)
        
        // Then
        verify { marker.copy(state = MarkerState.TEMPORARY) }
    }
} 