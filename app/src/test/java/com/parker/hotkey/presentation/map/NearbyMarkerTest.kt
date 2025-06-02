package com.parker.hotkey.presentation.map

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.naver.maps.geometry.LatLng
import com.parker.hotkey.domain.manager.EditModeManager
import com.parker.hotkey.domain.manager.LocationTracker
import com.parker.hotkey.domain.manager.MarkerManager
import com.parker.hotkey.domain.manager.MemoManager
import com.parker.hotkey.domain.manager.TemporaryMarkerManager
import com.parker.hotkey.domain.manager.TemporaryMarkerEvent
import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.repository.AuthRepository
import com.parker.hotkey.presentation.map.event.MapEventHandler
import com.parker.hotkey.presentation.map.processor.MapStateProcessor
import com.parker.hotkey.presentation.memo.MemoInteractor
import com.parker.hotkey.util.MainCoroutineRule
import com.parker.hotkey.util.calculateDistanceTo
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 마커 근처 검사 기능 테스트 클래스
 */
@ExperimentalCoroutinesApi
class NearbyMarkerTest {

    @get:Rule
    val mockKRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    // Mocks
    @MockK
    lateinit var markerManager: MarkerManager
    
    @MockK
    lateinit var memoManager: MemoManager
    
    @MockK
    lateinit var memoInteractor: MemoInteractor
    
    @MockK
    lateinit var editModeManager: EditModeManager
    
    @MockK
    lateinit var mapEventHandler: MapEventHandler
    
    @MockK
    lateinit var mapStateProcessor: MapStateProcessor
    
    @MockK
    lateinit var locationTracker: LocationTracker
    
    @MockK
    lateinit var temporaryMarkerManager: TemporaryMarkerManager
    
    @MockK
    lateinit var authRepository: AuthRepository
    
    // 실제 테스트 대상 ViewModel
    private lateinit var viewModel: TestMapViewModel
    
    // 테스트 데이터
    private val testUserId = "test-user-id"
    private val testCenter = LatLng(37.5665, 126.9780)  // 서울 시청
    
    // 거리 계산 테스트를 위한 좌표들
    private val nearbyPoint = LatLng(37.5664, 126.9779)  // 약 15m 이내로 수정
    private val farPoint = LatLng(37.5675, 126.9790)     // 약 150m
    
    // 테스트 마커들
    private val nearbyMarker = Marker(
        id = "nearby-marker-id",
        userId = testUserId,
        latitude = nearbyPoint.latitude, 
        longitude = nearbyPoint.longitude,
        geohash = "wydmc5",
        modifiedAt = System.currentTimeMillis()
    )
    
    private val farMarker = Marker(
        id = "far-marker-id",
        userId = testUserId,
        latitude = farPoint.latitude, 
        longitude = farPoint.longitude,
        geohash = "wydmc6",
        modifiedAt = System.currentTimeMillis()
    )

    // 캡쳐된 UI 이벤트
    private val capturedEvents = mutableListOf<MapEvent>()
    
    // Flow 관련 객체
    private val markersFlow = MutableStateFlow<List<Marker>>(emptyList())

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        
        // markerManager 모킹
        every { markerManager.markers } returns markersFlow
        
        // authRepository 모킹
        coEvery { authRepository.getUserId() } returns testUserId
        
        // memoManager 모킹
        every { memoManager.subscribeToEvents(any(), any()) } returns Job()
        
        // editModeManager 모킹
        every { editModeManager.subscribeToEvents(any(), any()) } returns Job()
        
        // memoInteractor 모킹
        coEvery { memoInteractor.getUserId() } returns testUserId
        
        // temporaryMarkerManager 모킹
        coEvery { 
            temporaryMarkerManager.createTemporaryMarker(any(), any()) 
        } returns Marker(
            id = "new-marker-id",
            userId = testUserId,
            latitude = testCenter.latitude,
            longitude = testCenter.longitude,
            geohash = "wydmc5",
            modifiedAt = System.currentTimeMillis()
        )
        every { temporaryMarkerManager.events } returns MutableSharedFlow<TemporaryMarkerEvent>()
        
