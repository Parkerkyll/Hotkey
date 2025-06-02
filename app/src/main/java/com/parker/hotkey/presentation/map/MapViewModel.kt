package com.parker.hotkey.presentation.map

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naver.maps.geometry.LatLng
import com.parker.hotkey.data.remote.network.ConnectionStateMonitor
import com.parker.hotkey.domain.manager.EditModeEvent
import com.parker.hotkey.domain.manager.EditModeManager
import com.parker.hotkey.domain.manager.EditModeState
import com.parker.hotkey.domain.manager.LocationTracker
import com.parker.hotkey.domain.manager.MarkerManager
import com.parker.hotkey.domain.manager.MemoEvent
import com.parker.hotkey.domain.manager.MemoManager
import com.parker.hotkey.domain.manager.TemporaryMarkerEvent
import com.parker.hotkey.domain.manager.TemporaryMarkerManager
import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.model.Memo
import com.parker.hotkey.domain.model.state.DialogState
import com.parker.hotkey.domain.repository.AuthRepository
import com.parker.hotkey.domain.repository.LocationManager
import com.parker.hotkey.domain.repository.MarkerRepository
import com.parker.hotkey.domain.repository.MemoRepository
import com.parker.hotkey.domain.usecase.marker.CreateMarkerUseCase
import com.parker.hotkey.domain.usecase.marker.DeleteMarkerWithValidationUseCase
import com.parker.hotkey.presentation.map.event.MapEventHandler
import com.parker.hotkey.presentation.map.markers.MarkerViewModel
import com.parker.hotkey.presentation.map.processor.MapStateProcessor
import com.parker.hotkey.presentation.memo.MemoInteractor
import com.parker.hotkey.presentation.state.MapState
import com.parker.hotkey.domain.util.EventHandler
import com.parker.hotkey.util.SharedPrefsManager
import com.parker.hotkey.util.calculateDistanceTo
import com.parker.hotkey.domain.constants.MapConstants
import com.parker.hotkey.di.MapFeatureFlagDependencies
import com.parker.hotkey.di.MapManagerDependencies
import com.parker.hotkey.di.MapProcessorDependencies
import com.parker.hotkey.di.MapRepositoryDependencies
import com.parker.hotkey.di.MapUseCaseDependencies
import com.parker.hotkey.di.MapUtilityDependencies
import com.parker.hotkey.domain.constants.GeohashConstants
import com.parker.hotkey.domain.map.MarkerLoadingCoordinator
import com.parker.hotkey.domain.util.AppStateManager
import com.parker.hotkey.domain.util.AppStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlinx.coroutines.withTimeoutOrNull
import com.parker.hotkey.util.Debouncer
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.parker.hotkey.domain.repository.MarkerQueryOptions

/**
 * 지도 화면 관련 UI 이벤트를 정의하는 sealed 클래스
 */
sealed class MapEvent {
    /**
     * 확인 스낵바 표시 이벤트
     * @param message 표시할 메시지
     * @param actionText 액션 버튼 텍스트
     * @param onAction 액션 버튼 클릭 시 실행할 콜백
     * @param cancelText 취소 버튼 텍스트 (기본값: "취소")
     * @param onCancel 취소 버튼 클릭 시 실행할 콜백 (기본값: 빈 함수)
     */
    data class ShowConfirmationSnackbar(
        val message: String,
        val actionText: String,
        val onAction: () -> Unit,
        val cancelText: String = "취소",
        val onCancel: () -> Unit = {}
    ) : MapEvent()
    
    /**
     * 스낵바 표시 이벤트
     * @param message 표시할 메시지
     */
    data class ShowSnackbar(
        val message: String
    ) : MapEvent()
}

