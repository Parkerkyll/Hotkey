package com.parker.hotkey.presentation.map.markers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naver.maps.geometry.LatLng
import com.parker.hotkey.domain.manager.MarkerEvent
import com.parker.hotkey.domain.manager.MarkerManager
import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.model.state.MarkerState
import com.parker.hotkey.domain.repository.MarkerRepository
import com.parker.hotkey.domain.repository.SyncRepository
import com.parker.hotkey.domain.usecase.marker.CreateMarkerUseCase
import com.parker.hotkey.domain.usecase.marker.DeleteMarkerWithValidationUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject

/**
 * 마커 로딩 상태를 표현하는 sealed 클래스
 */
sealed class MarkerLoadingState {
    object Idle : MarkerLoadingState()
    object Loading : MarkerLoadingState()
    data class Success(val markers: List<Marker>) : MarkerLoadingState()
    data class Error(val message: String) : MarkerLoadingState()
}

/**
 * 마커 관련 기능을 담당하는 ViewModel
 * 마커의 상태 관리, 이벤트 처리, 비즈니스 로직을 처리합니다.
 */
class MarkerViewModel @Inject constructor(
    private val markerRepository: MarkerRepository,
    private val syncRepository: SyncRepository,
    private val markerManager: MarkerManager,
    private val markerInteractor: MarkerInteractor,
    private val markerEventHandler: MarkerEventHandler,
    private val markerStateProcessor: MarkerStateProcessor,
    private val createMarkerUseCase: CreateMarkerUseCase,
    private val deleteMarkerWithValidationUseCase: DeleteMarkerWithValidationUseCase
) : ViewModel() {
    
    // 상수 추가
    companion object {
        private const val CAMERA_IDLE_DEBOUNCE_KEY = "camera_idle_marker_update"
        private const val CAMERA_IDLE_DEBOUNCE_MS = 500L // 500ms
    }
    
    // 기존 상태 관리 - 도메인 모델의 MarkerState 사용
    private val _state = MutableStateFlow(MarkerState())
    val state: StateFlow<MarkerState> = _state.asStateFlow()
    
    // 마커를 삭제 중인지 추적하는 별도 상태
    private val _markerToDeleteId = MutableStateFlow<String?>(null)
    val markerToDeleteId: StateFlow<String?> = _markerToDeleteId.asStateFlow()
    
    // 새로운 마커 로딩 상태 관리
    private val _markerLoadingState = MutableStateFlow<MarkerLoadingState>(MarkerLoadingState.Idle)
    val markerLoadingState: StateFlow<MarkerLoadingState> = _markerLoadingState.asStateFlow()
    
    // 작업 관리를 위한 Job 변수 추가
    private var currentSyncJob: Job? = null
    private var currentLoadingJob: Job? = null
    
    init {
        Timber.d("MarkerViewModel 초기화")
        setupMarkerSubscription()
        markerManager.initialize()
    }
    
    /**
     * 마커 이벤트 구독 설정
     */
    private fun setupMarkerSubscription() {
        // 마커 상태 구독
        viewModelScope.launch {
            markerStateProcessor.processMarkerState().collect { markerState ->
                _state.value = markerState
            }
        }
        
        // 마커 이벤트 구독
        markerManager.subscribeToEvents(viewModelScope) { event ->
            markerEventHandler.handleMarkerEvent(event)
        }
    }
    
    /**
     * 마커 선택
     */
    fun selectMarker(markerId: String) {
        markerManager.selectMarker(markerId)
    }
    
    /**
     * 마커 선택 해제
     */
    fun clearSelection() {
        markerManager.clearSelectedMarker()
    }
    
    /**
     * 마커 삭제
     */
    fun deleteMarker(markerId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            _markerToDeleteId.value = markerId
            
            try {
                val result = deleteMarkerWithValidationUseCase(markerId)
                result.onSuccess {
                    _state.update { it.copy(isLoading = false) }
                    _markerToDeleteId.value = null
                }.onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.message) }
                    _markerToDeleteId.value = null
                    Timber.e(error, "마커 삭제 실패: $markerId")
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
                _markerToDeleteId.value = null
                Timber.e(e, "마커 삭제 중 예외 발생: $markerId")
            }
        }
    }
    
    /**
     * 특정 위치에 마커 생성
     */
    fun createMarkerAt(userId: String, coord: LatLng) {
        viewModelScope.launch {
            // 로딩 상태 업데이트
            _markerLoadingState.value = MarkerLoadingState.Loading
            // Timber.d("마커 생성 시작 - 로딩 상태: true")

            try {
                // createMarker 대신 createMarkerAndOpenMemo 호출하도록 변경
                val result = markerInteractor.createMarkerAndOpenMemo(userId, coord)
                
                result.onSuccess {
                    // 성공 로그는 MarkerManager 또는 MarkerInteractor에서 처리
                    // Timber.d("마커 생성 및 메모 열기 성공: ${it.id}")
                }.onFailure {
                    Timber.e(it, "마커 생성 및 메모 열기 실패")
                    // 에러 상태 업데이트 (필요시)
                    _state.update { currentState -> 
                        currentState.copy(error = it.message ?: "알 수 없는 오류") 
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "마커 생성 중 예외 발생")
                // 에러 상태 업데이트 (필요시)
                _state.update { currentState -> 
                    currentState.copy(error = e.message ?: "알 수 없는 오류") 
                }
            } finally {
                _markerLoadingState.value = MarkerLoadingState.Error(state.value.error ?: "알 수 없는 오류")
                // Timber.d("마커 생성 완료 - 로딩 상태: false")
            }
        }
    }
    
    /**
     * 특정 영역의 마커 로드 - 상태 관리 개선 버전
     */
    fun loadMarkersInArea(geohash: String, neighbors: List<String>, zoomLevel: Double) {
        // 이전 작업 취소
        cancelPreviousJobs()
        
        currentLoadingJob = viewModelScope.launch {
            try {
                // 로딩 상태로 변경
                _markerLoadingState.value = MarkerLoadingState.Loading
                Timber.d("마커 로딩 시작: geohash=$geohash, neighbors=${neighbors.size}개")
                
                // 비동기 마커 로딩 실행
                try {
                    markerManager.loadMarkersInAreaOptimized(geohash, neighbors, zoomLevel)
                    
                    // 마커 로드 결과 수집
                    var isFirstUpdate = true // 첫 번째 업데이트인지 추적하는 플래그
                    markerManager.markers.collect { loadedMarkers ->
                        // 첫 번째 업데이트만 처리
                        if (isFirstUpdate) {
                            isFirstUpdate = false
                            Timber.d("마커 로딩 완료: ${loadedMarkers.size}개 마커")
                            
                            // 성공 상태로 변경
                            _markerLoadingState.value = MarkerLoadingState.Success(loadedMarkers)
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        Timber.d("마커 로딩 작업이 취소됨: ${e.message}")
                    } else {
                        Timber.e(e, "마커 로딩 중 오류 발생")
                        _markerLoadingState.value = MarkerLoadingState.Error(e.message ?: "마커 로딩 실패")
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    Timber.d("마커 로딩 작업이 취소됨: ${e.message}")
                } else {
                    Timber.e(e, "마커 로딩 중 예외 발생")
                    _markerLoadingState.value = MarkerLoadingState.Error(e.message ?: "알 수 없는 오류 발생")
                    _state.update { it.copy(error = e.message) }
                }
            }
        }
    }
    
    /**
     * 이전 작업을 취소합니다.
     */
    private fun cancelPreviousJobs() {
        currentSyncJob?.cancel()
        currentLoadingJob?.cancel()
        currentSyncJob = null
        currentLoadingJob = null
        Timber.d("이전 마커 작업 취소됨")
    }
    
    /**
     * 데이터 동기화와 마커 로딩을 함께 수행합니다.
     */
    fun syncDataAndLoadMarkers(geohash: String, neighbors: List<String>?, zoomLevel: Double) {
        // 이전 작업 취소
        cancelPreviousJobs()
        
        currentSyncJob = viewModelScope.launch {
            try {
                Timber.d("데이터 동기화 및 마커 로딩 시작: geohash=$geohash")
                
                // 로딩 상태로 변경
                _state.update { it.copy(isLoading = true) }
                
                // 안전한 이웃 geohash 목록 처리
                val safeNeighbors = neighbors ?: emptyList()
                
                // 실제 geohash 길이 확인
                val geohashLength = geohash.length
                Timber.d("사용하는 geohash 길이: ${geohashLength}자리")
                
                // 정밀도 조정 - geohash 길이에 맞추기
                val adjustedZoomLevel = when {
                    geohashLength <= 5 -> 13.0 // 정밀도 5 이하면 낮은 줌 레벨
                    geohashLength == 6 -> 15.0 // 정밀도 6이면 중간 줌 레벨
                    else -> zoomLevel // 그대로 사용
                }
                
                // 개선된 마커 로딩 사용
                markerManager.loadMarkersInAreaOptimized(geohash, safeNeighbors, adjustedZoomLevel)
                
                // 마커가 로드될 때까지 잠시 대기
                delay(300)
                
                // 로드 상태 확인
                val loadedMarkers = markerManager.markers.first()
                Timber.d("마커 로딩 결과: ${loadedMarkers.size}개 마커")
                
                // 0개가 로드되었다면 다른 정밀도로 다시 시도
                if (loadedMarkers.isEmpty()) {
                    Timber.d("마커 0개 로드됨, 정밀도 6으로 다시 시도")
                    markerManager.loadMarkersInAreaOptimized(geohash, safeNeighbors, 15.0) // 정밀도 6에 해당하는 줌 레벨
                }
                
                // 성공 상태로 변경
                _state.update { it.copy(isLoading = false) }
                
            } catch (e: CancellationException) {
                // 작업 취소 예외는 정상적인 흐름이므로 에러로 처리하지 않음
                Timber.d("마커 로딩 작업이 취소되었습니다")
                _state.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "데이터 동기화 및 마커 로딩 중 예외 발생")
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
    
    /**
     * 리소스 해제
     */
    fun cleanup() {
        // 선택된 마커 초기화
        markerManager.clearSelectedMarker()
    }
    
    /**
     * 마커 풀 상태를 로깅합니다.
     */
    fun logMarkerPoolStatus() {
        // 직접 로깅만 출력
        Timber.d("마커 풀 상태 로깅 요청됨 (MarkerViewModel)")
    }
    
    /**
     * 마커를 새로고침합니다.
     * 화면 전환 후 깜박임을 방지하기 위해 사용됩니다.
     */
    fun refreshMarkers() {
        viewModelScope.launch {
            try {
                Timber.d("마커 새로고침 시작")
                
                // 현재 상태 복제 (객체 참조 변경으로 새로고침 효과)
                val currentState = _state.value
                val currentMarkers = currentState.markers.toList()
                
                if (currentMarkers.isEmpty()) {
                    Timber.d("새로고침할 마커가 없습니다")
                    return@launch
                }
                
                // 즉시 동일한 마커로 업데이트하여 깜박임 방지 (지연 없이)
                _state.update { 
                    it.copy(
                        markers = currentMarkers,
                        isLoading = false
                    )
                }
                
                // 마커 매니저에 직접 UI 갱신 요청
                markerManager.refreshMarkersUI()
                
                Timber.d("마커 새로고침 완료: ${currentMarkers.size}개 마커")
            } catch (e: CancellationException) {
                // 작업 취소 예외는 정상적인 흐름이므로 에러로 처리하지 않음
                Timber.d("마커 새로고침 작업이 취소되었습니다")
                _state.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "마커 새로고침 중 오류 발생: ${e.message}")
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
    
    /**
     * 마커 가시성을 업데이트합니다.
     */
    fun updateMarkerVisibility(currentGeohash: String, neighbors: List<String>, isEditMode: Boolean) {
        // 상태를 통해 간접적으로 처리
        Timber.d("마커 가시성 업데이트 요청: geohash=$currentGeohash, neighbors=${neighbors.size}개, isEditMode=$isEditMode")
    }
    
    /**
     * 디바운싱된 카메라 이벤트 처리
     * 카메라 이동이 끝난 후 일정 시간 지연 후 한 번만 마커 로딩
     */
    fun onCameraIdleDebounced(geohash: String, neighbors: List<String>?, zoomLevel: Double) {
        // 이전 작업 취소
        cancelPreviousJobs()
        
        currentLoadingJob = viewModelScope.launch {
            try {
                Timber.d("디바운싱된 카메라 IDLE 이벤트 - 지연 시작")
                delay(CAMERA_IDLE_DEBOUNCE_MS) // 디바운싱 지연
                
                Timber.d("디바운싱된 카메라 IDLE 이벤트로 마커 로딩 실행")
                syncDataAndLoadMarkers(geohash, neighbors, zoomLevel)
            } catch (e: CancellationException) {
                Timber.d("디바운싱된 카메라 IDLE 이벤트 취소됨")
            }
        }
    }

    /**
     * 앱이 일시 정지될 때 호출 (onPause)
     * 진행 중인 작업을 취소하여 CPU 사용 최소화
     */
    fun onLifecyclePause() {
        Timber.d("MarkerViewModel: Lifecycle onPause - 작업 취소")
        cancelPreviousJobs() // 진행 중인 로딩, 동기화 작업 취소
    }

    /**
     * 앱이 재개될 때 호출 (onResume)
     * 현재 카메라 위치에 맞는 데이터 로드
     */
    @Deprecated("Use handleForegroundTransition instead", ReplaceWith("handleForegroundTransition(currentGeohash, neighbors, zoomLevel)"))
    fun onLifecycleResume(currentGeohash: String, neighbors: List<String>?, zoomLevel: Double) {
        Timber.d("MarkerViewModel: Lifecycle onResume - 데이터 로드 시작")
        // 앱이 다시 활성화될 때 현재 카메라 위치 기준으로 데이터 로드
        viewModelScope.launch {
            try {
                // 약간의 지연 후 로드 (UI 초기화 완료 보장)
                delay(100) 
                syncDataAndLoadMarkers(currentGeohash, neighbors, zoomLevel)
            } catch (e: Exception) {
                Timber.e(e, "onLifecycleResume 중 오류 발생")
            }
        }
    }
    
    /**
     * 포그라운드 전환 처리 - 데이터 로드 최적화 버전
     */
    fun handleForegroundTransition(currentGeohash: String, neighbors: List<String>?, zoomLevel: Double) {
        Timber.d("MarkerViewModel: 포그라운드 전환 처리 - 데이터 로드 시작")
        // 기존 작업 취소
        cancelPreviousJobs()
        
        // 새 작업 시작
        viewModelScope.launch {
            try {
                syncDataAndLoadMarkers(currentGeohash, neighbors, zoomLevel)
            } catch (e: Exception) {
                Timber.e(e, "handleForegroundTransition 중 오류 발생")
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        cancelPreviousJobs()
        cleanup()
    }
} 