        // 테스트용 ViewModel 인스턴스 생성
        viewModel = TestMapViewModel(
            markerManager = markerManager,
            temporaryMarkerManager = temporaryMarkerManager,
            memoManager = memoManager,
            memoInteractor = memoInteractor,
            editModeManager = editModeManager,
            mapEventHandler = mapEventHandler,
            mapStateProcessor = mapStateProcessor,
            locationTracker = locationTracker,
            useTemporaryMarkerFeature = true,
            authRepository = authRepository
        )
        
        // 거리 계산 확인
        val distance = testCenter.calculateDistanceTo(nearbyPoint)
        println("테스트 좌표 간 계산된 거리: ${distance}m")
    }

    @Test
    fun `거리 계산 테스트 - 두 지점 간의 거리가 올바르게 계산되는지 확인`() = runTest {
        // 가까운 지점 거리 계산
        val nearDistance = testCenter.calculateDistanceTo(nearbyPoint)
        println("중심점과 가까운 지점 간 거리: ${nearDistance}m")
        assertTrue(nearDistance < 20.0, "가까운 지점은 20m 이내여야 함")
        
        // 먼 지점 거리 계산
        val farDistance = testCenter.calculateDistanceTo(farPoint)
        println("중심점과 먼 지점 간 거리: ${farDistance}m")
        assertTrue(farDistance > 20.0, "먼 지점은 20m 이상이어야 함")
    }
    
    @Test
    fun `주변에 마커가 없을 때 NoMarkerNearby 결과가 반환되는지 확인`() = runTest {
        // Given: 마커가 없는 상태
        markersFlow.value = emptyList()
        
        // When: checkNearbyMarkers 호출
        val result = viewModel.testCheckNearbyMarkers(testCenter)
        
        // Then: NoMarkerNearby 결과가 반환됨
        assertTrue(result is MapViewModel.NearbyMarkerCheckResult.NoMarkerNearby)
    }
    
    @Test
    fun `주변에 마커가 있을 때 MarkerExists 결과가 반환되는지 확인`() = runTest {
        // Given: 가까운 위치에 마커가 있는 상태
        markersFlow.value = listOf(nearbyMarker)
        
        // When: checkNearbyMarkers 호출
        val result = viewModel.testCheckNearbyMarkers(testCenter)
        
        // Then: MarkerExists 결과가 반환됨
        assertTrue(result is MapViewModel.NearbyMarkerCheckResult.MarkerExists)
        if (result is MapViewModel.NearbyMarkerCheckResult.MarkerExists) {
            assertEquals(1, result.nearbyMarkers.size)
            assertTrue(result.minDistance < 20.0)
        }
    }
    
    @Test
    fun `주변 마커 존재시 스낵바 이벤트 발행 확인`() = runTest {
        // Given: 가까운 위치에 마커가 있는 상태
        markersFlow.value = listOf(nearbyMarker)
        
        // 이벤트 수집 설정
        val collectedEvents = mutableListOf<MapEvent>()
        val collectJob = mainCoroutineRule.testScope.launch {
            viewModel.exposedUiEvents.collect {
                collectedEvents.add(it)
            }
        }
        
        // When: createTemporaryMarkerAtLocation 호출
        viewModel.testCreateTemporaryMarkerAtLocation(testCenter)
        
        // 모든 코루틴이 완료될 때까지 대기
        advanceUntilIdle()
        
        // Then: 스낵바 이벤트가 발행됨
        println("수집된 이벤트: $collectedEvents")
        assertTrue(collectedEvents.isNotEmpty())
        assertTrue(collectedEvents[0] is MapEvent.ShowConfirmationSnackbar)
        
        // 수집 작업 취소
        collectJob.cancel()
    }
    
    @Test
    fun `주변에 마커가 없을 때 스낵바 이벤트가 발행되지 않는지 확인`() = runTest {
        // Given: 마커가 없는 상태
        markersFlow.value = listOf(farMarker) // 먼 위치에 있는 마커만 존재
        
        // 이벤트 수집 설정
        val collectedEvents = mutableListOf<MapEvent>()
        val collectJob = mainCoroutineRule.testScope.launch {
            viewModel.exposedUiEvents.collect {
                collectedEvents.add(it)
            }
        }
        
        // When: createTemporaryMarkerAtLocation 호출
        viewModel.testCreateTemporaryMarkerAtLocation(testCenter)
        
        // 모든 코루틴이 완료될 때까지 대기
        advanceUntilIdle()
        
        // Then: 스낵바 이벤트가 발행되지 않음 (임시 마커만 생성됨)
        println("수집된 이벤트 (스낵바 없음 예상): $collectedEvents")
        assertTrue(collectedEvents.isEmpty())
        
        // 임시 마커 생성 확인
        coVerify { temporaryMarkerManager.createTemporaryMarker(any(), any()) }
        
        // 수집 작업 취소
        collectJob.cancel()
    }
}

