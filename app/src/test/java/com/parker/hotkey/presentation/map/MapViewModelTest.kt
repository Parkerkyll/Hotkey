package com.parker.hotkey.presentation.map

import com.naver.maps.geometry.LatLng
import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.repository.MarkerRepository
import com.parker.hotkey.util.GeohashUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    
    private lateinit var viewModel: MapViewModel
    private lateinit var markerRepository: MarkerRepository
    private val testDispatcher = StandardTestDispatcher()
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        markerRepository = mock()
        viewModel = MapViewModel(markerRepository)
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `카메라 위치 변경 시 상태가 업데이트되어야 한다`() = runTest {
        // Given
        val testPosition = LatLng(37.5666102, 126.9783881)
        val testZoom = 16.0
        
        // When
        viewModel.onCameraPositionChanged(testPosition, testZoom)
        
        // Then
        val state = viewModel.mapState.first()
        assertEquals(testPosition, state.cameraPosition)
        assertEquals(testZoom, state.zoomLevel)
    }
    
    @Test
    fun `마커 생성 시 geohash가 정상적으로 생성되어야 한다`() = runTest {
        // Given
        val testPosition = LatLng(37.5666102, 126.9783881)
        val testTitle = "테스트 마커"
        val testDescription = "테스트 설명"
        val testUserId = "test_user"
        val expectedGeohash = GeohashUtil.encode(testPosition)
        
        whenever(markerRepository.createMarker(any())).thenReturn(
            Result.success(
                Marker(
                    id = "test_id",
                    position = testPosition,
                    title = testTitle,
                    description = testDescription,
                    geohash = expectedGeohash,
                    userId = testUserId
                )
            )
        )
        
        // When
        viewModel.createMarker(testPosition, testTitle, testDescription, testUserId)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        verify(markerRepository).createMarker(any())
    }
    
    @Test
    fun `마커 로드 시 보이는 영역의 geohash들이 정상적으로 계산되어야 한다`() = runTest {
        // Given
        val testPosition = LatLng(37.5666102, 126.9783881)
        val testZoom = 16.0
        val testMarker = Marker(
            id = "test_id",
            position = testPosition,
            title = "테스트 마커",
            description = "테스트 설명",
            geohash = GeohashUtil.encode(testPosition),
            userId = "test_user"
        )
        
        whenever(markerRepository.getMarkersByGeohash(any())).thenReturn(
            Result.success(listOf(testMarker))
        )
        
        // When
        viewModel.onCameraPositionChanged(testPosition, testZoom)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        val state = viewModel.mapState.first()
        assertNotNull(state.markers)
        assert(state.markers.isNotEmpty())
    }
} 