@HiltViewModel
class MapViewModel @Inject constructor(
    repositoryDependencies: MapRepositoryDependencies,
    useCaseDependencies: MapUseCaseDependencies,
    managerDependencies: MapManagerDependencies,
    processorDependencies: MapProcessorDependencies,
    utilityDependencies: MapUtilityDependencies,
    featureFlagDependencies: MapFeatureFlagDependencies,
    val markerViewModel: MarkerViewModel,
    private val markerLoadingCoordinator: MarkerLoadingCoordinator,
    val appStateManager: AppStateManager // AppStateManager를 public으로 선언
) : ViewModel() {
    // 그룹화된 의존성에서 필요한 개별 의존성 추출
    private val markerRepository = repositoryDependencies.markerRepository
    private val memoRepository = repositoryDependencies.memoRepository
    val authRepository = repositoryDependencies.authRepository
    private val syncRepository = repositoryDependencies.syncRepository
    
    private val createMarkerUseCase = useCaseDependencies.createMarkerUseCase
    private val deleteMarkerWithValidationUseCase = useCaseDependencies.deleteMarkerWithValidationUseCase
    private val syncDataUseCase = useCaseDependencies.syncDataUseCase
    
    // Fragment에서 접근 필요한 의존성은 public으로 유지
    val editModeManager = managerDependencies.editModeManager
    private val locationManager = managerDependencies.locationManager
    private val locationTracker = managerDependencies.locationTracker
    val markerManager = managerDependencies.markerManager
    private val memoManager = managerDependencies.memoManager
    private val temporaryMarkerManager = managerDependencies.temporaryMarkerManager
    
    private val mapStateProcessor = processorDependencies.mapStateProcessor
    private val mapEventHandler = processorDependencies.mapEventHandler
    private val memoInteractor = processorDependencies.memoInteractor
    
    private val context = utilityDependencies.context
    private val connectionStateMonitor = utilityDependencies.connectionStateMonitor
    private val sharedPrefsManager = utilityDependencies.sharedPrefsManager
    
    private val useTemporaryMarkerFeature = featureFlagDependencies.useTemporaryMarkerFeature

    // UI 이벤트를 위한 Flow
    private val _uiEvents = MutableSharedFlow<MapEvent>(
        replay = 0,
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val uiEvents: SharedFlow<MapEvent> = _uiEvents.asSharedFlow()

    // 메모장 다이얼로그 상태
    private val _memoDialogState = MutableStateFlow(DialogState())
    val memoDialogState: StateFlow<DialogState> = _memoDialogState.asStateFlow()
    
    // 에러 상태 Flow
    private val _errorState = MutableStateFlow<MapError?>(null)
    
    // 상태 통합을 위한 MapState Flow
    private val _mapState = MutableStateFlow<MapState>(MapState.Initial)
    val mapState: StateFlow<MapState> = _mapState.asStateFlow()
    
    // mapState에서 파생된 로딩 상태
    val shouldShowLoading = mapState.map { it is MapState.Loading }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    
    // 마커 삭제 상태를 담을 필드 추가
    private val _markerToDeleteId = MutableStateFlow<String?>(null)
    val markerToDeleteId: StateFlow<String?> = _markerToDeleteId.asStateFlow()

    // 내부 로직에서 사용할 선택된 마커 ID 상태
    val selectedMemoMarkerId = _memoDialogState.map { it.markerId }.stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )

    // 이전에 방문한 지역을 추적하기 위한 변수를 추가하세요 (클래스 상단 적절한 위치)
    private val visitedGeohashes = mutableSetOf<String>()
    // 첫 로드 여부를 판단하는 플래그
    private var isFirstLoad = true
    
    // 데이터 로드 요청 중인 geohash 추적
    private val loadingGeohashes = mutableSetOf<String>()
    // 지오해시 로드 요청 뮤텍스
    private val geohashLoadMutex = kotlinx.coroutines.sync.Mutex()

    // EventHandler 참조를 저장하기 위한 변수
    private var _eventHandler: EventHandler<Any>? = null

    // 마커 클릭 중복 처리 방지를 위한 변수들
    private var lastClickedMarkerId: String? = null
    private var lastClickTime: Long = 0
    private val CLICK_DEBOUNCE_TIME = 800L // 0.8초 내에 동일 마커 클릭 무시

    // 저장 성공 메시지 이벤트
    private val _saveSuccessEvent = MutableSharedFlow<String>()
    val saveSuccessEvent: SharedFlow<String> = _saveSuccessEvent
    
    // 저장 실패 메시지 이벤트
    private val _saveFailEvent = MutableSharedFlow<String>()
    val saveFailEvent: SharedFlow<String> = _saveFailEvent

    // 카메라 위치 변경 디바운서 추가
    private val cameraPositionDebouncer = Debouncer<Pair<LatLng, Double>>(300L)

    init {
        // 이벤트 구독을 가장 먼저 설정 (기존에는 마지막에 설정되었음)
        Timber.d("MapViewModel 초기화 시작")
        
        // AppStateManager의 상태 변화 구독 설정
        setupAppStateObserver()
        
        setupEventSubscriptions()
        initializeManagers()
        setupStateCollectors()
        Timber.d("MapViewModel 초기화 완료")
    }
    
    /**
     * AppStateManager의 상태 변화를 관찰하고 적절한 초기화 로직 실행
     */
    private fun setupAppStateObserver() {
        viewModelScope.launch {
            appStateManager.currentAppStatus.collect { appStatus ->
                Timber.d("앱 상태 변경 감지: $appStatus")
                when (appStatus) {
                    AppStatus.FRESH_INSTALL -> {
                        Timber.d("앱 최초 설치 - 초기화 로직 실행")
                        initializeForFreshInstall()
                    }
                    
                    AppStatus.NORMAL_LAUNCH -> {
                        Timber.d("일반 실행 - 표준 초기화 로직 실행 예정")
                        // 세부 로직 구현은 다음 단계에서 진행
                        // initializeForNormalLaunch()
                    }
                    
                    AppStatus.FOREGROUND_RESUME -> {
                        Timber.d("포그라운드 복귀 - 데이터 새로고침 로직 실행 예정")
                        // 세부 로직 구현은 다음 단계에서 진행
                        // refreshDataOnResume()
                    }
                    
                    AppStatus.NAVIGATION_RETURN -> {
                        Timber.d("네비게이션 복귀 - 로컬 데이터 우선 로딩 예정")
                        // 세부 로직 구현은 다음 단계에서 진행
                        // handleNavigationReturn()
                    }
                    
                    AppStatus.AFTER_UPDATE -> {
                        Timber.d("앱 업데이트 후 실행 - 업데이트 후처리 로직 실행 예정")
                        // 세부 로직 구현은 다음 단계에서 진행
                        // handleAfterUpdate()
                    }
                }
            }
        }
    }

    /**
     * 모든 이벤트 구독을 명시적인 순서로 설정합니다.
     */
    private fun setupEventSubscriptions() {
        Timber.d("이벤트 구독 설정 시작")
        
        // 1. 마커 이벤트 구독
        // subscribeToMarkerEvents()
        
        // 2. 메모 이벤트 구독
        subscribeToMemoEvents()
        
        // 3. 편집 모드 이벤트 구독
        subscribeToEditModeEvents()
        
        // 4. 위치 이벤트 구독
        subscribeToLocationEvents()
        
        // 5. 임시 마커 이벤트 구독 추가
        if (useTemporaryMarkerFeature) {
            subscribeToTemporaryMarkerEvents()
        }
        
        // 마커 상태 변화 구독 (마커 ViewModel의 상태를 통합)
        viewModelScope.launch {
            markerViewModel.state.collect { markerState ->
                updateMapStateWithMarkerState(markerState)
            }
        }
        
        Timber.d("이벤트 구독 설정 완료")
    }

    /**
     * 모든 매니저 초기화 및 구독 설정
     */
    private fun initializeManagers() {
        try {
            Timber.d("매니저 초기화 시작")
            
            // 1. 마커 매니저 초기화
            // markerViewModel.initialize() 호출 제거
            
            // 2. 메모 매니저 초기화
            // memoManager.initialize()
            
            // 3. 편집 모드 매니저 초기화
            // editModeManager.initialize()
            
            // 4. 임시 마커 매니저 초기화
            // temporaryMarkerManager.initialize()
            
            // 5. 위치 추적 초기화 - Flow를 통한 초기화 완료 이벤트 구독
            viewModelScope.launch {
                // 위치 추적기 초기화
                locationTracker.initialize()
                
                // 초기화 완료를 기다림 (delay 대신 상태 구독)
                locationTracker.initialized.filter { it }.first()
                Timber.d("LocationTracker 초기화 완료 상태 수신")
                
                // 초기화 완료 후 설정 계속 진행
                setupLocationChangeSubscription()
                setupNetworkMonitoring()
                
                // 8. 초기 네트워크 상태 확인 및 데이터 동기화
                val isConnected = connectionStateMonitor.isConnected()
                Timber.d("네트워크 연결 상태: $isConnected")
                
                // 위치와 연결 상태가 있는 경우 초기 데이터 동기화 수행
                val currentGeohash = locationTracker.currentGeohash.value
                if (currentGeohash != null && isConnected) {
                    Timber.d("초기화 완료 후 데이터 동기화 시작: geohash=$currentGeohash")
                    syncDataForGeohash(currentGeohash)
                } else {
                    Timber.d("초기화 후 데이터 동기화 불가: geohash=${currentGeohash}, network=${isConnected}")
                }
            }
            
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
     * 위치 변경 이벤트 구독 설정
     */
    private fun setupLocationChangeSubscription() {
        viewModelScope.launch {
            try {
                Timber.d("위치 변경 이벤트 구독 시작")
                
                if (locationTracker is com.parker.hotkey.domain.manager.impl.LocationTrackerImpl) {
                    locationTracker.locationChangedEvent.collect { event ->
                        Timber.d("위치 변경 이벤트 수신: 새 geohash=${event.newGeohash}, 이전=${event.previousGeohash}")
                        
                        // 새로운 지역으로 이동한 경우 데이터 동기화 수행
                        syncDataForGeohash(event.newGeohash)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "위치 변경 이벤트 구독 중 오류 발생")
            }
        }
    }
    
    /**
     * 현재 위치에 대한 데이터 동기화 수행
     */
    private fun syncDataForCurrentLocation() {
        viewModelScope.launch {
            try {
                val currentGeohash = locationTracker.currentGeohash.value
                if (currentGeohash != null) {
                    syncDataForGeohash(currentGeohash)
                } else {
                    Timber.w("현재 위치 정보가 없어 동기화할 수 없습니다.")
                }
            } catch (e: Exception) {
                Timber.e(e, "데이터 동기화 중 오류 발생: ${e.message}")
                handleError(e, "데이터 동기화 중 오류가 발생했습니다.")
            }
        }
    }

    /**
     * 특정 지역의 데이터 동기화 수행
     */
    private fun syncDataForGeohash(geohash: String) {
        viewModelScope.launch {
            try {
                // 이미 로드 중인 geohash인지 확인하고 중복 로드 방지
                geohashLoadMutex.withLock {
                    if (loadingGeohashes.contains(geohash)) {
                        Timber.tag("API_FLOW").d("이미 로드 중인 geohash입니다: $geohash - 중복 요청 무시")
                        return@withLock
                    }
                    loadingGeohashes.add(geohash)
                }
                
                Timber.d("지역 데이터 동기화 시작 - geohash: $geohash")
                
                // 동기화 상태를 로딩으로 업데이트
                _mapState.value = MapState.Loading
                
                // 마지막 동기화 시간 확인
                val lastSyncTimestamp = getLastSyncTimestamp(geohash)
                
                // 현재 위치의 이웃 geohash 가져오기
                val neighbors = locationTracker.neighbors.value.orEmpty()
                
                // 현재 줌 레벨 가져오기
                val currentZoom = locationTracker.getCurrentZoom() ?: MapConstants.DEFAULT_ZOOM
                
                // 마커 로딩과 동기화를 병렬로 수행하기 위해 MarkerViewModel의 syncDataAndLoadMarkers 활용
                markerViewModel.syncDataAndLoadMarkers(geohash, neighbors, currentZoom)
                
                // 데이터 동기화 작업 비동기 실행
                coroutineScope {
                    val syncResult = if (lastSyncTimestamp > 0) {
                        // 이전에 동기화한 적이 있는 지역 - LastSync 기반 증분 동기화 수행
                        Timber.d("LastSync 기반 증분 동기화 수행 - geohash: $geohash")
                        syncDataUseCase.syncIncrementalData(geohash)
                    } else {
                        // 처음 방문하는 지역 - 초기 데이터 로딩
                        Timber.d("초기 데이터 로딩 수행 - geohash: $geohash (마지막 동기화 기록 없음)")
                        syncDataUseCase(geohash)
                    }
                    
                    // 동기화 작업이 성공했을 경우 마지막 동기화 시간 업데이트
                    if (syncResult) {
                        val currentTime = System.currentTimeMillis()
                        Timber.d("데이터 동기화 성공: $geohash, 새 동기화 시간: ${currentTime}ms")
                        updateLastSyncTimestamp(geohash, currentTime)
                    } else {
                        Timber.w("데이터 동기화 실패: $geohash")
                    }
                    
                    // 동기화 상태 업데이트
                    _mapState.value = MapState.Success()
                }
            } catch (e: Exception) {
                Timber.e(e, "데이터 동기화 중 오류 발생: ${e.message}")
                handleError(e, "데이터 동기화 중 오류가 발생했습니다.")
            } finally {
                // 로딩 완료 후 상태 정리
                geohashLoadMutex.withLock {
                    loadingGeohashes.remove(geohash)
                }
            }
        }
    }

    /**
     * 특정 지역의 마지막 동기화 시간을 가져옵니다.
     */
    private fun getLastSyncTimestamp(geohash: String): Long {
        return try {
            val key = "${SharedPrefsManager.LAST_SYNC_PREFIX}$geohash"
            val lastSync = sharedPrefsManager.getLongPreference(key, 0L)
            Timber.d("마지막 동기화 시간 조회: $geohash = $lastSync")
            lastSync
        } catch (e: Exception) {
            Timber.e(e, "마지막 동기화 시간 조회 중 오류 발생: $geohash")
            0L
        }
    }

    /**
     * 특정 지역의 마지막 동기화 시간을 업데이트합니다.
     */
    private fun updateLastSyncTimestamp(geohash: String, timestamp: Long) {
        try {
            val key = "${SharedPrefsManager.LAST_SYNC_PREFIX}$geohash"
            sharedPrefsManager.setLongPreference(key, timestamp)
            Timber.d("마지막 동기화 시간 업데이트: $geohash, $timestamp")
        } catch (e: Exception) {
            Timber.e(e, "마지막 동기화 시간 업데이트 중 오류 발생: $geohash")
        }
    }

    /**
     * 마커 이벤트 구독
     */
    // private fun subscribeToMarkerEvents() {
    
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
                        Timber.e(exception, errorMessage)
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
                        Timber.e(exception, errorMessage)
                        handleError(exception, event.message)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "편집 모드 이벤트 처리 중 오류 발생")
                handleError(e, "편집 모드 이벤트 처리 중 오류가 발생했습니다.")
            }
        }
    }
    
    /**
     * 위치 이벤트 구독 - geohash 변경 감지 및 서버 데이터 로드 추가
     */
    private fun subscribeToLocationEvents() {
        // geohash 변경 감지 및 서버 데이터 로드
        viewModelScope.launch {
            Timber.tag("API_FLOW").d("위치 이벤트 구독 설정 시작")
            
            locationTracker.getGeohashWithNeighborsFlow().collect { (geohash, neighbors) ->
                val eventId = UUID.randomUUID().toString().take(6)
                Timber.tag("API_FLOW").d("[$eventId] 위치 이벤트 수신 - geohash: $geohash, neighbors: ${neighbors.size}개")
                
                if (isFirstLoad) {
                    // 첫 로드 시에는 위치 이벤트에 의한 로드 무시
                    Timber.tag("API_FLOW").d("[$eventId] 첫 번째 위치 이벤트 무시 (isFirstLoad=true)")
                    isFirstLoad = false
                    return@collect
                }
                
                if (geohash != null && !visitedGeohashes.contains(geohash)) {
                    // 새로 방문한 geohash 영역
                    Timber.tag("API_FLOW").d("[$eventId] 새로운 geohash 영역 감지: $geohash, 서버 데이터 로드 시작")
                    visitedGeohashes.add(geohash) // 방문 목록에 추가
                    // 유일한 loadNewAreaData 메서드 호출 방식으로 통일
                    loadNewAreaDataWithLog(geohash, neighbors, locationTracker.getCurrentZoom() ?: MapConstants.DEFAULT_ZOOM, eventId)
                } else if (geohash != null) {
                    // 이미 방문한 영역, 로컬 DB에서만 로드
                    Timber.tag("API_FLOW").d("[$eventId] 이미 방문한 geohash 영역: $geohash, 로컬 데이터만 로드")
                    refreshMarkers(eventId)
                }
            }
            
            Timber.tag("API_FLOW").d("위치 이벤트 구독 설정 완료")
        }
    }

    /**
     * 임시 마커 이벤트 구독
     */
    private fun subscribeToTemporaryMarkerEvents() {
        viewModelScope.launch {
            temporaryMarkerManager.events.collect { event ->
                try {
                    Timber.d("임시 마커 이벤트 수신: $event")
                    when (event) {
                        is TemporaryMarkerEvent.MarkerCreated -> {
                            try {
                                Timber.d("임시 마커 생성 이벤트 수신: ${event.marker.id}")
                                // 필요한 경우 추가 처리
                            } catch (e: Exception) {
                                Timber.e(e, "임시 마커 생성 이벤트 처리 중 예외 발생: ${event.marker.id}")
                            }
                        }
                        is TemporaryMarkerEvent.MarkerMadePermanent -> {
                            try {
                                Timber.d("임시 마커 영구 저장 이벤트 수신: ${event.markerId}")
                                // 마커가 영구 저장되었을 때 성공 메시지 표시
                                _saveSuccessEvent.emit("마커 저장 성공")
                            } catch (e: Exception) {
                                Timber.e(e, "임시 마커 영구 저장 이벤트 처리 중 예외 발생: ${event.markerId}")
                                _saveFailEvent.emit("저장 실패. 다시 시도하세요.")
                            }
                        }
                        is TemporaryMarkerEvent.MarkerDeleted -> {
                            try {
                                Timber.d("임시 마커 삭제 이벤트 수신: ${event.markerId}")
                                val markerId = event.markerId
                                
                                // 선택된 마커인 경우 선택 해제
                                if (markerManager.selectedMarkerId.value == markerId) {
                                    Timber.d("삭제된 마커가 선택 상태임 - 선택 초기화: $markerId")
                                    markerManager.clearSelectedMarker()
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "마커 삭제 이벤트 처리 중 예외 발생: ${event.markerId}")
                            }
                        }
                        is TemporaryMarkerEvent.Error -> {
                            Timber.e(event.throwable, "임시 마커 오류 이벤트 수신: ${event.message}")
                            viewModelScope.launch {
                                _saveFailEvent.emit(event.message)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "임시 마커 이벤트 처리 중 예외 발생")
                    viewModelScope.launch {
                        _saveFailEvent.emit("저장 실패. 다시 시도하세요.")
                    }
                }
            }
        }
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
                    val markers = values[0] as List<Marker>
                    val selectedMarkerId = values[1] as? String?
                    @Suppress("UNCHECKED_CAST")
                    val memos = values[2] as List<Memo>
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
     * 마커 삭제 처리
     */
    fun deleteMarker(markerId: String) {
        markerViewModel.deleteMarker(markerId)
    }
    
    /**
     * 마커 목록 새로고침 - 낙관적 업데이트 실패 시 마커 상태를 복원합니다.
     */
    private fun refreshMarkers(eventId: String = UUID.randomUUID().toString().take(6)) {
        viewModelScope.launch {
            try {
                // 현재 위치에 기반한 마커 로드
                val currentLocation = locationTracker.currentLocation.value
                val currentGeohash = locationTracker.currentGeohash.value
                val neighbors = locationTracker.neighbors.value.orEmpty()
                
                if (currentGeohash != null && currentLocation != null) {
                    Timber.tag("API_FLOW").d("[$eventId] 현재 위치 기반 마커 새로고침 시작 - geohash: $currentGeohash")
                    val startTime = System.currentTimeMillis()
                    
                    markerManager.loadMarkersInAreaOptimized(
                        currentGeohash,
                        neighbors,
                        locationTracker.getCurrentZoom() ?: MapConstants.DEFAULT_ZOOM
                    )
                    
                    val duration = System.currentTimeMillis() - startTime
                    Timber.tag("API_FLOW").d("[$eventId] 마커 새로고침 완료 (${duration}ms)")
                }
            } catch (e: Exception) {
                Timber.tag("API_FLOW").e(e, "[$eventId] 마커 새로고침 중 오류 발생")
            }
        }
    }
    
    /**
     * 새 지역 데이터 로드 - 새로운 geohash 영역에 진입했을 때 호출
     * 서버에서 초기 데이터를 로드한 후 지역 마커를 표시합니다.
     */
    private fun loadNewAreaDataWithLog(
        geohash: String, 
        neighbors: List<String>?, 
        zoomLevel: Double,
        eventId: String = UUID.randomUUID().toString().take(6)
    ) {
        viewModelScope.launch {
            try {
                // 이미 로드 중인 geohash인지 확인하고 중복 로드 방지
                geohashLoadMutex.withLock {
                    if (loadingGeohashes.contains(geohash)) {
                        Timber.tag("API_FLOW").d("[$eventId] 이미 로드 중인 geohash입니다: $geohash - 중복 요청 무시")
                        return@withLock
                    }
                    loadingGeohashes.add(geohash)
                }
                
                Timber.tag("API_FLOW").d("[$eventId] 새 지역 데이터 로드 시작: $geohash, 줌: $zoomLevel")
                
                // UI 상태를 로딩으로 변경
                _mapState.value = MapState.Loading
                
                // 서버에서 지역 데이터 로드
                val apiStartTime = System.currentTimeMillis()
                val success = syncDataUseCase(geohash)
                val apiDuration = System.currentTimeMillis() - apiStartTime
                
                if (success) {
                    Timber.tag("API_FLOW").d("[$eventId] 지역 데이터 로드 성공: $geohash (${apiDuration}ms), 이제 마커 로드")
                } else {
                    Timber.tag("API_FLOW").w("[$eventId] 지역 데이터 로드 실패: $geohash (${apiDuration}ms)")
                }
                
                // 서버 로드 성공 여부와 관계없이 로컬 DB에서 마커 로드
                val loadStartTime = System.currentTimeMillis()
                val loadJob = coroutineScope {
                    async {
                        markerManager.loadMarkersInAreaOptimized(
                            geohash, 
                            neighbors ?: emptyList(), 
                            zoomLevel
                        )
                    }
                }
                
                // 마커 로드 결과 처리
                loadJob.await()
                val loadDuration = System.currentTimeMillis() - loadStartTime
                
                Timber.tag("API_FLOW").d("[$eventId] 마커 로드 완료: $geohash (${loadDuration}ms)")
                
                // UI 상태를 성공으로 변경
                _mapState.value = MapState.Success(zoomLevel = zoomLevel)
                
                // DB와 메모리 간 마커 상태 확인 (지연 없이 즉시 실행)
                validateMarkerState(geohash, neighbors ?: emptyList(), zoomLevel)
                
                Timber.tag("API_FLOW").d("[$eventId] 새 지역 데이터 로드 완료: 총 소요시간 ${System.currentTimeMillis() - apiStartTime}ms")
            } catch (e: Exception) {
                Timber.tag("API_FLOW").e(e, "[$eventId] 새 지역 데이터 로드 중 오류 발생: $geohash")
                _mapState.value = MapState.Error(MapError.fromException(e))
            } finally {
                // 로딩 완료 후 상태 정리
                geohashLoadMutex.withLock {
                    loadingGeohashes.remove(geohash)
                }
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
        val mapError = MapError.fromException(e)
        // 원래 예외 메시지 대신 전달받은 메시지 출력
        Timber.e("사용자에게 표시할 메시지: $message")
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
     * 카메라 위치 변경 이벤트 처리 - 마커 로딩 없이 줌 레벨만 업데이트
     * 디바운싱을 적용하여 빈번한 호출을 방지합니다.
     */
    fun onCameraPositionChanged(target: LatLng, zoom: Double) {
        // 카메라 변경 이벤트에 디바운싱 적용
        cameraPositionDebouncer.debounce(Pair(target, zoom)) { (finalTarget, finalZoom) ->
            Timber.d("디바운싱 후 카메라 위치 변경 처리: $finalTarget, 줌: $finalZoom")
            
            // 현재 상태가 Success이고 지도 줌 레벨이 변경된 경우에만 업데이트
            if (_mapState.value is MapState.Success && (_mapState.value as MapState.Success).zoomLevel != finalZoom) {
                _mapState.value = (_mapState.value as MapState.Success).copy(zoomLevel = finalZoom)
                Timber.d("지도 줌 레벨 업데이트: $finalZoom")
                
                // LocationTracker의 줌 레벨도 업데이트
                if (locationTracker is com.parker.hotkey.domain.manager.impl.LocationTrackerImpl) {
                    // 이미 타입 체크를 했으므로 추가 캐스팅 불필요
                    locationTracker.updateZoom(finalZoom)
                    Timber.d("LocationTracker 줌 레벨 업데이트: $finalZoom")
                }
            }
            
            // 카메라 이동에 따른 마커 로딩 코드 삭제
            Timber.d("카메라 위치 변경: $finalTarget, 줌: $finalZoom (마커 로딩 없음)")
        }
    }

    /**
     * 선택된 마커 초기화 - 각 Manager의 선택 상태를 초기화합니다.
     */
    fun clearSelectedMarker() {
        markerViewModel.clearSelection()
    }

    /**
     * 마커 선택 - MarkerManager에 마커 선택 작업을 위임합니다.
     */
    fun selectMarker(markerId: String) {
        markerViewModel.selectMarker(markerId)
    }

    /**
     * 지도 클릭 이벤트 처리 - 편집 모드에 따라 다른 동작을 수행합니다.
     * @param coord 클릭된 지도 좌표
     */
    fun onMapClicked(coord: LatLng) {
        try {
            val isEditMode = editModeManager.getCurrentMode()
            Timber.e("지도 클릭: 좌표=(${coord.latitude}, ${coord.longitude}), 모드=${if(isEditMode) "쓰기모드" else "읽기모드"}")
            
            if (isEditMode) {
                Timber.e("쓰기 모드에서 지도 클릭 처리 시작")
                restartEditModeTimer()
                
                // 피처 플래그에 따라 기존 마커 생성 또는 임시 마커 생성
                if (useTemporaryMarkerFeature) {
                    Timber.e("임시 마커 기능 사용: createTemporaryMarkerAtLocation 호출")
                    createTemporaryMarkerAtLocation(coord)
                } else {
                    Timber.e("기존 마커 생성 로직 사용: mapEventHandler.handleMapClick 호출")
                    // 이벤트 핸들러로 위임 (기존 방식 유지)
                    mapEventHandler.handleMapClick(coord)
                }
            } else {
                Timber.e("읽기 모드에서 지도 클릭 - 마커 선택 초기화")
                markerManager.clearSelectedMarker()
                memoManager.clearMemos()
            }
        } catch (e: Exception) {
            Timber.e(e, "지도 클릭 처리 중 오류 발생: ${e.message}")
            handleError(e, "지도 클릭 이벤트 처리 중 오류가 발생했습니다.")
        }
    }

    /**
     * 임시 마커 생성 메서드
     */
    private fun createTemporaryMarkerAtLocation(coord: LatLng) {
        viewModelScope.launch {
            try {
                val userId = authRepository.getUserId()
                Timber.e("임시 마커 생성 시작: 좌표=(${coord.latitude}, ${coord.longitude}), 사용자ID=$userId")
                
                // 주변 20미터 이내 마커 검사 (좀 더 명확한 변수명 사용)
                val proximityCheckResult = checkNearbyMarkers(coord)
                
                // 결과 확인 로그 추가
                when (proximityCheckResult) {
                    is NearbyMarkerCheckResult.MarkerExists -> {
                        val markerCount = proximityCheckResult.nearbyMarkers.size
                        val minDistance = String.format("%.1f", proximityCheckResult.minDistance)
                        Timber.e("근처에 마커가 존재함: ${markerCount}개, 가장 가까운 거리=${minDistance}m")
                        
                        // 근처에 마커가 있을 경우 사용자에게 확인 요청
                        val message = "근처에 마커가 있습니다. 새로운 마커를 만드시겠습니까?"
                        
                        try {
                            Timber.e("스낵바 이벤트 발행 시도")
                            _uiEvents.emit(MapEvent.ShowConfirmationSnackbar(
                                message = message,
                                actionText = "마커 생성",
                                onAction = {
                                    Timber.e("스낵바 확인 버튼 클릭됨")
                                    // 사용자가 확인한 경우 마커 생성 진행
                                    viewModelScope.launch {
                                        Timber.e("확인 후 마커 생성 시작")
                                        createMarkerAfterConfirmation(userId, coord)
                                    }
                                },
                                cancelText = "취소",
                                onCancel = {
                                    Timber.e("스낵바 취소 버튼 클릭됨")
                                    // 취소 시 아무 작업도 하지 않음
                                }
                            ))
                            Timber.e("스낵바 이벤트 발행 성공")
                        } catch (e: Exception) {
                            Timber.e(e, "스낵바 이벤트 발행 실패: ${e.message}")
                            // 오류 발생 시 경고 후 마커 생성
                            showError(e, "마커 생성 확인 메시지 표시 중 오류가 발생했습니다. 마커를 생성합니다.")
                            createMarkerAfterConfirmation(userId, coord)
                        }
                    }
                    is NearbyMarkerCheckResult.NoMarkerNearby -> {
                        // 주변에 마커가 없으면 바로 생성
                        Timber.e("주변에 마커 없음: 바로 마커 생성")
                        val marker = temporaryMarkerManager.createTemporaryMarker(userId, coord)
                        Timber.e("임시 마커 생성 완료: ${marker.id}")
                        
                        // 임시 마커 플래그와 함께 메모장 표시
                        showMemoDialogForMarker(marker.id, isTemporary = true)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "임시 마커 생성 중 예외 발생: ${e.message}")
                handleError(e, "마커 생성 중 오류가 발생했습니다.")
            }
        }
    }
    
    /**
     * 사용자 확인 후 마커 생성
     */
    private suspend fun createMarkerAfterConfirmation(userId: String, coord: LatLng) {
        try {
            // 임시 마커 생성
            val marker = temporaryMarkerManager.createTemporaryMarker(userId, coord)
            Timber.d("확인 후 임시 마커 생성됨: ${marker.id}")
            
            // 임시 마커 플래그와 함께 메모장 표시
            showMemoDialogForMarker(marker.id, isTemporary = true)
        } catch (e: Exception) {
            Timber.e(e, "확인 후 임시 마커 생성 중 오류 발생")
            handleError(e, "마커 생성 중 오류가 발생했습니다.")
        }
    }
    
    /**
     * 주변 마커 검사
     */
    private suspend fun checkNearbyMarkers(coord: LatLng): NearbyMarkerCheckResult {
        // 현재 마커 목록 가져오기 (바로 가져오지 않고 이전에 캐시된 목록 가져옴)
        val currentMarkers = markerManager.markers.value
        val NEARBY_MARKER_THRESHOLD_METERS = 20.0
        
        Timber.e("주변 마커 검사 시작 - DEBUG: 좌표=(${coord.latitude}, ${coord.longitude}), 마커 수: ${currentMarkers.size}개")
        
        if (currentMarkers.isEmpty()) {
            Timber.e("마커 목록이 비어있음 - checkNearbyMarkers 실패")
            return NearbyMarkerCheckResult.NoMarkerNearby
        }
        
        // 각 마커까지의 거리 계산 및 상세 로그
        var foundNearby = false
        val markersWithDistance = mutableListOf<Pair<Marker, Double>>()
        
        for (marker in currentMarkers) {
            val markerLatLng = LatLng(marker.latitude, marker.longitude)
            val distance = markerLatLng.calculateDistanceTo(coord)
            
            markersWithDistance.add(Pair(marker, distance))
            
            // 개별 마커 거리 계산 결과 로그
            val isNearby = distance <= NEARBY_MARKER_THRESHOLD_METERS
            if (isNearby) {
                foundNearby = true
                Timber.e("가까운 마커 발견! ID=${marker.id}, 거리=${distance}m (임계값: ${NEARBY_MARKER_THRESHOLD_METERS}m)")
            } else {
                Timber.d("멀리 있는 마커: ID=${marker.id}, 거리=${distance}m")
            }
        }
        
        // 디버그용 로그: 모든 마커의 거리 정보 출력
        Timber.e("모든 마커 거리 계산 완료. 총 ${markersWithDistance.size}개, 근처 마커 ${if (foundNearby) "있음" else "없음"}")
        
        // 20미터 이내의 마커 필터링
        val nearbyMarkers = markersWithDistance
            .filter { (_, distance) -> distance <= NEARBY_MARKER_THRESHOLD_METERS }
            .map { it.first }
        
        Timber.e("주변 마커 검색 결과: 총 ${nearbyMarkers.size}개 (${NEARBY_MARKER_THRESHOLD_METERS}m 이내)")
        
        return if (nearbyMarkers.isEmpty()) {
            Timber.e("주변 마커 없음 - 마커 생성 가능")
            NearbyMarkerCheckResult.NoMarkerNearby
        } else {
            // 가장 가까운 마커까지의 거리 계산
            val minDistance = markersWithDistance
                .filter { (marker, _) -> marker.id in nearbyMarkers.map { it.id } }
                .minOfOrNull { it.second } ?: NEARBY_MARKER_THRESHOLD_METERS
            
            Timber.e("가장 가까운 마커까지의 거리: ${minDistance}m - 확인 필요")
            NearbyMarkerCheckResult.MarkerExists(nearbyMarkers, minDistance)
        }
    }
    
    /**
     * 마커 근접성 검사 결과를 나타내는 sealed 클래스
     */
    sealed class NearbyMarkerCheckResult {
        /**
         * 주변에 마커가 없는 상태
         */
        object NoMarkerNearby : NearbyMarkerCheckResult()
        
        /**
         * 주변에 마커가 있는 상태
         * @param nearbyMarkers 근처 마커 목록
         * @param minDistance 가장 가까운 마커까지의 거리 (미터)
         */
        data class MarkerExists(
            val nearbyMarkers: List<Marker>,
            val minDistance: Double
        ) : NearbyMarkerCheckResult()
    }

    /**
     * 마커 클릭 이벤트 처리
     */
    fun onMarkerClick(markerId: String): Boolean {
        try {
            Timber.d("마커 클릭 처리: ID=$markerId, 현재 모드: ${if(editModeManager.getCurrentMode()) "쓰기모드" else "읽기모드"}")
            
            // 마커가 존재하는지 확인
            if (markerId.isBlank()) {
                Timber.e("마커 ID가 비어있습니다")
                return true
            }
            
            // 중복 클릭 방지 로직
            val currentTime = System.currentTimeMillis()
            if (markerId == lastClickedMarkerId && 
                currentTime - lastClickTime < CLICK_DEBOUNCE_TIME) {
                Timber.d("중복 마커 클릭 무시: $markerId (${currentTime - lastClickTime}ms 내)")
                return true
            }
            
            // 클릭 정보 업데이트
            lastClickedMarkerId = markerId
            lastClickTime = currentTime
            
            // 이미 표시된 메모창이 있는지 확인 
            if (_memoDialogState.value.isVisible && _memoDialogState.value.markerId == markerId) {
                Timber.d("이미 동일한 마커의 메모창이 표시 중입니다: $markerId")
                return true
            }
            
            // 중요: 새로운 마커 클릭 시 이전 상태 완전 정리 (메모 정보 섞임 방지)
            viewModelScope.launch {
                try {
                    // 1. 이전 메모 상태 정리
                    memoManager.clearMemos()
                    memoManager.clearSelectedMarker()
                    
                    // 2. 상태 정리 완료 대기
                    delay(30)
                    
                    // 3. 임시 마커 여부 확인
                    val isTemporary = temporaryMarkerManager.isTemporaryMarker(markerId)
                    
                    // 4. 새로운 다이얼로그 상태 설정
                    _memoDialogState.value = DialogState(
                        isVisible = true, 
                        markerId = markerId,
                        isTemporary = isTemporary
                    )
                    
                    // 5. 메모 다이얼로그 표시
                    memoManager.showMemoDialog(markerId)
                    Timber.d("메모장 UI 상태 업데이트 완료: markerId=$markerId, isTemporary=$isTemporary")
                    
                    // 6. 기타 비동기 처리
                    // 쓰기 모드인 경우 타이머 재시작
                    if (editModeManager.getCurrentMode()) {
                        restartEditModeTimer()
                    }
                    
                    // 데이터 로딩 (비동기)
                    memoManager.loadMemosByMarkerId(markerId)
                    
                    // 이벤트 핸들러로 위임 (마커 선택 처리만 수행)
                    mapEventHandler.handleMarkerClick(markerId)
                    
                } catch (e: Exception) {
                    Timber.e(e, "마커 클릭 비동기 처리 중 오류 발생: ID=$markerId")
                    // 오류 발생 시 상태 초기화
                    _memoDialogState.value = DialogState()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "마커 클릭 처리 중 오류 발생: ID=$markerId")
        }
        
        return true
    }
    
    /**
     * 메모장 표시 - 지정된 마커의 메모장을 표시합니다.
     * @param markerId 메모장을 표시할 마커 ID
     * @param isTemporary 임시 마커 여부
     */
    fun showMemoDialogForMarker(markerId: String, isTemporary: Boolean = false) {
        Timber.d("메모장 표시 요청: markerId=$markerId, isTemporary=$isTemporary")
        
        // 이미 표시된 경우 무시
        if (_memoDialogState.value.markerId == markerId && _memoDialogState.value.isVisible) {
            Timber.d("이미 같은 마커에 대한 메모장이 표시되어 있음: $markerId")
            return
        }
        
        // 중요: 이전 상태 완전 초기화 (임시 마커 생성 시 이전 메모 정보 섞임 방지)
        viewModelScope.launch {
            try {
                // 1. 먼저 메모 매니저의 이전 상태 정리
                memoManager.clearMemos()
                memoManager.clearSelectedMarker()
                
                // 2. 짧은 지연으로 상태 정리 완료 보장
                delay(50)
                
                // 3. 새로운 다이얼로그 상태 설정
                _memoDialogState.value = DialogState(
                    isVisible = true,
                    markerId = markerId,
                    isTemporary = isTemporary
                )
                
                // 4. 메모 매니저에 새 마커 ID로 메모장 표시 요청
                memoManager.showMemoDialog(markerId)
                
                Timber.d("메모장 상태 초기화 및 새 마커 설정 완료: markerId=$markerId, isTemporary=$isTemporary")
                
            } catch (e: Exception) {
                Timber.e(e, "메모장 표시 중 오류 발생: markerId=$markerId")
                // 오류 발생 시 상태 초기화
                _memoDialogState.value = DialogState()
            }
        }
    }
    
    /**
     * 현재 마커가 임시 마커인지 확인합니다.
     */
    fun isCurrentMarkerTemporary(): Boolean {
        return _memoDialogState.value.isTemporary
    }
    
    /**
     * 특정 마커가 임시 마커인지 확인합니다.
     * @param markerId 마커 ID
     * @return 임시 마커 여부
     */
    fun isTemporaryMarker(markerId: String): Boolean {
        return temporaryMarkerManager.isTemporaryMarker(markerId)
    }
    
    /**
     * 메모장 다이얼로그를 닫고 임시 마커 처리를 수행합니다.
     * @param shouldSaveMarker 임시 마커를 저장할지 여부
     */
    fun onMemoDialogDismissed(shouldSaveMarker: Boolean = false) {
        val markerId = _memoDialogState.value.markerId
        val isTemporary = _memoDialogState.value.isTemporary
        
        Timber.d("메모장 닫힘 처리: markerId=$markerId, isTemporary=$isTemporary, shouldSave=$shouldSaveMarker")
        
        // 임시 마커를 저장할지 또는 삭제할지 결정
        if (isTemporary && !shouldSaveMarker && markerId != null) {
            viewModelScope.launch {
                // 임시 마커 삭제
                temporaryMarkerManager.deleteTemporaryMarker(markerId)
                Timber.d("임시 마커 삭제: $markerId")
            }
        }
        
        // 다이얼로그 상태 초기화
        _memoDialogState.value = DialogState()
    }
    
    /**
     * 메모장 상태만 초기화 (shouldShow = false)
     */
    fun resetMemoDialogState() {
        _memoDialogState.value = _memoDialogState.value.copy(isVisible = false)
    }

    /**
     * 메모 생성 - 지정된 마커에 새 메모를 추가합니다.
     * @param userId 사용자 ID
     * @param markerId 마커 ID
     * @param content 메모 내용
     * @param isTemporaryMarker 임시 마커 여부
     */
    fun createMemo(
        userId: String,
        markerId: String,
        content: String,
        isTemporaryMarker: Boolean = false
    ) {
        viewModelScope.launch {
            try {
                Timber.d("메모 생성 시작: userId=$userId, markerId=$markerId, isTemporary=$isTemporaryMarker")
                
                // 1. 임시 마커이면 먼저 영구 저장
                if (isTemporaryMarker) {
                    Timber.d("임시 마커 영구 저장 시작: $markerId")
                    
                    // 1-1. 먼저 영구 저장 요청 실행
                    temporaryMarkerManager.makeMarkerPermanent(markerId)
                    Timber.d("임시 마커 영구 저장 요청 완료: $markerId")
                    
                    // 1-2. 기본 딜레이 적용 (최소 안전 대기 시간)
                    delay(100)
                    
                    // 1-3. 이벤트로 완료 확인 시도
                    val markerSaved = withTimeoutOrNull(3000L) { // 타임아웃 시간 단축
                        temporaryMarkerManager.events
                            .filter { event -> 
                                event is TemporaryMarkerEvent.MarkerMadePermanent && 
                                event.markerId == markerId 
                            }
                            .map { true }
                            .first()
                    }
                    
                    if (markerSaved == true) {
                        Timber.d("임시 마커 영구 저장 완료 이벤트 수신 성공: $markerId")
                    } else {
                        Timber.w("임시 마커 영구 저장 완료 이벤트를 수신하지 못했습니다: $markerId")
                        // 이벤트 수신 실패시 추가 대기 시간 확보
                        delay(300)
                        Timber.d("임시 마커 영구 저장 완료를 위한 추가 대기 완료: $markerId")
                    }
                }
                
                // 2. 그 다음 메모 생성
                memoInteractor.createMemo(
                    userId = userId, 
                    markerId = markerId, 
                    content = content,
                    scope = viewModelScope,
                    onError = { e ->
                        Timber.e(e, "메모 생성 중 예외 발생: ${e.message}")
                        viewModelScope.launch {
                            // 오류 메시지에 따라 다른 실패 메시지 표시
                            val failMessage = when {
                                e.message?.contains("마커를 찾을 수 없습니다") == true -> "저장 실패. 마커가 존재하지 않습니다."
                                else -> "저장 실패. 다시 시도하세요."
                            }
                            _saveFailEvent.emit(failMessage)
                        }
                        handleError(e, "메모 생성 중 오류가 발생했습니다.")
                    }
                )
                
                Timber.d("메모 생성 및 저장 완료: markerId=$markerId")
            } catch (e: Exception) {
                Timber.e(e, "메모 생성 처리 중 오류: ${e.message}")
                
                // 오류 메시지에 따라 다른 실패 메시지 표시
                val failMessage = when {
                    e.message?.contains("마커를 찾을 수 없습니다") == true -> "저장 실패. 마커가 존재하지 않습니다."
                    else -> "저장 실패. 다시 시도하세요."
                }
                _saveFailEvent.emit(failMessage)
                
                handleError(e, "메모 생성 중 오류가 발생했습니다.")
            }
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
     * Resume 이벤트 처리 - 포그라운드 전환 시 상태를 복원합니다.
     */
    fun onResume() {
        viewModelScope.launch {
            try {
                Timber.d("onResume: 포그라운드 전환 - 상태 복원 시작")
                
                // 메모장 상태 초기화는 MemoManager에 위임
                memoManager.hideMemoDialog()
                
                // 위치 추적 시작 전 초기화
                locationTracker.initialize()
                
                // 초기화 완료 대기 (delay 대신 상태 구독)
                locationTracker.initialized.filter { it }.first()
                Timber.d("LocationTracker 초기화 완료 상태 수신")
                
                // 위치 추적 시작 및 결과 확인
                val trackingResult = locationTracker.startTracking()
                if (trackingResult.isSuccess) {
                    Timber.d("위치 추적 시작 성공")
                    
                    // 위치 업데이트를 flow를 통해 수신 (폴링 방식 제거)
                    // 첫 번째 위치 데이터를 수신하면 동기화 작업 진행
                    withTimeoutOrNull(5000L) {
                        locationTracker.currentLocation
                            .filterNotNull()
                            .first()
                        Timber.d("위치 업데이트 수신 성공")
                    }
                } else {
                    Timber.w("위치 추적 시작 실패: ${trackingResult.exceptionOrNull()?.message}")
                }
                
                // 포그라운드 전환 시 데이터 동기화 수행
                syncDataForCurrentLocation()
                
                // 편집 모드는 유지하고, 타이머만 계속 작동하게 함
                // EditModeManager는 이제 상태를 스스로 관리함
                Timber.d("포그라운드 진입 시 쓰기모드 상태 유지 - 타이머만 계속 작동")
                
                Timber.d("onResume: 상태 복원 완료")
            } catch (e: Exception) {
                Timber.e(e, "onResume 처리 중 오류 발생")
                // UI 흐름에 영향을 주지 않도록 오류는 사용자에게 표시하지 않음
            }
        }
    }
    
    /**
     * Pause 이벤트 처리 - 백그라운드 전환 시 상태를 초기화합니다.
     */
    fun onPause() {
        try {
            Timber.d("onPause: 백그라운드 전환 - 상태 초기화")
            // 메모장 관련 상태 초기화는 MemoManager에 위임
            memoManager.hideMemoDialog()
            
            // 백그라운드 진입 시 읽기 모드로 강제 전환하는 코드를 제거
            // 이제는 쓰기모드를 유지하고 타이머만 계속 작동하게 함
            Timber.d("백그라운드 진입 시 쓰기모드 유지 - 타이머만 계속 작동")
        } catch (e: Exception) {
            Timber.e(e, "onPause 처리 중 오류 발생")
            // UI 흐름에 영향을 주지 않도록 오류는 사용자에게 표시하지 않음
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
     * ViewModel이 clear될 때 호출되어 리소스를 정리
     */
    override fun onCleared() {
        try {
            Timber.d("MapViewModel onCleared 호출됨")
            
            // 1. 이벤트 핸들러 해제
            _eventHandler?.unsubscribeAll()
            _eventHandler = null
            
            // 2. 타이머 취소
            editModeManager.clearTimerAndJobs()
            
            // viewModelScope는 ViewModel이 destroy될 때 자동으로 취소됩니다.
            
            Timber.d("MapViewModel 리소스 정리 완료")
        } catch (e: Exception) {
            Timber.e(e, "MapViewModel.onCleared() 중 오류 발생")
        } finally {
            super.onCleared()
        }
        // 디바운서 정리
        cameraPositionDebouncer.cancel()
    }

    /**
     * 오류 표시 헬퍼 메소드
     */
    private fun showError(e: Throwable, message: String) {
        val errorMessage = e.message ?: message
        _mapState.value = MapState.Error(MapError.GenericError(errorMessage))
        Timber.e(e, message)
    }
    
    // 문자열 오류에 대한 overload
    private fun showError(errorMessage: String, userMessage: String) {
        _mapState.value = MapState.Error(MapError.GenericError(errorMessage))
        Timber.e(userMessage)
    }
    
    /**
     * 지도 상태 새로고침
     */
    private fun refreshMapState() {
        val currentState = _mapState.value
        if (currentState is MapState.Success) {
            // 현재 상태를 기반으로 새로운 상태 생성
            refreshMarkers()
        }
    }

    /**
     * API 호출 없이 UI만 갱신하는 메서드
     * 네비게이션 복귀 시 불필요한 API 호출을 방지하기 위해 사용
     */
    fun refreshMapUIOnly() {
        Timber.d("UI만 갱신 (API 호출 없음)")
        try {
            // 현재 상태 가져오기
            val currentState = _mapState.value
            
            // 성공 상태인 경우에만 처리
            if (currentState is MapState.Success) {
                // UI 관련 업데이트만 수행
                // 기존 마커 데이터를 그대로 사용
                _mapState.value = currentState.copy(
                    timestamp = System.currentTimeMillis(),
                    loading = false
                )
                
                Timber.d("UI 갱신 완료 (API 호출 없음)")
            }
        } catch (e: Exception) {
            Timber.e(e, "UI 갱신 중 오류 발생")
        }
    }

    /**
     * 현재 geohash와 마커 정보를 자세히 로깅합니다.
     * 문제 해결을 위한 디버깅 용도로 사용됩니다.
     */
    fun logGeohashAndMarkerInfo() {
        val currentState = _mapState.value
        val location = if (currentState is MapState.Success) currentState.currentLocation else null
        
        // 현재 위치의 geohash 계산
        val currentGeohash = location?.let {
            try {
                com.parker.hotkey.util.GeoHashUtil.encode(it.latitude, it.longitude, GeohashConstants.GEOHASH_PRECISION)
            } catch (e: Exception) {
                Timber.e(e, "geohash 계산 중 오류")
                null
            }
        }
        
        Timber.d("======= 마커 상태 진단 시작 =======")
        Timber.d("현재 위치: ${location?.latitude}, ${location?.longitude}")
        Timber.d("현재 geohash: $currentGeohash")
        
        // 현재 메모리에 있는 마커 확인
        val memoryMarkers = markerManager.markers.value
        Timber.d("메모리 마커 개수: ${memoryMarkers.size}")
        
        // geohash별 마커 분포 확인
        val markersByGeohash = memoryMarkers.groupBy { it.geohash }
        Timber.d("geohash별 마커 분포:")
        markersByGeohash.forEach { (geohash, markers) ->
            Timber.d("- $geohash: ${markers.size}개 마커")
        }
        
        // 현재 geohash에 해당하는 마커 확인
        if (!currentGeohash.isNullOrEmpty()) {
            val markersInCurrentGeohash = memoryMarkers.filter { it.geohash == currentGeohash }
            Timber.d("현재 geohash($currentGeohash)에 있는 마커: ${markersInCurrentGeohash.size}개")
            
            // 현재 geohash의 마커 상세 정보
            markersInCurrentGeohash.forEach { marker ->
                Timber.d("현재 영역 마커: id=${marker.id}, geohash=${marker.geohash}, lat=${marker.latitude}, lng=${marker.longitude}")
            }
            
            // DB에서도 확인
            viewModelScope.launch {
                try {
                    val dbMarkers = markerRepository.getMarkersSync(currentGeohash, listOf(currentGeohash))
                    Timber.d("DB 조회 결과 - geohash '$currentGeohash'에 해당하는 마커: ${dbMarkers.size}개")
                    
                    // 불일치 확인
                    if (markersInCurrentGeohash.size != dbMarkers.size) {
                        Timber.w("메모리와 DB 간 마커 개수 불일치 감지! DB: ${dbMarkers.size}, 메모리: ${markersInCurrentGeohash.size}")
                        
                        // DB에는 있지만 메모리에는 없는 마커 찾기
                        val memoryIds = markersInCurrentGeohash.map { it.id }.toSet()
                        val dbOnlyMarkers = dbMarkers.filter { it.id !in memoryIds }
                        
                        if (dbOnlyMarkers.isNotEmpty()) {
                            Timber.w("DB에는 있지만 메모리에 없는 마커: ${dbOnlyMarkers.size}개")
                            dbOnlyMarkers.forEach { marker ->
                                Timber.w("DB 전용 마커: id=${marker.id}, geohash=${marker.geohash}")
                            }
                        }
                        
                        // 메모리에는 있지만 DB에는 없는 마커 찾기
                        val dbIds = dbMarkers.map { it.id }.toSet()
                        val memoryOnlyMarkers = markersInCurrentGeohash.filter { it.id !in dbIds }
                        
                        if (memoryOnlyMarkers.isNotEmpty()) {
                            Timber.w("메모리에는 있지만 DB에 없는 마커: ${memoryOnlyMarkers.size}개")
                            memoryOnlyMarkers.forEach { marker ->
                                Timber.w("메모리 전용 마커: id=${marker.id}, geohash=${marker.geohash}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "DB 마커 확인 중 오류 발생")
                }
            }
        }
        
        Timber.d("======= 마커 상태 진단 완료 =======")
        
        // 마커 상태 진단 결과 DB와 메모리 간 불일치가 발견된 경우 마커 다시 로드
        if (!currentGeohash.isNullOrEmpty()) {
            viewModelScope.launch {
                try {
                    val dbMemoryMarkers = markerManager.markers.value.filter { it.geohash == currentGeohash }
                    val dbMarkers = markerRepository.getMarkersSync(currentGeohash, listOf(currentGeohash))
                    
                    if (dbMemoryMarkers.size != dbMarkers.size) {
                        Timber.w("마커 불일치 감지 후 마커 다시 로드 시도: geohash=$currentGeohash")
                        val neighbors = locationTracker.neighbors.value.orEmpty()
                        val currentZoom = locationTracker.getCurrentZoom() ?: MapConstants.DEFAULT_ZOOM
                        markerManager.loadMarkersInAreaOptimized(currentGeohash, neighbors, currentZoom)
                        Timber.d("마커 데이터 재로드 완료")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "마커 데이터 재로드 중 오류 발생")
                }
            }
        }
    }

    // MapViewModel 상태를 MapState로 통합
    private fun updateMapStateWithMarkerState(markerState: com.parker.hotkey.domain.model.state.MarkerState) {
        try {
            Timber.d("마커 상태 업데이트: ${markerState.markers.size}개 마커, 선택된 마커: ${markerState.selectedId}")
            
            // 에러 처리
            if (markerState.error != null) {
                showError(markerState.error, "마커 처리 중 오류가 발생했습니다")
                return
            }
            
            // 로딩 상태 처리
            if (markerState.isLoading) {
                _mapState.value = MapState.Loading
                return
            }
            
            // 삭제 진행 중 상태 처리 - 이제 임시 마커 여부를 확인해야 함
            val markerToDeleteId = _markerToDeleteId.value
            if (markerToDeleteId != null) {
                _mapState.value = MapState.Loading
                return
            }
            
            // 정상 상태 처리 - MapStateProcessor로 위임
            refreshMapState()
        } catch (e: Exception) {
            Timber.e(e, "마커 상태 업데이트 중 예외 발생")
            showError(e, "마커 상태 업데이트 중 오류가 발생했습니다")
        }
    }

    /**
     * 새 지역 데이터 로드 - 새로운 geohash 영역에 진입했을 때 호출
     * 서버에서 초기 데이터를 로드한 후 지역 마커를 표시합니다.
     */
    private fun loadNewAreaDataOld(geohash: String, neighbors: List<String>, zoomLevel: Double) {
        viewModelScope.launch {
            try {
                Timber.d("새 지역 데이터 로드 시작: $geohash")
                
                // 서버에서 지역 데이터 로드
                val success = syncDataUseCase(geohash)
                
                if (success) {
                    Timber.d("지역 데이터 로드 성공: $geohash, 이제 마커 로드")
                } else {
                    Timber.w("지역 데이터 로드 실패: $geohash")
                }
                
                // 서버 로드 성공 여부와 관계없이 로컬 DB에서 마커 로드 (기존 데이터가 있을 수 있음)
                val loadJob = coroutineScope {
                    async {
                        markerManager.loadMarkersInAreaOptimized(geohash, neighbors, zoomLevel)
                    }
                }
                
                // 비동기 작업 완료 대기
                loadJob.await()
                
                // DB와 메모리 간 마커 상태 확인 (지연 없이 즉시 실행)
                validateMarkerState(geohash, neighbors, zoomLevel)
                
            } catch (e: Exception) {
                Timber.e(e, "새 지역 데이터 로드 중 오류 발생: $geohash")
            }
        }
    }

    /**
     * 마커 상태 검증 및 필요시 재동기화
     */
    private suspend fun validateMarkerState(geohash: String, neighbors: List<String>, zoomLevel: Double) {
        try {
            val memoryMarkers = markerManager.markers.value.filter { it.geohash == geohash }
            val dbMarkers = markerRepository.getMarkersSync(geohash, listOf(geohash))
            
            if (memoryMarkers.size != dbMarkers.size) {
                Timber.w("마커 불일치 감지: DB=${dbMarkers.size}개, 메모리=${memoryMarkers.size}개 (geohash=$geohash)")
                Timber.d("마커 상태 재동기화 시작")
                
                // 마커 다시 로드
                markerManager.loadMarkersInAreaOptimized(geohash, neighbors, zoomLevel)
                Timber.d("마커 상태 재동기화 완료")
            }
        } catch (e: Exception) {
            Timber.e(e, "마커 상태 검증 중 오류 발생")
        }
    }

    /**
     * 네트워크 상태 모니터링 설정
     */
    private fun setupNetworkMonitoring() {
        // 네트워크 모니터링 작업 시작
        viewModelScope.launch {
            try {
                Timber.d("네트워크 모니터링 시작")
                connectionStateMonitor.observeNetworkState()
                    .catch { e -> 
                        Timber.e(e, "네트워크 모니터링 중 오류 발생")
                    }
                    .collect { isConnected ->
                        Timber.d("네트워크 상태 변경: $isConnected")
                        
                        if (isConnected) {
                            // 네트워크 연결이 복구되면 데이터 동기화 수행
                            try {
                                syncDataForCurrentLocation()
                            } catch (e: Exception) {
                                Timber.e(e, "네트워크 복구 후 데이터 동기화 중 오류 발생")
                            }
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "네트워크 상태 모니터링 작업 오류 발생")
            }
        }
    }

    /**
     * 임시 마커 삭제 메서드
     */
    private fun deleteTemporaryMarker(markerId: String) {
        viewModelScope.launch {
            try {
                Timber.d("저장되지 않은 임시 마커 삭제: $markerId")
                temporaryMarkerManager.deleteTemporaryMarker(markerId)
            } catch (e: Exception) {
                Timber.e(e, "임시 마커 삭제 중 오류 발생: $markerId")
            }
        }
    }
    
    /**
     * 현재 사용자 ID를 반환합니다.
     * @return 사용자 ID 또는 null
     */
    suspend fun getUserId(): String? {
        return try {
            authRepository.getUserId()
        } catch (e: Exception) {
            Timber.e(e, "사용자 ID 가져오기 실패")
            null
        }
    }
    
    /**
     * DB에서 특정 geohash에 대한 마커 개수를 가져와 로그에 출력합니다.
     * 디버깅 용도로 사용됩니다.
     */
    fun getMarkerCountFromDB(geohash: String) {
        if (geohash.isEmpty()) {
            Timber.w("getMarkerCountFromDB: geohash가 비어있습니다")
            return
        }
        
        viewModelScope.launch {
            try {
                val markers = markerRepository.getMarkersSync(geohash, listOf(geohash))
                Timber.d("DB 조회 결과 - geohash '$geohash'에 해당하는 마커: ${markers.size}개")
                
                if (markers.isNotEmpty()) {
                    Timber.d("DB 마커 샘플:")
                    markers.take(3).forEach { marker ->
                        Timber.d("- ID: ${marker.id}, geohash: ${marker.geohash}, 위치: (${marker.latitude}, ${marker.longitude})")
                    }
                }
                
                // 메모리 상태의 마커 목록과 비교
                val memoryMarkers = markerManager.markers.value
                val memoryMarkersInGeohash = memoryMarkers.filter { it.geohash == geohash }
                
                Timber.d("메모리 상태 비교 - 전체 마커: ${memoryMarkers.size}개, geohash '$geohash'에 해당하는 마커: ${memoryMarkersInGeohash.size}개")
                
                // 불일치 있는지 확인
                if (markers.size != memoryMarkersInGeohash.size) {
                    Timber.w("DB와 메모리 상태 불일치 감지! DB: ${markers.size}개, 메모리: ${memoryMarkersInGeohash.size}개")
                }
            } catch (e: Exception) {
                Timber.e(e, "DB에서 마커 개수 조회 중 오류 발생")
            }
        }
    }

    /**
     * 프래그먼트에서 생성한 EventHandler를 설정합니다.
     * onCleared() 시 자동으로 해제됩니다.
     */
    fun setEventHandler(eventHandler: EventHandler<Any>) {
        _eventHandler = eventHandler
        Timber.d("이벤트 핸들러 설정됨")
    }

    /**
     * 마커 풀 상태를 로깅합니다.
     */
    fun logMarkerPoolStatus() {
        markerViewModel.logMarkerPoolStatus()
    }
    
    /**
     * 마커를 강제로 새로고침합니다.
     * 화면 전환 시 마커 깜박임을 방지하기 위해 사용합니다.
     */
    fun forceRefreshMarkers() {
        viewModelScope.launch {
            try {
                Timber.d("마커 강제 새로고침 시작")
                
                // 현재 위치 정보 기반으로 마커 즉시 새로고침
                val currentGeohash = locationTracker.currentGeohash.value
                val neighbors = locationTracker.neighbors.value.orEmpty()
                
                if (currentGeohash != null) {
                    // 편집 모드 상태 확인
                    val isEditMode = editModeManager.getCurrentMode()
                    
                    // 마커 새로고침 먼저 수행 (순서 중요)
                    markerViewModel.refreshMarkers()
                    
                    // 즉시 마커 가시성 업데이트 (지연 없이)
                    markerViewModel.updateMarkerVisibility(
                        currentGeohash,
                        neighbors,
                        isEditMode
                    )
                } else {
                    // 현재 위치가 없더라도 마커 새로고침은 수행
                    markerViewModel.refreshMarkers()
                }
                
                Timber.d("마커 강제 새로고침 완료")
            } catch (e: Exception) {
                Timber.e(e, "마커 강제 새로고침 중 오류 발생")
            }
        }
    }

    /**
     * 지도 초기화 및 마커 로딩을 수행합니다.
     * 
     * @param geohash 중심 지역의 geohash
     * @param neighbors 인접 지역의 geohash 목록
     */
    fun loadMarkersInArea(geohash: String, neighbors: List<String>) {
        viewModelScope.launch {
            try {
                Timber.d("마커 로딩 시작 - geohash: $geohash, 이웃 지역: ${neighbors.size}개")
                
                // 현재 줌 레벨 가져오기 (기본값 사용)
                val currentZoom = locationTracker.getCurrentZoom() ?: MapConstants.DEFAULT_ZOOM
                
                // MarkerLoadingCoordinator를 사용하여 마커 로딩
                markerLoadingCoordinator.loadMarkersForNewArea(geohash, neighbors, currentZoom)
                
                // 기존 마커뷰모델 호출 대신 코디네이터 사용
                // markerViewModel.loadMarkersInArea(geohash, neighbors, currentZoom)
            } catch (e: Exception) {
                Timber.e(e, "마커 로딩 중 오류 발생")
            }
        }
    }

    /**
     * 마커를 다시 로드합니다.
     * MapFragment에서 updateMarkersAsync 메서드에서 호출됩니다.
     * 
     * @param geohash 중심 지역의 geohash
     * @param neighbors 인접 지역의 geohash 목록
     */
    fun reloadMarkersInArea(geohash: String, neighbors: List<String>) {
        viewModelScope.launch {
            try {
                Timber.d("마커 재로딩 시작 - geohash: $geohash, 이웃 지역: ${neighbors.size}개")
                
                // 현재 줌 레벨 가져오기 (기본값 사용)
                val currentZoom = locationTracker.getCurrentZoom() ?: MapConstants.DEFAULT_ZOOM
                
                // 로딩 상태로 업데이트
                _mapState.value = MapState.Loading
                
                // MarkerLoadingCoordinator를 사용하여 마커 로딩 (강제 새로고침)
                val success = markerLoadingCoordinator.loadMarkers(geohash, neighbors, currentZoom, forceRefresh = true)
                
                if (success) {
                    Timber.d("마커 재로딩 완료")
                    // 성공 상태로 업데이트
                    val currentState = _mapState.value
                    if (currentState is MapState.Success) {
                        _mapState.value = currentState
                    } else {
                        _mapState.value = MapState.Success()
                    }
                } else {
                    Timber.e("마커 재로딩 실패")
                    _errorState.value = MapError.MarkerLoadingError("마커 로딩 실패")
                    
                    // 에러 핸들링 후 현재 상태 유지를 위해 마지막 알려진 성공 상태로 복원
                    if (_mapState.value is MapState.Loading) {
                        _mapState.value = MapState.Success()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "마커 재로딩 중 오류 발생")
                _errorState.value = MapError.MarkerLoadingError(e.message ?: "알 수 없는 오류")
                
                // 오류 발생 시 마지막 알려진 성공 상태로 복원
                if (_mapState.value is MapState.Loading) {
                    _mapState.value = MapState.Success()
                }
            }
        }
    }
    
    /**
     * 앱이 포그라운드로 전환될 때 마커를 새로고침합니다.
     */
    fun refreshMarkersForForeground() {
        viewModelScope.launch {
            try {
                Timber.d("포그라운드 전환 시 마커 새로고침 시작")
                
                val currentGeohash = locationTracker.currentGeohash.value
                val neighbors = locationTracker.neighbors.value.orEmpty()
                
                if (currentGeohash != null) {
                    val currentZoom = locationTracker.getCurrentZoom() ?: MapConstants.DEFAULT_ZOOM
                    
                    // MarkerLoadingCoordinator를 사용하여 포그라운드 새로고침 수행
                    markerLoadingCoordinator.foregroundRefreshMarkers(currentGeohash, neighbors, currentZoom)
                    
                    Timber.d("포그라운드 전환 마커 새로고침 완료")
                } else {
                    Timber.w("포그라운드 전환 시 현재 위치 정보 없음, 마커 새로고침 건너뜀")
                }
            } catch (e: Exception) {
                Timber.e(e, "포그라운드 전환 마커 새로고침 중 오류 발생")
            }
        }
    }

    /**
     * 앱 최초 실행 시 초기화 로직
     * 사용자 위치 기반 데이터 로드, 초기 마커 설정 등의 작업을 수행합니다.
     */
    private fun initializeForFreshInstall() {
        Timber.d("앱 최초 설치 - 초기화 로직 시작")
        viewModelScope.launch {
            try {
                _mapState.value = MapState.Loading
                
                // 1. 위치 권한 확인 및 위치 추적 초기화
                val permissionGranted = locationManager.hasLocationPermission()
                if (!permissionGranted) {
                    Timber.w("위치 권한 없음, 기본 데이터만 로드합니다")
                    // 위치 권한이 없어도 앱 초기화는 계속 진행
                }
                
                // 2. 위치 추적기 초기화 및 초기화 완료 대기
                locationTracker.initialize()
                
                // 3. 위치 정보 확인하여 데이터 로드
                val currentGeohash = locationTracker.currentGeohash.value
                val neighbors = locationTracker.neighbors.value
                val currentZoom = locationTracker.getCurrentZoom() ?: MapConstants.DEFAULT_ZOOM
                
                if (currentGeohash != null) {
                    Timber.d("최초 실행 - 현재 위치 geohash: $currentGeohash")
                    
                    // 4. 네트워크 상태 확인
                    val isConnected = connectionStateMonitor.isConnected()
                    
                    if (isConnected) {
                        // 중요: 초기 데이터 로드를 마커 로딩과 분리해서 중복 호출 방지
                        // 5. 서버에서 초기 데이터 로드 대신 markerLoadingCoordinator를 통해서만 작업 처리
                        Timber.d("초기 데이터 로드는 markerLoadingCoordinator를 통해서만 처리합니다")
                        
                        // 방문한 지역 기록 먼저 추가하여 subscribeToLocationEvents에서 중복 호출 방지
                        visitedGeohashes.add(currentGeohash)
                    } else {
                        Timber.w("네트워크 연결 없음, 로컬 데이터만 사용")
                    }
                    
                    // 6. 마커 로딩 - API 호출도 함께 처리됨
                    markerLoadingCoordinator.initialLoadMarkers(currentGeohash, neighbors, currentZoom)
                } else {
                    Timber.w("위치 정보를 가져올 수 없음, 기본 설정으로 진행")
                }
                
                // 상태 업데이트
                _mapState.value = MapState.Success()
                
                // 8. 최초 실행 완료 처리
                Timber.d("앱 최초 실행 초기화 완료, 상태 업데이트")
                appStateManager.completeFirstLaunch()
                isFirstLoad = false
                
            } catch (e: Exception) {
                Timber.e(e, "앱 최초 실행 초기화 중 오류 발생")
                handleError(e, "초기화 중 오류가 발생했습니다")
                
                // 오류가 발생해도 최초 실행은 완료된 것으로 처리
                // 다음 실행 시 정상 로직을 통해 복구 기회 제공
                appStateManager.completeFirstLaunch()
            }
        }
    }

    /**
     * 네비게이션 복귀 시 로컬 데이터만 사용하여 마커를 로드합니다.
     * API 호출을 하지 않고 로컬 데이터베이스에서만 마커를 가져와 불필요한 네트워크 요청을 방지합니다.
     */
    fun loadLocalDataOnly() {
        viewModelScope.launch {
            try {
                Timber.tag("API_FLOW").d("[NAV_RETURN] 네비게이션 복귀 감지 - 로컬 데이터만 사용")
                
                // 현재 위치에 기반한 정보 가져오기
                val currentGeohash = locationTracker.currentGeohash.value
                if (currentGeohash != null) {
                    val neighbors = locationTracker.neighbors.value.orEmpty()
                    val zoom = locationTracker.getCurrentZoom() ?: MapConstants.DEFAULT_ZOOM
                    
                    Timber.tag("API_FLOW").d("[NAV_RETURN] 로컬 데이터베이스에서 마커 로드 시작: $currentGeohash")
                    
                    // 로딩 상태로 변경
                    _mapState.value = MapState.Loading
                    
                    try {
                        // 로컬 데이터베이스에서 마커 가져오기 (getMarkersSync를 사용하여 동기적으로 처리)
                        val localMarkers = markerRepository.getMarkersSync(
                            currentGeohash,
                            neighbors,
                            MarkerQueryOptions(
                                precision = 6,
                                limit = 500,
                                zoom = zoom
                            )
                        )
                        
                        // 마커 개수 로깅
                        Timber.tag("API_FLOW").d("[NAV_RETURN] 로컬 데이터베이스에서 ${localMarkers.size}개 마커 로드됨")
                        
                        // 마커 매니저에 마커 업데이트
                        markerManager.updateMarkers(localMarkers)
                        
                        // 성공 상태로 변경
                        _mapState.value = MapState.Success(zoomLevel = zoom)
                        
                        // 필요한 경우 마커 UI 새로고침
                        markerManager.refreshMarkersUI()
                    } catch (e: Exception) {
                        // 로컬 데이터 로드 중 오류 발생시에도 API를 호출하지 않음
                        Timber.tag("API_FLOW").e(e, "[NAV_RETURN] 로컬 데이터 로드 중 오류 발생")
                        
                        // 이전에 로드된 마커 사용 (아무것도 하지 않음)
                        // 대신 UI 상태만 성공으로 변경하여 로딩 인디케이터 제거
                        _mapState.value = MapState.Success(zoomLevel = zoom)
                        
                        // 최대한 UI는 갱신
                        markerManager.refreshMarkersUI()
                    }
                } else {
                    Timber.tag("API_FLOW").w("[NAV_RETURN] 현재 위치의 geohash를 가져올 수 없음")
                    
                    // 위치를 가져올 수 없는 경우에도 로컬 데이터만 사용 시도
                    // refreshMarkers 호출 대신 기존 마커 표시만 시도
                    markerManager.refreshMarkersUI()
                    _mapState.value = MapState.Success()
                }
            } catch (e: Exception) {
                Timber.tag("API_FLOW").e(e, "[NAV_RETURN] 로컬 데이터 로드 중 오류 발생")
                
                // 어떤 오류가 발생하더라도 API 호출하지 않음
                // 단순히 UI 상태만 성공으로 설정하여 로딩 표시 제거
                _mapState.value = MapState.Success()
            }
        }
    }

    /**
     * 포그라운드 전환 처리 - MapFragment에서 호출
     * @param needsRefresh 데이터 새로고침 필요 여부
     */
    fun handleForegroundTransition(needsRefresh: Boolean) {
        viewModelScope.launch {
            try {
                Timber.d("MapViewModel: 포그라운드 전환 처리, 새로고침 필요: $needsRefresh")
                
                // 메모장 상태 초기화는 MemoManager에 위임
                memoManager.hideMemoDialog()
                
                if (needsRefresh) {
                    // 위치 추적 시작 전 초기화
                    locationTracker.initialize()
                    
                    // 위치 업데이트를 flow를 통해 수신
                    withTimeoutOrNull(3000L) {
                        locationTracker.currentLocation
                            .filterNotNull()
                            .first()
                        Timber.d("위치 업데이트 수신 성공")
                    }
                    
                    // 포그라운드 전환 시 데이터 동기화 수행
                    syncDataForCurrentLocation()
                }
                
                // EditModeManager에 포그라운드 전환 알림
                editModeManager.onAppForeground()
                
                Timber.d("포그라운드 전환 처리 완료")
            } catch (e: Exception) {
                Timber.e(e, "포그라운드 전환 처리 중 오류 발생")
            }
        }
    }
} 