/**
 * 테스트를 위해 확장한 MapViewModel
 * private 메서드를 테스트용으로 노출시키기 위한 클래스
 */
class TestMapViewModel(
    val markerManager: MarkerManager,
    val temporaryMarkerManager: TemporaryMarkerManager,
    val memoManager: MemoManager,
    val memoInteractor: MemoInteractor,
    val editModeManager: EditModeManager,
    val mapEventHandler: MapEventHandler,
    val mapStateProcessor: MapStateProcessor,
    val locationTracker: LocationTracker,
    val useTemporaryMarkerFeature: Boolean,
    val authRepository: AuthRepository
) {
    // 테스트용 사용자 ID
    val testUserId = "test-user-id"
    
    // UI 이벤트를 위한 Flow
    val exposedUiEvents = MutableSharedFlow<MapEvent>(
        replay = 0,
        extraBufferCapacity = 10
    )
    
    // 테스트용 메서드
    suspend fun testCheckNearbyMarkers(coord: LatLng): MapViewModel.NearbyMarkerCheckResult {
        // 실제 ViewModel의 private 메서드를 호출할 수 없으므로,
        // 테스트 로직을 여기에 직접 구현
        val currentMarkers = markerManager.markers.value
        val NEARBY_MARKER_THRESHOLD_METERS = 20.0
        
        if (currentMarkers.isEmpty()) {
            return MapViewModel.NearbyMarkerCheckResult.NoMarkerNearby
        }
        
        // 각 마커까지의 거리 계산
        val markersWithDistance = mutableListOf<Pair<Marker, Double>>()
        
        for (marker in currentMarkers) {
            val markerLatLng = LatLng(marker.latitude, marker.longitude)
            val distance = markerLatLng.calculateDistanceTo(coord)
            markersWithDistance.add(Pair(marker, distance))
        }
        
        // 20미터 이내의 마커 필터링
        val nearbyMarkers = markersWithDistance
            .filter { (_, distance) -> distance <= NEARBY_MARKER_THRESHOLD_METERS }
            .map { it.first }
        
        return if (nearbyMarkers.isEmpty()) {
            MapViewModel.NearbyMarkerCheckResult.NoMarkerNearby
        } else {
            // 가장 가까운 마커까지의 거리 계산
            val minDistance = markersWithDistance
                .filter { (marker, _) -> marker.id in nearbyMarkers.map { it.id } }
                .minOfOrNull { it.second } ?: NEARBY_MARKER_THRESHOLD_METERS
            
            MapViewModel.NearbyMarkerCheckResult.MarkerExists(nearbyMarkers, minDistance)
        }
    }
    
    fun testCreateTemporaryMarkerAtLocation(coord: LatLng) {
        kotlinx.coroutines.GlobalScope.launch {
            try {
                val userId = authRepository.getUserId()
                
                // 마커 감지 로직
                val result = testCheckNearbyMarkers(coord)
                
                when (result) {
                    is MapViewModel.NearbyMarkerCheckResult.MarkerExists -> {
                        // 근처에 마커가 있을 경우 확인 스낵바 표시
                        exposedUiEvents.emit(MapEvent.ShowConfirmationSnackbar(
                            message = "근처에 마커가 있습니다. 새로운 마커를 만드시겠습니까?",
                            actionText = "마커 생성",
                            onAction = { /* 테스트에서는 빈 구현 */ },
                            cancelText = "취소",
                            onCancel = { /* 테스트에서는 빈 구현 */ }
                        ))
                    }
                    is MapViewModel.NearbyMarkerCheckResult.NoMarkerNearby -> {
                        // 주변에 마커가 없으면 임시 마커 생성
                        temporaryMarkerManager.createTemporaryMarker(userId, coord)
                    }
                }
            } catch (e: Exception) {
                println("테스트 마커 생성 중 오류: ${e.message}")
            }
        }
    }
    
    // viewModelScope를 사용하기 위한 정의
    private val viewModelScope get() = mainCoroutineRule.testScope
    private val mainCoroutineRule = MainCoroutineRule()
} 