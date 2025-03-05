package com.parker.hotkey.presentation.map

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naver.maps.geometry.LatLng
import com.parker.hotkey.domain.manager.EditModeEvent
import com.parker.hotkey.domain.manager.EditModeManager
import com.parker.hotkey.domain.manager.EditModeState
import com.parker.hotkey.domain.manager.LocationTracker
import com.parker.hotkey.domain.manager.MarkerEvent
import com.parker.hotkey.domain.manager.MarkerManager
import com.parker.hotkey.domain.manager.MemoEvent
import com.parker.hotkey.domain.manager.MemoManager
import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.model.Memo
import com.parker.hotkey.domain.repository.AuthRepository
import com.parker.hotkey.domain.repository.LocationManager
import com.parker.hotkey.domain.repository.MarkerRepository
import com.parker.hotkey.domain.repository.MemoRepository
import com.parker.hotkey.domain.usecase.marker.CreateMarkerUseCase
import com.parker.hotkey.domain.usecase.marker.DeleteMarkerWithValidationUseCase
import com.parker.hotkey.domain.usecase.memo.CreateMemoUseCase
import com.parker.hotkey.domain.usecase.memo.DeleteMemoUseCase
import com.parker.hotkey.domain.usecase.memo.GetMemosByMarkerIdUseCase
import com.parker.hotkey.presentation.map.event.MapEventHandler
import com.parker.hotkey.presentation.map.processor.MapStateProcessor
import com.parker.hotkey.presentation.state.MapState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val markerRepository: MarkerRepository,
    private val memoRepository: MemoRepository,
    private val createMarkerUseCase: CreateMarkerUseCase,
    private val deleteMarkerWithValidationUseCase: DeleteMarkerWithValidationUseCase,
    val authRepository: AuthRepository,
    val editModeManager: EditModeManager,
    @ApplicationContext private val context: Context,
    private val locationManager: LocationManager,
    private val locationTracker: LocationTracker,
    private val createMemoUseCase: CreateMemoUseCase,
    private val deleteMemoUseCase: DeleteMemoUseCase,
    private val getMemosByMarkerIdUseCase: GetMemosByMarkerIdUseCase,
    val markerManager: MarkerManager,
    private val memoManager: MemoManager,
    private val mapStateProcessor: MapStateProcessor,
    private val mapEventHandler: MapEventHandler
) : ViewModel() {

    // 에러 상태 Flow
    private val _errorState = MutableStateFlow<MapError?>(null)
    
    // 상태 통합을 위한 MapState Flow
    private val _mapState = MutableStateFlow<MapState>(MapState.Initial)
    val mapState: StateFlow<MapState> = _mapState.asStateFlow()
    
    // mapState에서 파생된 로딩 상태
    val shouldShowLoading = mapState.map { it is MapState.Loading }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    
    // 메모장 표시 관련 상태
    private data class MemoDialogState(
        val shouldShow: Boolean = false,
        val markerId: String? = null
    )
    
    private val _memoDialogState = MutableStateFlow(MemoDialogState())
    val shouldShowMemoDialog: StateFlow<Boolean> = _memoDialogState.map { it.shouldShow }.distinctUntilChanged().stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        false
    ).also { flow ->
        // 메모장 표시 상태 변화 추적을 위한 로깅 설정
        viewModelScope.launch {
            flow.collect { shouldShow ->
                Timber.d("메모장 표시 상태 변경: $shouldShow, 마커 ID: ${_memoDialogState.value.markerId}")
            }
        }
    }

    // 메모장에 표시할 마커 ID
    val selectedMemoMarkerId: StateFlow<String?> = _memoDialogState.map { it.markerId }.distinctUntilChanged().stateIn(
        viewModelScope, 
        SharingStarted.Eagerly,
        null
    )

    // 마커 삭제 상태를 담을 필드 추가
    private val _markerToDeleteId = MutableStateFlow<String?>(null)

    init {
        initializeManagers()
        setupStateCollectors()
        setupEventSubscriptions()
        Timber.d("MapViewModel 초기화 완료")
    }

    /**
     * 모든 매니저를 명시적인 순서로 초기화합니다.
     */
    private fun initializeManagers() {
        try {
            Timber.d("매니저 초기화 시작")
            
            // 1. 편집 모드 매니저 초기화
            editModeManager.initialize()
            Timber.d("편집 모드 매니저 초기화 완료")
            
            // 2. 위치 추적 매니저 초기화
            initializeLocationTracking()
            Timber.d("위치 추적 매니저 초기화 완료")
            
            // 3. 마커 매니저 초기화
            markerManager.initialize()
            Timber.d("마커 매니저 초기화 완료")
            
            // 4. 메모 매니저 초기화
            memoManager.initialize()
            Timber.d("메모 매니저 초기화 완료")
            
            Timber.d("모든 매니저 초기화 완료")
        } catch (e: Exception) {
            Timber.e(e, "매니저 초기화 중 오류 발생")
            handleError(e, "매니저 초기화 중 오류가 발생했습니다.")
        }
    }

    /**
     * 위치 추적 초기화 - LocationTracker를 통해 위치 추적을 설정합니다.
     */
    private fun initializeLocationTracking() {
        viewModelScope.launch {
            try {
                // 위치 권한 확인
                val permissionGranted = locationManager.hasLocationPermission()
                
                if (permissionGranted) {
                    Timber.d("위치 추적 설정: 권한 승인됨, 위치 추적 시작")
                    
                    // LocationTracker 초기화
                    locationTracker.initialize()
                    Timber.d("LocationTracker 초기화 완료")
                    
                    // 마커 추적 설정 - 개선된 방식 사용
                    locationTracker.setupMarkerTracking(markerManager)
                    Timber.d("마커 추적 설정 완료")
                } else {
                    Timber.w("위치 추적 설정: 권한 없음, 위치 추적 시작 불가")
                    // 권한 없을 때는 에러 표시하지 않고 위치 추적만 비활성화
                }
            } catch (e: Exception) {
                Timber.e(e, "위치 추적 설정 중 예외 발생")
                handleError(e, "위치 추적 설정 중 오류가 발생했습니다.")
            }
        }
    }

    /**
     * 모든 이벤트 구독을 명시적인 순서로 설정합니다.
     */
    private fun setupEventSubscriptions() {
        Timber.d("이벤트 구독 설정 시작")
        
        // 1. 마커 이벤트 구독
        subscribeToMarkerEvents()
        
        // 2. 메모 이벤트 구독
        subscribeToMemoEvents()
        
        // 3. 편집 모드 이벤트 구독
        subscribeToEditModeEvents()
        
        // 4. 위치 이벤트 구독
        subscribeToLocationEvents()
        
        Timber.d("이벤트 구독 설정 완료")
    }
    
    /**
     * 마커 이벤트 구독
     */
    private fun subscribeToMarkerEvents() {
        markerManager.subscribeToEvents(viewModelScope) { event ->
            try {
                Timber.d("마커 이벤트 수신: $event")
                when (event) {
                    is MarkerEvent.MarkerCreationSuccess -> {
                        try {
                            val markerId = event.marker.id
                            Timber.d("마커 생성 이벤트: $markerId")
                            // 이벤트 기반으로 즉시 메모장 표시 
                            Timber.d("마커 생성 이벤트로 인한 메모장 표시 요청: $markerId")
                            showMemoDialogForMarker(markerId)
                        } catch (e: Exception) {
                            Timber.e(e, "마커 생성 이벤트 처리 중 오류 발생")
                        }
                    }
                    is MarkerEvent.MarkerDeleted -> {
                        val markerId = event.markerId
                        Timber.d("마커 삭제 성공 이벤트 수신: $markerId")
                        // UI 상태 업데이트 (이미 상태 구독에서 자동으로 처리됨)
                    }
                    is MarkerEvent.MarkerSelected -> {
                        Timber.d("마커 선택 이벤트 수신: ${event.markerId}")
                        // 메모 로드 - 필요 시 수행
                        loadMemos(event.markerId)
                    }
                    is MarkerEvent.MarkerSelectionCleared -> {
                        Timber.d("마커 선택 해제 이벤트 수신")
                        // 메모장 상태 초기화 - 필요 시 수행
                        resetMemoDialogState()
                    }
                    is MarkerEvent.MarkerCreated -> {
                        Timber.d("마커 생성 이벤트 수신: ${event.marker.id}")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "마커 이벤트 처리 중 오류 발생")
                handleError(e, "마커 이벤트 처리 중 오류가 발생했습니다.")
            }
        }
    }
    
    /**
     * 메모 이벤트 구독
     */
    private fun subscribeToMemoEvents() {
        memoManager.subscribeToEvents(viewModelScope) { event ->
            try {
                Timber.d("메모 이벤트 수신: $event")
                when (event) {
                    is MemoEvent.MemosLoaded -> {
                        Timber.d("메모 로드 성공 이벤트: ${event.markerId}, ${event.memos.size}개")
                    }
                    is MemoEvent.MemoCreated -> {
                        Timber.d("메모 생성 성공 이벤트: ${event.memo.id}")
                    }
                    is MemoEvent.MemoDeleted -> {
                        Timber.d("메모 삭제 성공 이벤트: ${event.memoId}")
                    }
                    is MemoEvent.Error -> {
                        val errorMessage = "메모 관련 오류 이벤트: ${event.message}"
                        val exception = Exception(event.message)
                        Timber.e(exception as Throwable, errorMessage)
                        handleError(exception, event.message)
                    }
                    is MemoEvent.ClearedSelection -> {
                        Timber.d("메모 선택 초기화 이벤트")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "메모 이벤트 처리 중 오류 발생")
                handleError(e, "메모 이벤트 처리 중 오류가 발생했습니다.")
            }
        }
    }
    
    /**
     * 편집 모드 이벤트 구독
     */
    private fun subscribeToEditModeEvents() {
        editModeManager.subscribeToEvents(viewModelScope) { event ->
            try {
                Timber.d("편집 모드 이벤트 수신: $event")
                when (event) {
                    is EditModeEvent.ModeChanged -> {
                        Timber.d("편집 모드 변경 이벤트: ${if (event.isEditMode) "쓰기모드" else "읽기모드"}")
                    }
                    is EditModeEvent.TimerUpdated -> {
                        Timber.d("편집 모드 타이머 업데이트: ${event.remainingTimeMs}ms")
                        // 타이머 상태 업데이트 처리 추가
                        updateState { state ->
                            when (state) {
                                is MapState.Success -> state.copy(
                                    editModeTimeRemaining = event.remainingTimeMs
                                )
                                else -> state
                            }
                        }
                    }
                    is EditModeEvent.TimerExpired -> {
                        Timber.d("편집 모드 타이머 만료")
                    }
                    is EditModeEvent.Error -> {
                        val exception = event.exception
                        val errorMessage = "편집 모드 오류 이벤트: ${event.message}"
                        Timber.e(exception as Throwable, errorMessage)
                        handleError(exception, event.message)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e as Throwable, "편집 모드 이벤트 처리 중 오류 발생")
                handleError(e, "편집 모드 이벤트 처리 중 오류가 발생했습니다.")
            }
        }
    }
    
    /**
     * 위치 이벤트 구독
     */
    private fun subscribeToLocationEvents() {
        // 위치 추적 이벤트 구독 로직이 필요하면 여기에 구현
        Timber.d("위치 이벤트 구독 설정 완료")
    }

    /**
     * 상태 수집기 설정 - 각 관리자의 상태 변화를 감지하여 통합 상태를 업데이트합니다.
     */
    private fun setupStateCollectors() {
        viewModelScope.launch {
            try {
                // 각 Flow를 개별적으로 수집
                val markersFlow = markerManager.markers
                val selectedMarkerIdFlow = markerManager.selectedMarkerId
                val memosFlow = memoManager.memos
                val locationFlow = locationTracker.currentLocation.map { location ->
                    location?.let { LatLng(it.latitude, it.longitude) }
                }
                val editModeStateFlow = editModeManager.state
                val errorStateFlow = _errorState
                val markerToDeleteIdFlow = _markerToDeleteId

                // 모든 Flow를 결합
                combine(
                    markersFlow,
                    selectedMarkerIdFlow,
                    memosFlow,
                    locationFlow,
                    editModeStateFlow,
                    errorStateFlow,
                    markerToDeleteIdFlow
                ) { values ->
                    // 안전한 타입 캐스팅으로 변경
                    @Suppress("UNCHECKED_CAST")
                    val markers = values[0] as? List<Marker> ?: emptyList()
                    val selectedMarkerId = values[1] as? String?
                    @Suppress("UNCHECKED_CAST")
                    val memos = values[2] as? List<Memo> ?: emptyList()
                    val location = values[3] as? LatLng?
                    val editModeState = values[4] as? EditModeState ?: EditModeState()
                    val error = values[5] as? Throwable?
                    val markerToDeleteId = values[6] as? String?
                    
                    // 현재 줌 레벨 가져오기 (성공 상태에서만 존재)
                    val currentZoomLevel = (_mapState.value as? MapState.Success)?.zoomLevel
                    
                    // MapStateProcessor를 통해 상태 처리
                    mapStateProcessor.process(
                        markers = markers,
                        selectedMarkerId = selectedMarkerId,
                        memos = memos,
                        location = location,
                        editModeState = editModeState,
                        error = error,
                        markerToDeleteId = markerToDeleteId,
                        currentZoomLevel = currentZoomLevel
                    )
                }.collect { state ->
                    _mapState.value = state
                    Timber.d("MapState 업데이트: $state")
                }
            } catch (e: Exception) {
                Timber.e(e, "상태 수집 중 오류 발생")
                _mapState.value = MapState.Error(MapError.fromException(e))
            }
        }
    }

    /**
     * 상태 업데이트 헬퍼 메서드
     */
    private fun updateState(update: (MapState) -> MapState) {
        _mapState.value = update(_mapState.value)
    }

    /**
     * 마커 삭제 - 선택된 마커를 삭제합니다.
     * @param markerId 삭제할 마커 ID
     */
    fun deleteMarker(markerId: String) {
        viewModelScope.launch {
            try {
                Timber.d("마커 삭제 시작: markerId=$markerId")
                
                // 먼저 UI에서 마커가 즉시 제거되도록 상태 업데이트 (낙관적 업데이트)
                updateMapStateWithMarkerToDelete(markerId)
                
                // 마커 매니저에게 UI에서 즉시 제거 요청
                markerManager.forceRemoveMarkerFromList(markerId)
                
                // 이벤트 핸들러로 실제 삭제 작업 위임
                mapEventHandler.handleDeleteMarker(markerId)
                
                Timber.d("마커 삭제 요청 완료: markerId=$markerId")
            } catch (e: Exception) {
                Timber.e(e, "마커 삭제 중 예외 발생")
                handleError(e, "마커 삭제 중 오류가 발생했습니다.")
                
                // 오류 발생 시 마커 목록 새로고침 시도
                refreshMarkers()
            }
        }
    }
    
    /**
     * 마커 목록 새로고침 - 낙관적 업데이트 실패 시 마커 상태를 복원합니다.
     */
    private fun refreshMarkers() {
        viewModelScope.launch {
            try {
                // 현재 위치에 기반한 마커 로드
                val currentLocation = locationTracker.currentLocation.value
                val currentGeohash = locationTracker.currentGeohash.value
                val neighbors = locationTracker.neighbors.value
                
                if (currentGeohash != null && currentLocation != null) {
                    Timber.d("현재 위치 기반 마커 새로고침 시작")
                    markerManager.loadMarkersInAreaOptimized(
                        currentGeohash,
                        neighbors,
                        MapConstants.DEFAULT_ZOOM
                    )
                } else {
                    Timber.d("위치 정보 없음, 마커 새로고침 불가")
                }
                
                Timber.d("마커 목록 새로고침 완료")
            } catch (e: Exception) {
                Timber.e(e, "마커 목록 새로고침 중 오류 발생")
            }
        }
    }
    
    /**
     * 오류 처리 - 오류를 로깅하고 오류 상태를 업데이트합니다.
     * @param e 발생한 예외
     * @param message 사용자에게 표시할 오류 메시지
     */
    private fun handleError(e: Throwable, message: String) {
        Timber.e(e, "오류 발생: $message")
        
        // MapError 객체 생성 및 상태 업데이트
        val mapError = MapError.fromException(e).copy(message = message)
        updateErrorState(mapError)
        
        // 추가 로깅 (디버깅 목적)
        if (e.cause != null) {
            Timber.e("원인 예외: ${e.cause?.message}")
        }
    }
    
    /**
     * 오류 상태 업데이트
     * @param error 업데이트할 오류 객체
     */
    private fun updateErrorState(error: MapError) {
        _errorState.value = error
        Timber.e("맵 오류 상태 업데이트: ${error.message}")
    }

    /**
     * 삭제할 마커 ID로 맵 상태 업데이트
     */
    fun updateMapStateWithMarkerToDelete(markerId: String) {
        _markerToDeleteId.value = markerId
        Timber.d("삭제할 마커 ID 설정: $markerId")
    }
    
    /**
     * 마커 삭제 상태 초기화
     */
    fun resetMarkerToDeleteState() {
        _markerToDeleteId.value = null
        Timber.d("마커 삭제 상태 초기화")
    }

    /**
     * 카메라 위치 변경 처리
     */
    fun onCameraPositionChanged(target: LatLng, zoom: Double) {
        // 현재 상태가 Success이고 지도 줌 레벨이 변경된 경우에만 업데이트
        if (_mapState.value is MapState.Success && (_mapState.value as MapState.Success).zoomLevel != zoom) {
            _mapState.value = (_mapState.value as MapState.Success).copy(zoomLevel = zoom)
            Timber.d("지도 줌 레벨 업데이트: $zoom")
        }
        
        Timber.d("카메라 위치 변경: $target, 줌: $zoom")
    }

    /**
     * 메모 로드 - MapEventHandler에 메모 로드 작업을 위임합니다.
     * @param markerId 메모를 로드할 마커 ID
     */
    fun loadMemos(markerId: String) {
        viewModelScope.launch {
            try {
                Timber.d("메모 로드 시작: markerId=$markerId")
                mapEventHandler.handleLoadMemos(markerId)
                
                // 메모 로딩 시 현재 선택된 마커 ID 업데이트 로깅
                Timber.d("메모 로드 요청 완료: markerId=$markerId")
            } catch (e: Exception) {
                Timber.e(e, "메모 로드 중 예외 발생")
                handleError(e, "메모 로드 중 오류가 발생했습니다.")
            }
        }
    }

    /**
     * 메모 삭제 - MemoManager에 메모 삭제 작업을 위임합니다.
     */
    fun deleteMemo(memoId: String) {
        viewModelScope.launch {
            try {
                // 쓰기 모드 타이머 재시작
                restartEditModeTimer()
                
                // 이벤트 핸들러로 위임
                mapEventHandler.handleDeleteMemo(memoId)
            } catch (e: Exception) {
                val errorMsg = "메모 삭제 중 예외 발생"
                Timber.e(e, errorMsg)
                handleError(e, "메모 삭제 중 오류가 발생했습니다.")
            }
        }
    }

    /**
     * 선택된 마커 초기화 - 각 Manager의 선택 상태를 초기화합니다.
     */
    fun clearSelectedMarker() {
        markerManager.clearSelectedMarker()
        memoManager.clearSelectedMarker()
    }

    /**
     * 메모 생성 - 지도 화면에서 메모를 생성합니다.
     * 메모 생성 후 쓰기 모드 타이머를 재시작하고, 실제 생성 작업은 mapEventHandler에 위임합니다.
     * 
     * @param markerId 메모를 생성할 마커 ID
     * @param content 메모 내용
     */
    fun createMemo(markerId: String, content: String) {
        viewModelScope.launch {
            try {
                Timber.d("메모 생성 시작: 마커 ID=$markerId, 내용=${content.take(10)}${if (content.length > 10) "..." else ""}")
                
                // 쓰기 모드 타이머 재시작
                restartEditModeTimer()
                
                // 이벤트 핸들러로 위임
                mapEventHandler.handleCreateMemo(markerId, content)
            } catch (e: Exception) {
                Timber.e(e, "메모 생성 중 예외 발생: ${e.message}")
                handleError(e, "메모 생성 중 오류가 발생했습니다.")
            }
        }
    }

    /**
     * 마커 선택 - MarkerManager에 마커 선택 작업을 위임하고 메모를 로드합니다.
     */
    fun selectMarker(markerId: String) {
        markerManager.selectMarker(markerId)
        loadMemos(markerId)
    }

    /**
     * 지도 클릭 이벤트 처리 - 편집 모드에 따라 다른 동작을 수행합니다.
     * @param coord 클릭된 지도 좌표
     */
    fun onMapClicked(coord: LatLng) {
        try {
            val isEditMode = editModeManager.getCurrentMode()
            Timber.d("지도 클릭: 좌표=(${coord.latitude}, ${coord.longitude}), 모드=${if(isEditMode) "쓰기" else "읽기"}")
            
            // 쓰기 모드일 때만 타이머 재시작
            if (isEditMode) {
                restartEditModeTimer()
            }
            
            // 이벤트 핸들러로 위임 (모드에 따라 내부적으로 다르게 처리)
            mapEventHandler.handleMapClick(coord)
        } catch (e: Exception) {
            Timber.e(e, "지도 클릭 처리 중 오류 발생")
            handleError(e, "지도 클릭 이벤트 처리 중 오류가 발생했습니다.")
        }
    }

    /**
     * 마커 클릭 이벤트 처리
     * @param markerId 클릭된 마커 ID
     * @return 이벤트 처리 여부
     */
    fun onMarkerClick(markerId: String): Boolean {
        Timber.d("마커 클릭: markerId=$markerId")
        
        try {
            val currentState = mapState.value
            if (currentState !is MapState.Success) {
                Timber.d("마커 클릭 처리 불가: 상태가 Success가 아님")
                return false
            }
            
            // 쓰기 모드일 때 타이머 재시작
            if (currentState.editMode) {
                restartEditModeTimer()
            }
            
            // 메모장 표시 상태 관리
            handleMemoDialogStateForMarkerClick(markerId)
            
            // 이벤트 핸들러로 위임 (마커 선택 및 메모 로드 처리)
            return mapEventHandler.handleMarkerClick(markerId)
        } catch (e: Exception) {
            Timber.e(e, "마커 클릭 처리 중 오류 발생")
            handleError(e, "마커 클릭 이벤트 처리 중 오류가 발생했습니다.")
            return false
        }
    }
    
    /**
     * 마커 클릭 시 메모장 표시 상태 관리
     * @param markerId 클릭된 마커 ID
     */
    private fun handleMemoDialogStateForMarkerClick(markerId: String) {
        val currentState = _memoDialogState.value
        
        // 메모장이 표시되어 있지 않은 경우
        if (!currentState.shouldShow) {
            showMemoDialogForMarker(markerId)
            return
        }
        
        // 이미 다른 마커의 메모장이 표시 중인 경우
        if (currentState.markerId != markerId) {
            Timber.d("다른 마커(${currentState.markerId})의 메모장이 표시 중, 새 마커($markerId)로 전환")
            _memoDialogState.value = MemoDialogState(shouldShow = false, markerId = null)
            showMemoDialogForMarker(markerId)
        } else {
            Timber.d("동일한 마커의 메모장이 이미 표시 중: $markerId")
        }
    }

    /**
     * 마커 개수 조회 - 전체 마커 개수를 조회합니다.
     */
    fun countAllMarkers() {
        viewModelScope.launch {
            try {
                val totalMarkers = markerRepository.getMarkerCount()
                Timber.d("Room DB에 저장된 전체 마커 개수: $totalMarkers")
            } catch (e: Exception) {
                Timber.e(e, "전체 마커 개수 조회 중 오류 발생")
            }
        }
    }

    /**
     * Resume 이벤트 처리 - 포그라운드 전환 시 메모장 상태를 초기화합니다.
     */
    fun onResume() {
        viewModelScope.launch {
            try {
                Timber.d("onResume: 포그라운드 전환 - 메모장 상태 초기화 시작")
                
                // 포그라운드 전환 시 메모장 상태 초기화
                _memoDialogState.value = MemoDialogState(shouldShow = false, markerId = null)
                
                // 선택된 마커 상태도 초기화
                markerManager.clearSelectedMarker()
                memoManager.clearSelectedMarker()
                
                Timber.d("onResume: 포그라운드 전환 - 메모장 상태 초기화 완료")
            } catch (e: Exception) {
                Timber.e(e, "onResume 처리 중 오류 발생")
                // UI 흐름에 영향을 주지 않도록 오류는 사용자에게 표시하지 않음
            }
        }
    }
    
    /**
     * Pause 이벤트 처리 - 백그라운드 전환 시 메모장 상태를 초기화합니다.
     */
    fun onPause() {
        try {
            Timber.d("onPause: 백그라운드 전환 - 메모장 상태 초기화")
            _memoDialogState.value = MemoDialogState(shouldShow = false, markerId = null)
        } catch (e: Exception) {
            Timber.e(e, "onPause 처리 중 오류 발생")
            // UI 흐름에 영향을 주지 않도록 오류는 사용자에게 표시하지 않음
        }
    }

    /**
     * 메모장 표시 완료 처리 - 메모장 UI가 표시된 후 호출됩니다.
     */
    fun onMemoDialogShown() {
        try {
            // 이벤트 핸들러로 위임
            mapEventHandler.handleMemoDialogShown()
            
            // 메모장 상태 유지 (shouldShow를 true로 유지)
            val currentMarkerId = _memoDialogState.value.markerId
            Timber.d("메모장 표시 완료 처리: shouldShow=true, markerId=$currentMarkerId")
        } catch (e: Exception) {
            Timber.e(e, "메모장 표시 완료 처리 중 오류 발생")
            // UI 흐름에 영향을 주지 않도록 오류는 사용자에게 표시하지 않음
        }
    }

    /**
     * 메모장 표시 상태 초기화 - 현재 표시 중인 메모장 UI를 닫고 상태를 초기화합니다.
     */
    fun resetMemoDialogState() {
        Timber.d("메모장 표시 상태 초기화")
        onMemoDialogDismissed(resetState = true)
    }

    /**
     * 메모장 닫힘 처리 - 메모장 UI가 닫힐 때 호출됩니다.
     * @param resetState 상태를 완전히 초기화할지 여부 (기본값: true)
     */
    fun onMemoDialogDismissed(resetState: Boolean = true) {
        try {
            val oldMarkerId = _memoDialogState.value.markerId
            Timber.d("메모장 닫힘 처리: 마커 ID=$oldMarkerId, 상태 초기화=$resetState")
            
            // 이벤트 핸들러로 위임
            mapEventHandler.handleMemoDialogDismissed()
            
            // 메모장 상태 초기화
            if (resetState) {
                // 완전히 초기화 (새로운 마커 클릭 시 다시 메모장이 열림)
                _memoDialogState.value = MemoDialogState(shouldShow = false, markerId = null)
                Timber.d("메모장 상태 완전 초기화")
            } else {
                // 표시 상태만 변경 (마커 ID는 유지)
                _memoDialogState.value = _memoDialogState.value.copy(shouldShow = false)
                Timber.d("메모장 상태 부분 초기화 (마커 ID 유지)")
            }
        } catch (e: Exception) {
            Timber.e(e, "메모장 닫힘 처리 중 오류 발생")
            // UI 흐름에 영향을 주지 않도록 오류는 사용자에게 표시하지 않음
            // 상태 초기화는 시도
            _memoDialogState.value = MemoDialogState(shouldShow = false, markerId = null)
        }
    }

    /**
     * 편집 모드 토글 - 읽기 모드와 쓰기 모드 간 전환합니다.
     * @param onModeChanged 모드 변경 시 호출될 콜백 함수 (옵션)
     */
    fun toggleEditMode(onModeChanged: ((Boolean) -> Unit)? = null) {
        try {
            Timber.d("편집 모드 토글 요청")
            
            // 콜백이 제공된 경우 이를 추가
            onModeChanged?.let { editModeManager.addOnModeChangeListener(it) }
            
            // 이벤트 핸들러로 위임
            mapEventHandler.toggleEditMode()
        } catch (e: Exception) {
            Timber.e(e, "편집 모드 토글 중 오류 발생")
            handleError(e, "편집 모드 전환 중 오류가 발생했습니다.")
        }
    }

    /**
     * 편집 모드 설정 - 특정 모드로 직접 설정합니다.
     * @param editMode 설정할 편집 모드 상태 (true: 쓰기 모드, false: 읽기 모드)
     * @param onModeChanged 모드 변경 시 호출될 콜백 함수 (옵션)
     */
    fun setEditMode(editMode: Boolean, onModeChanged: ((Boolean) -> Unit)? = null) {
        try {
            Timber.d("편집 모드 직접 설정: ${if(editMode) "쓰기" else "읽기"} 모드")
            
            // 콜백이 제공된 경우 이를 추가
            onModeChanged?.let { editModeManager.addOnModeChangeListener(it) }
            
            // 편집 모드 설정
            editModeManager.setEditMode(editMode)
        } catch (e: Exception) {
            Timber.e(e, "편집 모드 설정 중 오류 발생")
            handleError(e, "편집 모드 설정 중 오류가 발생했습니다.")
        }
    }

    /**
     * 편집 모드 타이머 재시작 - EditModeManager에 타이머 재시작 작업을 위임합니다.
     */
    fun restartEditModeTimer() {
        editModeManager.restartEditModeTimer()
    }

    /**
     * 메모장 표시 - 특정 마커에 대한 메모장을 표시합니다.
     * @param markerId 메모장을 표시할 마커 ID
     */
    private fun showMemoDialogForMarker(markerId: String) {
        Timber.d("메모장 표시 요청: markerId=$markerId")
        
        // 현재 상태 확인 및 중복 요청 방지
        val currentState = _memoDialogState.value
        if (currentState.shouldShow && currentState.markerId == markerId) {
            Timber.d("이미 동일한 마커($markerId)의 메모장이 표시 중. 중복 요청 무시")
            return
        }
        
        viewModelScope.launch {
            try {
                // 마커 선택 및 메모 로드 (순서 중요)
                markerManager.selectMarker(markerId)
                loadMemos(markerId)
                
                // 메모장 표시 상태 업데이트
                _memoDialogState.value = MemoDialogState(
                    shouldShow = true,
                    markerId = markerId
                )
                
                // 선택된 마커 검증 (정보 제공 목적)
                val marker = markerManager.getMarkerById(markerId)
                if (marker == null) {
                    Timber.w("메모장을 표시하려는 마커($markerId)가 존재하지 않습니다. 그러나 UI 표시는 계속 진행합니다.")
                }
            } catch (e: Exception) {
                Timber.e(e, "메모장 표시 중 오류 발생: markerId=$markerId")
                handleError(e, "메모장을 표시하는 중 오류가 발생했습니다.")
                
                // 오류 발생 시에도 UI 경험을 위해 메모장은 표시 시도
                _memoDialogState.value = MemoDialogState(
                    shouldShow = true,
                    markerId = markerId
                )
            }
        }
    }

    /**
     * 리소스 정리 메서드 - onCleared()에서 호출됨
     */
    private fun cleanupResources() {
        // 위치 추적 중지
        try {
            locationTracker.stopTracking()
            Timber.d("위치 추적 중지 완료")
        } catch (e: Exception) {
            Timber.e(e, "위치 추적 중지 중 오류 발생")
        }
        
        // 편집 모드 타이머 정리
        try {
            editModeManager.clearTimerAndJobs()
            Timber.d("편집 모드 타이머 정리 완료")
        } catch (e: Exception) {
            Timber.e(e, "편집 모드 타이머 정리 중 오류 발생")
        }
        
        // 메모 관리자 리소스 정리
        try {
            memoManager.clearSelectedMarker()
            Timber.d("메모 관리자 리소스 정리 완료")
        } catch (e: Exception) {
            Timber.e(e, "메모 관리자 리소스 정리 중 오류 발생")
        }
        
        // 마커 관리자 리소스 정리
        try {
            markerManager.clearSelectedMarker()
            Timber.d("마커 관리자 리소스 정리 완료")
        } catch (e: Exception) {
            Timber.e(e, "마커 관리자 리소스 정리 중 오류 발생")
        }
        
        Timber.d("MapViewModel 리소스 정리 완료")
    }

    /**
     * ViewModel이 해제될 때 호출되는 메서드 - 리소스를 정리합니다.
     */
    override fun onCleared() {
        super.onCleared()
        cleanupResources()
    }
} 