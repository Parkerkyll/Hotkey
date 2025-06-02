package com.parker.hotkey.domain.manager

import com.naver.maps.geometry.LatLng
import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.model.LastSync
import com.parker.hotkey.domain.repository.MarkerRepository
import com.parker.hotkey.domain.usecase.marker.CreateMarkerUseCase
import com.parker.hotkey.domain.usecase.marker.DeleteMarkerWithValidationUseCase
import com.parker.hotkey.domain.manager.impl.MarkerManagerImpl
import com.parker.hotkey.domain.repository.MarkerQueryOptions
import com.parker.hotkey.domain.manager.MemoManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class MarkerManagerTest {
    
    // 테스트 대상
    private lateinit var markerManager: MarkerManagerImpl
    
    // 모의 객체 (Mocks)
    private lateinit var markerRepository: MarkerRepository
    private lateinit var createMarkerUseCase: CreateMarkerUseCase
    private lateinit var deleteMarkerWithValidationUseCase: DeleteMarkerWithValidationUseCase
    private lateinit var memoManager: MemoManager
    
    // 테스트 데이터
    private val testUserId = "test_user"
    private val testLocation = LatLng(37.5, 127.0)
    private val testMarker = Marker(
        id = "marker1",
        userId = testUserId,
        latitude = testLocation.latitude,
        longitude = testLocation.longitude,
        modifiedAt = System.currentTimeMillis(),
        lastSync = LastSync.createInitial(),
        geohash = "test_geohash"
    )
    private val testMarkerId = testMarker.id
    
    @Before
    fun setup() {
        // 모의 객체 설정
        markerRepository = mockk(relaxed = true)
        createMarkerUseCase = mockk(relaxed = true)
        deleteMarkerWithValidationUseCase = mockk(relaxed = true)
        memoManager = mockk(relaxed = true)
        
        // 기본 동작 설정
        coEvery { createMarkerUseCase.invoke(any(), any(), any()) } returns Result.success(testMarker)
        coEvery { deleteMarkerWithValidationUseCase.invoke(any()) } returns Result.success(Unit)
        every { markerRepository.getMarkersByGeohash(any(), any()) } returns flow { emit(listOf(testMarker)) }
        every { markerRepository.getMarkers(any(), any()) } returns flow { emit(listOf(testMarker)) }
        every { markerRepository.getMarkers(any(), any(), any()) } returns flow { emit(listOf(testMarker)) }
        
        // 테스트 대상 생성
        val testDispatcher = UnconfinedTestDispatcher()
        markerManager = MarkerManagerImpl(
            markerRepository = markerRepository,
            createMarkerUseCase = createMarkerUseCase,
            deleteMarkerWithValidationUseCase = deleteMarkerWithValidationUseCase,
            memoManager = memoManager,
            coroutineScope = CoroutineScope(testDispatcher)
        )
    }
    
    @Test
    fun `초기화 시 initialized 상태가 true로 변경됨`() = runTest {
        // given
        var initialized = false
        val job = launch { markerManager.initialized.collect { initialized = it } }
        
        // when
        markerManager.initialize()
        advanceUntilIdle()
        
        // then
        assertTrue(initialized)
        job.cancel()
    }
    
    @Test
    fun `마커 선택시 selectedMarkerId 업데이트됨`() = runTest {
        // given
        var selectedId: String? = null
        val job = launch { markerManager.selectedMarkerId.collect { selectedId = it } }
        
        // when
        markerManager.selectMarker(testMarkerId)
        advanceUntilIdle()
        
        // then
        assertEquals(testMarkerId, selectedId)
        job.cancel()
    }
    
    @Test
    fun `마커 선택 해제시 selectedMarkerId null로 설정됨`() = runTest {
        // given
        markerManager.selectMarker(testMarkerId)
        advanceUntilIdle()
        
        // when
        markerManager.clearSelectedMarker()
        advanceUntilIdle()
        
        // then
        assertNull(markerManager.selectedMarkerId.value)
    }
    
    @Test
    fun `마커 생성 성공시 마커 목록에 추가됨`() = runTest {
        // given
        var markers: List<Marker> = emptyList()
        val job = launch { markerManager.markers.collect { markers = it } }
        
        // when
        val result = markerManager.createMarker(testUserId, testLocation)
        advanceUntilIdle()
        
        // then
        assertTrue(result.isSuccess)
        assertEquals(1, markers.size)
        assertEquals(testMarker.id, markers[0].id)
        
        // verify
        coVerify { createMarkerUseCase.invoke(testUserId, testLocation.latitude, testLocation.longitude) }
        job.cancel()
    }
    
    @Test
    fun `마커 삭제 성공시 마커 목록에서 제거됨`() = runTest {
        // given
        // 먼저 마커 생성
        markerManager.createMarker(testUserId, testLocation)
        advanceUntilIdle()
        
        // 해당 마커를 선택
        markerManager.selectMarker(testMarker.id)
        advanceUntilIdle()
        
        var markers: List<Marker> = emptyList()
        val job = launch { markerManager.markers.collect { markers = it } }
        
        // when
        val result = markerManager.deleteMarker(testMarker.id)
        advanceUntilIdle()
        
        // then
        assertTrue(result.isSuccess)
        assertTrue(markers.isEmpty())
        assertNull(markerManager.selectedMarkerId.value)
        
        // verify
        coVerify { deleteMarkerWithValidationUseCase.invoke(testMarker.id) }
        job.cancel()
    }
    
    @Test
    fun `마커 영역 로드시 마커 목록 업데이트됨`() = runTest {
        // given
        var markers: List<Marker> = emptyList()
        val job = launch { markerManager.markers.collect { markers = it } }
        
        // when
        markerManager.loadMarkersInArea("test_geohash", listOf("neighbor1", "neighbor2"))
        advanceUntilIdle()
        
        // then
        assertEquals(1, markers.size)
        assertEquals(testMarker.id, markers[0].id)
        
        // verify - Deprecated 메소드 대신 새로운 메소드 사용
        verify { markerRepository.getMarkers("test_geohash", listOf("neighbor1", "neighbor2")) }
        job.cancel()
    }
    
    @Test
    fun `loadMarkersInAreaOptimized 호출시 제한이 줌 레벨에 따라 설정됨 (정밀도 6)`() = runTest {
        // given
        val testGeohash = "test_geohash"
        val testNeighbors = listOf("neighbor1", "neighbor2")
        val midZoom = 15.0
        
        // when
        markerManager.loadMarkersInAreaOptimized(testGeohash, testNeighbors, midZoom)
        advanceUntilIdle()
        
        // then
        // 줌 레벨 15.0은 정밀도 6과 마커 500개 제한에 해당
        verify { markerRepository.getMarkers(testGeohash, testNeighbors, MarkerQueryOptions(precision = 6, limit = 500)) }
    }
    
    @Test
    fun `loadMarkersInAreaOptimized 호출시 높은 줌 레벨에서 제한이 증가함`() = runTest {
        // given
        val testGeohash = "test_geohash"
        val testNeighbors = listOf("neighbor1", "neighbor2")
        val highZoom = 18.0
        
        // when
        markerManager.loadMarkersInAreaOptimized(testGeohash, testNeighbors, highZoom)
        advanceUntilIdle()
        
        // then
        // 줌 레벨 18.0은 정밀도 6과 마커 1000개 제한에 해당
        verify { markerRepository.getMarkers(testGeohash, testNeighbors, MarkerQueryOptions(precision = 6, limit = 1000)) }
    }
    
    @Test
    fun `loadMarkersInAreaGeohash6Optimized를 대체하는 테스트`() = runTest {
        // given
        val testGeohash = "test_geohash"
        val testNeighbors = listOf("neighbor1", "neighbor2")
        val midZoom = 15.0
        
        // when
        markerManager.loadMarkersInAreaOptimized(testGeohash, testNeighbors, midZoom)
        advanceUntilIdle()
        
        // then
        verify { markerRepository.getMarkers(testGeohash, testNeighbors, MarkerQueryOptions(precision = 6, limit = 500)) }
    }
    
    @Test
    fun `마커 ID로 조회 성공시 마커 반환`() = runTest {
        // given
        // 먼저 마커 생성
        markerManager.createMarker(testUserId, testLocation)
        advanceUntilIdle()
        
        // when
        val result = markerManager.getMarkerById(testMarker.id)
        
        // then
        assertNotNull(result)
        assertEquals(testMarker.id, result?.id)
    }
    
    @Test
    fun `이벤트 구독 설정시 이벤트 수신됨`() = runTest {
        // given
        val receivedEvents = mutableListOf<MarkerEvent>()
        
        // when
        val job = markerManager.subscribeToEvents(this) { event ->
            receivedEvents.add(event)
        }
        markerManager.selectMarker(testMarkerId)
        advanceUntilIdle()
        
        // then
        assertEquals(1, receivedEvents.size)
        assertTrue(receivedEvents[0] is MarkerEvent.MarkerSelected)
        assertEquals(testMarkerId, (receivedEvents[0] as MarkerEvent.MarkerSelected).markerId)
        job.cancel()
    }
    
    @Test
    fun `마커 목록 업데이트 성공시 상태 업데이트됨`() = runTest {
        // given
        val newMarkers = listOf(
            testMarker,
            testMarker.copy(id = "marker2", latitude = 38.0, longitude = 128.0)
        )
        
        // when
        markerManager.updateMarkers(newMarkers)
        advanceUntilIdle()
        
        // then
        assertEquals(2, markerManager.markers.value.size)
        assertEquals(newMarkers, markerManager.markers.value)
    }
    
    @Test
    fun `forceRemoveMarkerFromList 호출시 마커 목록에서 제거됨`() = runTest {
        // given
        // 먼저 마커 생성 및 선택
        markerManager.createMarker(testUserId, testLocation)
        markerManager.selectMarker(testMarker.id)
        advanceUntilIdle()
        
        // when
        markerManager.forceRemoveMarkerFromList(testMarker.id)
        advanceUntilIdle()
        
        // then
        assertTrue(markerManager.markers.value.isEmpty())
    }
} 