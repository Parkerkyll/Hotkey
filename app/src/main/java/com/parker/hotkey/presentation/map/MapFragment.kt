package com.parker.hotkey.presentation.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraAnimation
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.LocationTrackingMode
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.overlay.CircleOverlay
import com.naver.maps.map.overlay.InfoWindow
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.util.FusedLocationSource
import com.parker.hotkey.MainActivity
import com.parker.hotkey.R
import com.parker.hotkey.domain.manager.EditModeEvent
import com.parker.hotkey.domain.manager.MemoManager
import com.parker.hotkey.domain.model.Memo
import com.parker.hotkey.presentation.map.markers.MarkerUIDelegate
import com.parker.hotkey.domain.constants.MapConstants.DEFAULT_ZOOM
import com.parker.hotkey.domain.constants.MapConstants.LOCATION_PERMISSION_REQUEST_CODE
import com.parker.hotkey.domain.constants.MapConstants.MAP_PADDING_DP
import com.parker.hotkey.domain.constants.GeohashConstants
import com.parker.hotkey.domain.manager.MarkerEvent
import com.parker.hotkey.presentation.memo.MemoListDialog
import com.parker.hotkey.presentation.memo.MemoViewModel
import com.parker.hotkey.presentation.state.MapState
import com.parker.hotkey.presentation.map.MapError
import com.parker.hotkey.util.GeoHashUtil
import com.parker.hotkey.util.dp2px
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import com.parker.hotkey.presentation.map.controllers.LocationPermissionController
import com.parker.hotkey.presentation.map.controllers.MapUIController
import com.parker.hotkey.domain.util.EventHandler
import com.parker.hotkey.data.remote.sync.util.RetryUtil
import androidx.lifecycle.DefaultLifecycleObserver
import com.parker.hotkey.presentation.map.markers.MarkerEventHandlerImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import com.parker.hotkey.util.MemoryWatchdog
import by.kirich1409.viewbindingdelegate.viewBinding
import com.parker.hotkey.databinding.FragmentMapBinding
import com.parker.hotkey.util.LifecycleAware
import android.os.Handler
import android.os.Looper
import com.parker.hotkey.HotKeyApplication

@AndroidEntryPoint
class MapFragment : Fragment(R.layout.fragment_map), OnMapReadyCallback {
    
    // ViewBinding 프로퍼티 델리게이트 사용
    private val binding by viewBinding(FragmentMapBinding::bind)
    
    // 뷰 생명주기와 연동된 코루틴 스코프
    private val viewLifecycleScope by lazy { viewLifecycleOwner.lifecycleScope }
    
    // 프래그먼트 생명주기 전체와 연동된 코루틴 스코프
    private val fragmentScope by lazy { lifecycleScope }
    
    private var naverMap: NaverMap? = null
    internal val viewModel: MapViewModel by viewModels()
    private var progressBar: ProgressBar? = null
    private var modeText: TextView? = null
    private var menuIcon: ImageView? = null
    private var modeSwitch: SwitchMaterial? = null
    private var editModeTimer: TextView? = null
    private val markers = mutableMapOf<String, Marker>()
    private var lastMarkerCreationTime = 0L
    private var cameraUpdateJob: Job? = null
    
    // 위치 추적을 위한 변수들
    private lateinit var locationSource: FusedLocationSource
    
    // 위치 권한 요청을 위한 launcher
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                // 위치 권한이 허용됨 - LocationViewModel에 권한 결과 전달
                locationViewModel.handlePermissionResult(
                    LOCATION_PERMISSION_REQUEST_CODE,
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    intArrayOf(
                        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) 
                            PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED,
                        if (permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
                            PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
                    )
                )
                checkLocationPermissionAndEnableTracking()
                
                // 위치 권한 부여 후 알림 권한 확인 및 요청
                checkAndRequestNotificationPermission()
            }
            else -> {
                // 위치 권한이 거부됨
                showLocationPermissionDeniedMessage()
            }
        }
    }
    
    // 알림 권한 요청을 위한 launcher
    private val notificationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Timber.d("알림 권한이 허용됨")
            // 알림 권한이 허용된 경우 필요한 처리
        } else {
            Timber.d("알림 권한이 거부됨")
            // 알림 권한이 거부된 경우 필요한 처리
            showSnackbar("알림 권한이 거부되어 알림을 받을 수 없습니다. 설정에서 권한을 허용해주세요.")
        }
    }
    
    // requestLocationPermissions 변수 추가
    private val requestLocationPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                // 위치 권한이 허용됨
                Timber.d("위치 권한 승인됨")
                locationViewModel.checkLocationPermission()
                
                // 알림 권한 확인 및 요청
                checkAndRequestNotificationPermission()
            }
            else -> {
                // 위치 권한이 거부됨
                Timber.d("위치 권한 거부됨")
                showLocationPermissionDeniedMessage()
            }
        }
    }
    
    private lateinit var infoWindow: InfoWindow
    
    @Inject
    lateinit var markerUIDelegate: MarkerUIDelegate
    
    @Inject
    lateinit var mapConfigDelegate: MapConfigDelegate
    
    private var currentGeohashCircle: CircleOverlay? = null
    private var currentLocationGeohash: String? = null
    private var currentLocationNeighbors: List<String> = emptyList()
    private var currentUserLocation: LatLng? = null  // 현재 위치 저장용 변수 추가
    
    private var lastMessageTime = 0L
    
    // 편집 모드 변경 리스너 - 이제 UI 업데이트는 EditModeManager에서 처리함
    private val editModeChangeListener: (Boolean) -> Unit = { isEditMode ->
        try {
            // 편집 모드 변경 이벤트 수신 로그
            Timber.d("편집 모드 변경 이벤트 수신됨 (Fragment): isEditMode=$isEditMode")
            
            // 즉시 처리할 필요가 있는 기능적 업데이트
            updateMarkersVisibility()
            
            // UI 업데이트는 뷰가 준비된 경우에만 수행
            if (view != null && isAdded && !isDetached) {
                // UI 상태 업데이트를 위한 별도 코루틴 실행
                // 이렇게 하면 UI 스레드에서 안전하게 실행됨
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                    try {
                        // 약간의 지연을 두어 다른 코루틴과의 경합 상태 방지
                        delay(50)
                        
                        // 정확한 상태 가져오기
                        val currentMode = viewModel.editModeManager.getCurrentMode()
                        Timber.d("최종 UI 상태 업데이트 전 확인: 현재 상태=$currentMode, 요청 상태=$isEditMode")

                        // UI를 현재 상태에 맞게 강제 업데이트
                        if (isAdded && !isDetached) {
                            mapUIController.updateEditModeUI(currentMode)
                            Timber.d("편집 모드 UI 강제 업데이트 완료: currentMode=$currentMode")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "UI 상태 업데이트 중 오류 발생")
                    }
                }
            } else {
                Timber.d("Fragment가 활성 상태가 아니어서 UI 업데이트 무시")
            }
        } catch (e: Exception) {
            Timber.e(e, "편집 모드 변경 리스너 실행 중 오류 발생")
        }
    }
    
    @Inject
    lateinit var memoManager: MemoManager
    
    private val memoViewModel: MemoViewModel by viewModels()
    
    // LocationViewModel 주입
    private val locationViewModel: LocationViewModel by viewModels()
    
    // 약한 참조로 변경
    private var currentMemoDialogRef: WeakReference<MemoListDialog>? = null
    private val currentMemoDialog: MemoListDialog?
        get() = currentMemoDialogRef?.get()
    
    private val fragmentJobs = mutableListOf<Job>()
    
    @Inject
    lateinit var locationPermissionController: LocationPermissionController
    
    @Inject
    lateinit var markerEventHandler: MarkerEventHandlerImpl
    
    @Inject
    lateinit var mapUIController: MapUIController
    
    // MemoryWatchdog 의존성 주입
    @Inject
    lateinit var memoryWatchdog: MemoryWatchdog
    
    @Inject
    lateinit var connectionStateMonitor: com.parker.hotkey.data.remote.network.ConnectionStateMonitor
    
    @Inject
    lateinit var sharedPrefsManager: com.parker.hotkey.util.SharedPrefsManager
    
    @Inject
    lateinit var userPreferencesManager: com.parker.hotkey.data.manager.UserPreferencesManager
    
    // 생명주기 인식 컴포넌트 목록
    private val lifecycleAwareComponents = mutableListOf<LifecycleAware>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationSource = FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE)
        // EditModeManager 리스너 등록
        viewModel.editModeManager.addOnModeChangeListener(editModeChangeListener)
        
        // LocationViewModel에 Fragment 연결
        locationViewModel.attachFragment(this)
        
        // 위치 권한 컨트롤러 초기화 - fragment가 추가된 상태이고 유효한지 확인
        try {
            if (isAdded && context != null) {
                locationPermissionController.init(this, locationSource)
                
                locationPermissionController.setCallbacks(
                    onPermissionGranted = {
                        Timber.d("위치 권한 승인됨 - 위치 추적 활성화")
                        mapConfigDelegate.enableLocationTracking()
                        locationViewModel.checkLocationPermission()
                    },
                    onPermissionDenied = {
                        Timber.d("위치 권한 거부됨 - 위치 추적 비활성화")
                        mapConfigDelegate.disableLocationTracking()
                        showError("위치 권한이 필요합니다.")
                    },
                    showError = { message ->
                        Timber.d("위치 오류 발생: $message")
                        showError(message)
                    },
                    onFirstLocationUpdate = { location ->
                        Timber.d("첫 위치 확인: lat=${location.latitude}, lng=${location.longitude}")
                        // 생명주기 확인 로직 추가
                        if (view != null && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            try {
                                // 위치를 LatLng 형식으로 변환
                                val latLng = LatLng(location.latitude, location.longitude)
                                // 첫 위치에 맞게 카메라 위치 조정
                                locationViewModel.updateLocation(latLng, DEFAULT_ZOOM)
                            } catch (e: Exception) {
                                Timber.e(e, "첫 위치 업데이트 처리 중 오류 발생")
                            }
                        } else {
                            Timber.d("Fragment가 활성 상태가 아니므로 위치 업데이트 무시")
                        }
                    }
                )
            } else {
                Timber.e("Fragment가 아직 준비되지 않아 위치 권한 컨트롤러를 초기화할 수 없습니다")
            }
        } catch (e: Exception) {
            Timber.e(e, "위치 권한 컨트롤러 초기화 중 오류 발생")
        }
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 약한 참조 패턴을 적용한 싱글톤 클래스에 참조 설정
        setupWeakReferences()
        
        // 생명주기 인식 컴포넌트 등록
        registerLifecycleAwareComponents()
        
        // MarkerUIDelegate에 생명주기 스코프 전달
        markerUIDelegate.setLifecycleScope(viewLifecycleOwner.lifecycleScope)
        
        // MarkerUIDelegate에 MapView 설정
        markerUIDelegate.setMapView(binding.mapView)
        
        try {
            Timber.d("MapFragment onViewCreated 시작")
            
            // 네이버 지도 SDK 지연 초기화 요청 (필요한 시점에만 초기화)
            (requireContext().applicationContext as HotKeyApplication).initializeNaverMapSdkIfNeeded()
            Timber.d("네이버 지도 SDK 지연 초기화 요청 완료")
            
            // 지도 뷰 초기화
            binding.mapView.onCreate(savedInstanceState)
            binding.mapView.getMapAsync(this)
            
            // 마커 관련 설정 초기화
            markerUIDelegate.removeAllMarkers()
            
            // 순서 변경: 먼저 위치 권한 및 서비스 확인 
            // LocationViewModel 초기화 전에 위치 권한 확인
            locationPermissionController.init(this, locationSource)
            
            // 위치 권한 및 서비스 확인 (추가 및 순서 변경)
            checkLocationPermissionAndServices()
            
            // 권한 확인 후 LocationViewModel 초기화
            locationViewModel.initialize()
            observeLocationState()
            
            // MapUIController 주입 및 초기화
            mapUIController = MapUIController(requireContext())
            mapUIController.init(view) { isChecked ->
                try {
                    // 스위치 상태 변경 로그 
                    Timber.d("스위치 상태 변경됨 (Fragment 콜백): ${if(isChecked) "쓰기" else "읽기"}모드로 전환 요청")
                    
                    // 현재 시스템 상태 확인
                    val currentMode = viewModel.editModeManager.getCurrentMode()
                    val currentModeName = if(currentMode) "쓰기" else "읽기"
                    val requestedModeName = if(isChecked) "쓰기" else "읽기"
                    
                    // 디버그 정보 로깅
                    Timber.d("모드 전환 분석: 현재=$currentModeName, 요청=$requestedModeName, 일치=${currentMode == isChecked}")
                    
                    // 중복 요청 확인
                    if (currentMode != isChecked) {
                        Timber.d("모드 전환 실행: $currentModeName -> $requestedModeName")
                        
                        // 사용자 직접 모드 변경 시간 기록 (상태 보호를 위해)
                        lastUserModeChangeTime = System.currentTimeMillis()
                        Timber.d("사용자 직접 모드 변경 시간 기록: $lastUserModeChangeTime")
                        
                        // EditModeManager에 직접 모드 설정 요청 - 사용자 액션임을 표시
                        viewModel.editModeManager.setEditMode(isChecked, true, true)
                        
                        // UI 지연 업데이트를 방지하기 위해 즉시 스위치 상태 업데이트
                        // 참고: EditModeManager도 UI를 업데이트하지만 추가 안전장치로 구현
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (isAdded && !isDetached) {
                                mapUIController.updateEditModeUI(isChecked)
                            }
                        }, 100) // 짧은 지연 후 스위치 상태 재확인
                    } else {
                        Timber.d("이미 $requestedModeName 모드 상태 - 불필요한 전환 무시")
                        
                        // 이미 같은 상태이지만 UI 동기화를 위해 강제 업데이트
                        mapUIController.updateEditModeUI(isChecked)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "모드 전환 중 오류 발생")
                    
                    // 오류 발생 시 UI를 현재 상태로 강제 업데이트
                    val currentMode = viewModel.editModeManager.getCurrentMode()
                    mapUIController.updateEditModeUI(currentMode)
                }
            }
            
            // 메뉴 아이콘 클릭 리스너 직접 설정 (MapUIController의 리스너와 충돌할 수 있음)
            val menuIcon = view.findViewById<ImageView>(R.id.menu_icon)
            menuIcon.setOnClickListener {
                Timber.d("메뉴 아이콘 클릭 - Fragment에서 직접 처리")
                try {
                    // MainActivity의 openDrawer 메서드 직접 호출
                    (activity as? MainActivity)?.openDrawer()
                    Timber.d("MainActivity.openDrawer() 메서드 호출 성공")
                } catch (e: Exception) {
                    Timber.e(e, "메뉴 아이콘 클릭 처리 중 오류 발생")
                }
            }
            
            // 초기 모드 설정
            viewModel.editModeManager.setEditMode(false, true, false)
            
            // UI 컴포넌트 참조 가져오기
            val uiComponents = mapUIController.getMapUIComponents()
            modeText = uiComponents.get("modeText") as TextView
            modeSwitch = uiComponents.get("modeSwitch") as SwitchMaterial
            editModeTimer = uiComponents.get("editModeTimer") as TextView
            val modeBar = uiComponents.get("modeBar") as CardView
            
            // EditModeManager에 UI 컴포넌트 전달
            if (modeText != null && modeSwitch != null) {
                // 임시 지역 변수에 할당하여 스마트 캐스트 문제 해결
                val nonNullModeText = modeText!!
                val nonNullModeSwitch = modeSwitch!!
                val nonNullModeBar = modeBar
                
                viewModel.editModeManager.updateUIComponents(
                    nonNullModeText,
                    nonNullModeSwitch,
                    nonNullModeBar,
                    editModeTimer
                )
            }
            
            // 마커, 메모리스트 등의 상태를 관찰합니다
            setupObservers()
            
            // 마커 상태 진단 로그 출력
            viewModel.logGeohashAndMarkerInfo()
            
            Timber.d("MapFragment onViewCreated 완료")
        } catch (e: Exception) {
            Timber.e(e, "MapFragment onViewCreated 중 오류 발생")
        }
    }
    
    /**
     * 생명주기 인식 컴포넌트들을 등록합니다.
     */
    private fun registerLifecycleAwareComponents() {
        // 기존 목록 초기화
        lifecycleAwareComponents.clear()
        
        // 생명주기 인식 컴포넌트 추가
        lifecycleAwareComponents.add(markerUIDelegate)
        
        // mapUIController를 생명주기 인식 컴포넌트로 추가
        lifecycleAwareComponents.add(mapUIController as LifecycleAware)
        
        // LocationPermissionController를 생명주기 인식 컴포넌트로 추가
        lifecycleAwareComponents.add(locationPermissionController as LifecycleAware)
        
        Timber.d("${lifecycleAwareComponents.size}개의 생명주기 인식 컴포넌트가 등록됨")
    }
    
    /**
     * 약한 참조 패턴을 적용한 싱글톤 클래스에 Fragment 참조 설정
     */
    private fun setupWeakReferences() {
        Timber.d("싱글톤 클래스에 약한 참조 설정")
        
        // MemoryWatchdog에 Fragment 참조 및 콜백 설정
        memoryWatchdog.setFragment(this)
        memoryWatchdog.setLowMemoryCallback { isAggressive ->
            if (isAggressive) {
                Timber.w("심각한 메모리 부족 상태 감지 - 공격적 메모리 정리 수행")
                cleanupMemoryAggressively()
            } else {
                Timber.w("메모리 부족 상태 감지 - 일반 메모리 정리 수행")
                cleanupMemoryNormal()
            }
        }
        
        // ConnectionStateMonitor에 Fragment 참조 및 콜백 설정
        connectionStateMonitor.setFragment(this)
        connectionStateMonitor.setConnectionChangedCallback { isConnected ->
            viewLifecycleScope.launch {
                updateNetworkStatus(isConnected)
            }
        }
        
        // 간단한 네트워크 리스너 설정
        connectionStateMonitor.setupNetworkListener()
        
        // UserPreferencesManager에 Fragment 참조 설정
        userPreferencesManager.setFragment(this)
        
        // SharedPrefsManager 콜백 설정 - 설정 변경 시 UI 업데이트
        sharedPrefsManager.registerPreferenceChangedCallback("map_style") {
            viewLifecycleScope.launch {
                updateMapStyle()
            }
        }
    }
    
    /**
     * 네트워크 상태 변경 시 UI 업데이트
     */
    private fun updateNetworkStatus(isConnected: Boolean) {
        if (view == null || !isAdded) return
        
        if (!isConnected) {
            showSnackbar("네트워크 연결이 끊겼습니다. 일부 기능이 제한될 수 있습니다.")
        }
    }
    
    /**
     * 맵 스타일 업데이트
     */
    private fun updateMapStyle() {
        naverMap?.let { _ ->
            // 맵 스타일 업데이트 로직
            Timber.d("맵 스타일 업데이트")
        }
    }
    
    /**
     * 심각한 메모리 부족 상황에서 공격적 메모리 정리
     */
    private fun cleanupMemoryAggressively() {
        Timber.d("공격적 메모리 정리 수행")
        // 캐시 정리, 불필요한 뷰 해제 등
    }
    
    /**
     * 일반적인 메모리 부족 상황에서 메모리 정리
     */
    private fun cleanupMemoryNormal() {
        Timber.d("일반 메모리 정리 수행")
        // 최소한의 리소스 정리
    }
    
    private fun initializeMapView(savedInstanceState: Bundle?) {
        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)
    }
    
    private fun setupEditModeButtons() {
        // 이 메서드는 더 이상 필요하지 않음 - MapUIController에서 처리
    }
    
    /**
     * 마커 상태 및 이벤트 관찰 설정
     */
    private fun setupObservers() {
        // EventHandler 인스턴스 생성 및 ViewModel에 설정
        val eventHandler = EventHandler<Any>(viewLifecycleOwner.lifecycleScope)
        viewModel.setEventHandler(eventHandler) // ViewModel에 핸들러 전달
        
        // 생명주기 감지하여 구독 관리
        val lifecycleObserver = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                Timber.d("Fragment onStart: 구독 시작")
                setupEventSubscriptions(eventHandler)
                super.onStart(owner)
            }
            
            override fun onStop(owner: LifecycleOwner) {
                Timber.d("Fragment onStop: 구독 취소")
                eventHandler.unsubscribeAll()
                super.onStop(owner)
            }
            
            override fun onDestroy(owner: LifecycleOwner) {
                Timber.d("Fragment onDestroy: 모든 리소스 정리")
                eventHandler.unsubscribeAll()
                viewLifecycleOwner.lifecycle.removeObserver(this)
                super.onDestroy(owner)
            }
        }
        
        // 라이프사이클에 옵저버 추가
        viewLifecycleOwner.lifecycle.addObserver(lifecycleObserver)
    }
    
    /**
     * 이벤트 구독 설정
     */
    private fun setupEventSubscriptions(eventHandler: EventHandler<Any>) {
        try {
            // EditModeManager 이벤트 구독
            eventHandler.subscribeWithHandler(
                owner = viewLifecycleOwner,
                flowProvider = { viewModel.editModeManager.events },
                handler = { event ->
                    try {
                        when (event) {
                            is EditModeEvent.TimerUpdated -> {
                                // 타이머 업데이트는 이제 EditModeManager에서 처리
                                Timber.d("타이머 업데이트 이벤트 수신: ${event.remainingTimeMs}ms")
                            }
                            is EditModeEvent.ModeChanged -> {
                                Timber.d("모드 변경 이벤트 수신: isEditMode=${event.isEditMode}")
                                // UI 업데이트는 EditModeManager에서 처리
                                updateMarkersVisibility()
                            }
                            is EditModeEvent.TimerExpired -> {
                                Timber.d("타이머 만료 이벤트 수신: 읽기 모드로 전환")
                                // 타이머가 완료되면 읽기모드로 강제 전환
                                // 이미 EditModeManager 내부에서 모드 전환을 처리하므로
                                // 여기서는 UI 업데이트만 진행
                                updateMarkersVisibility()
                                
                                // 현재 모드 확인 - 여전히 쓰기 모드라면 강제 전환
                                if (viewModel.editModeManager.getCurrentMode()) {
                                    Timber.w("타이머 만료 후에도 쓰기 모드 상태임: 강제 전환 시도")
                                    viewModel.editModeManager.setEditMode(false, true, false)
                                }
                            }
                            else -> {
                                // 다른 이벤트 처리
                            }
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) {
                            Timber.d("EditMode 이벤트 처리 중 취소됨")
                        } else {
                            Timber.e(e, "EditMode 이벤트 처리 중 오류 발생")
                        }
                    }
                },
                key = "EditModeEvents"
            )

            // 저장 성공 이벤트 구독 - 다이얼로그 내부에 메시지를 표시하도록 변경하여 비활성화
            /*
            eventHandler.subscribeWithHandler(
                owner = viewLifecycleOwner,
                flowProvider = { viewModel.saveSuccessEvent },
                handler = { message ->
                    try {
                        Timber.d("저장 성공 이벤트 수신: $message")
                        // 스낵바로 저장 성공 메시지 표시
                        view?.let { safeView ->
                            val snackbar = Snackbar.make(safeView, message, Snackbar.LENGTH_SHORT)
                            // 스낵바 위치를 화면 상단으로 조정
                            snackbar.view.translationY = -(400f.dp2px(resources))
                            snackbar.show()
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "저장 성공 이벤트 처리 중 오류 발생")
                    }
                },
                key = "SaveSuccessEvent"
            )
            */

            // 저장 실패 이벤트 구독
            eventHandler.subscribeWithHandler(
                owner = viewLifecycleOwner,
                flowProvider = { viewModel.saveFailEvent },
                handler = { message ->
                    try {
                        Timber.d("저장 실패 이벤트 수신: $message")
                        // 스낵바로 저장 실패 메시지 표시 (더 긴 시간 표시)
                        view?.let { safeView ->
                            Snackbar.make(safeView, message, Snackbar.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "저장 실패 이벤트 처리 중 오류 발생")
                    }
                },
                key = "SaveFailEvent"
            )

            // EditModeManager 상태 관찰
            eventHandler.subscribeWithHandler(
                owner = viewLifecycleOwner,
                flowProvider = { viewModel.editModeManager.state },
                handler = { state ->
                    try {
                        Timber.d("EditModeManager 상태 변경: isEditMode=${state.isEditMode}")
                        // UI 업데이트는 EditModeManager에서 처리
                        updateMarkersVisibility()
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) {
                            Timber.d("EditMode 상태 처리 중 취소됨")
                        } else {
                            Timber.e(e, "EditMode 상태 처리 중 오류 발생")
                        }
                    }
                },
                key = "EditModeState"
            )
            
            // 마커 이벤트 구독 추가
            eventHandler.subscribeWithHandler(
                owner = viewLifecycleOwner,
                flowProvider = { viewModel.markerManager.markerEvents },
                handler = { event ->
                    try {
                        Timber.d("마커 이벤트 수신(Fragment): $event")
                        when (event) {
                            is MarkerEvent.MarkerCreated -> {
                                Timber.d("새 마커 생성됨(Fragment): ${event.marker.id}, 위치: (${event.marker.latitude}, ${event.marker.longitude})")
                                // 마커 목록은 mapState를 통해 자동으로 업데이트됨
                                updateMarkersVisibility()
                            }
                            is MarkerEvent.MarkerCreationSuccess -> {
                                Timber.d("마커 생성 성공(Fragment): ${event.marker.id}")
                                try {
                                    // 메모장 다이얼로그 표시는 ViewModel에서 처리
                                    // 여기서는 추가 작업 필요 없음
                                } catch (e: Exception) {
                                    Timber.e(e, "메모장 표시 중 오류 발생")
                                }
                            }
                            is MarkerEvent.MarkerDeleted -> {
                                Timber.d("마커 삭제 이벤트(Fragment): ${event.markerId}")
                                markerUIDelegate.removeMarker(event.markerId)
                            }
                            else -> {
                                // 다른 이벤트는 처리하지 않음
                            }
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) {
                            Timber.d("마커 이벤트 처리 중 취소됨")
                        } else {
                            Timber.e(e, "마커 이벤트 처리 중 오류 발생")
                        }
                    }
                },
                key = "MarkerEvents"
            )
            
            // 메모장 표시 상태 관찰 추가
            eventHandler.subscribeWithHandler(
                owner = viewLifecycleOwner,
                flowProvider = { memoViewModel.shouldShowMemoDialog },
                handler = { shouldShow ->
                    try {
                        Timber.d("메모장 표시 상태 변경: shouldShow=$shouldShow, selectedMarkerId=${memoViewModel.selectedMemoMarkerId.value}")
                        if (shouldShow) {
                            val markerId = memoViewModel.selectedMemoMarkerId.value
                            if (markerId != null) {
                                // 마커 ID가 있으면 해당 마커 정보를 가져와서 메모장 표시
                                Timber.d("메모장 표시 요청 처리 시작: markerId=$markerId")
                                
                                // 마커 정보 로딩 및 재시도 로직을 분리된 함수로 추출
                                fetchMarkerAndShowMemoDialog(markerId)
                            } else {
                                Timber.e("메모장을 표시하려 했으나 마커 ID가 null임")
                                // 오류 보고 및 상태 초기화
                                memoViewModel.onMemoDialogShown()
                            }
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) {
                            Timber.d("메모장 상태 처리 중 취소됨")
                        } else {
                            Timber.e(e, "메모장 상태 처리 중 오류 발생")
                        }
                    }
                },
                key = "MemoDialogState"
            )
            
            // UI 이벤트 구독 추가
            eventHandler.subscribeWithHandler(
                owner = viewLifecycleOwner,
                flowProvider = { viewModel.uiEvents },
                handler = { event ->
                    try {
                        Timber.d("UI 이벤트 수신: $event")
                        when (event) {
                            is MapEvent.ShowConfirmationSnackbar -> {
                                Timber.d("확인 스낵바 이벤트 수신: ${event.message}, 액션: ${event.actionText}")
                                showConfirmationSnackbar(
                                    message = event.message,
                                    actionText = event.actionText,
                                    onAction = event.onAction
                                )
                            }
                            is MapEvent.ShowSnackbar -> {
                                Timber.d("스낵바 이벤트 수신: ${event.message}")
                                showSnackbar(event.message)
                            }
                            // 다른 UI 이벤트 처리
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) {
                            Timber.d("UI 이벤트 처리 중 취소됨")
                        } else {
                            Timber.e(e, "UI 이벤트 처리 중 오류 발생")
                        }
                    }
                },
                key = "UIEvents"
            )

            // 마커 이벤트 핸들러의 내부 이벤트 구독
            eventHandler.subscribeWithHandler<MarkerEventHandlerImpl.InternalMarkerEvent>(
                owner = viewLifecycleOwner,
                flowProvider = { markerEventHandler.internalEvents },
                handler = { event ->
                    try {
                        when (event) {
                            is MarkerEventHandlerImpl.InternalMarkerEvent.MarkerDeleted -> {
                                Timber.d("마커 삭제 내부 이벤트: ${event.markerId}")
                                markerUIDelegate.removeMarker(event.markerId)
                            }
                            is MarkerEventHandlerImpl.InternalMarkerEvent.MarkerSelected -> {
                                Timber.d("마커 선택 내부 이벤트: ${event.markerId}")
                                showMemoListDialog(event.markerId)
                            }
                            is MarkerEventHandlerImpl.InternalMarkerEvent.MarkerCreationSuccess -> {
                                Timber.d("마커 생성 성공 내부 이벤트: ${event.markerId}")
                                // 이미 메모장이 표시되므로 추가 작업 필요없음
                            }
                            is MarkerEventHandlerImpl.InternalMarkerEvent.RefreshMarkersUI -> {
                                Timber.d("마커 UI 새로고침 내부 이벤트 수신")
                                markerUIDelegate.refreshMarkersUI()
                            }
                            else -> {
                                // 처리하지 않는 이벤트
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "마커 내부 이벤트 처리 중 오류 발생")
                    }
                },
                key = "MarkerInternalEvents"
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                Timber.d("이벤트 구독 설정 중 취소됨")
            } else {
                Timber.e(e, "이벤트 구독 설정 중 오류 발생")
            }
        }
    }
    
    private fun observeState() {
        viewLifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.mapState.collect { state ->
                        when (state) {
                            is MapState.Initial -> {
                                progressBar?.isVisible = false
                                viewModel.editModeManager.setEditMode(false, true, false)
                            }
                            is MapState.Loading -> {
                                progressBar?.isVisible = true
                            }
                            is MapState.Success -> {
                                progressBar?.isVisible = false
                                updateMapBasedOnState(state)
                                updateMarkersBasedOnState(state)
                                updateModeBasedOnState(state)
                                
                                // 상태 진단 로그 출력
                                viewModel.logGeohashAndMarkerInfo()
                                
                                // 삭제 중인 마커가 있으면 UI에서 즉시 제거
                                state.markerToDeleteId?.let { markerId ->
                                    Timber.d("MapState에서 삭제 마커 ID 감지: $markerId - 즉시 UI에서 제거")
                                    markerUIDelegate.removeMarker(markerId)
                                }
                            }
                            is MapState.Error -> {
                                progressBar?.isVisible = false
                                handleError(state.error)
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 에러 메시지 표시
     */
    private fun showError(message: String) {
        // MapUIController로 에러 표시 위임
        mapUIController.showError(message)
    }
    
    /**
     * 에러 객체 표시
     */
    private fun showError(error: MapError) {
        // MapUIController로 에러 표시 위임
        mapUIController.showError(error)
    }
    
    /**
     * 마커 상세 정보를 표시합니다.
     */
    private fun showMarkerDetail(marker: com.parker.hotkey.domain.model.Marker, isTemporary: Boolean = false) {
        Timber.d("showMarkerDetail 시작: markerId=${marker.id}, isTemporary=$isTemporary")
        
        try {
            // 현재 표시 중인 메모장이 있으면 닫기
            currentMemoDialog?.dismiss()
            currentMemoDialogRef = null
            
            // 쓰레드 안전을 위해 핸들러 사용
            view?.post {
                if (isAdded && !isRemoving && !isDetached) {
                    // 새로운 메모장 다이얼로그 생성 및 표시
                    val dialog = MemoListDialog.newInstance(
                        markerId = marker.id,
                        isTemporary = isTemporary
                    )
                    dialog.show(childFragmentManager, "memo_list")
                    currentMemoDialogRef = WeakReference(dialog)
                    
                    // DialogFragment의 생명주기 이벤트 감지하여 참조 정리
                    childFragmentManager.registerFragmentLifecycleCallbacks(
                        object : FragmentManager.FragmentLifecycleCallbacks() {
                            override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
                                if (f === currentMemoDialog) {
                                    currentMemoDialogRef = null
                                    childFragmentManager.unregisterFragmentLifecycleCallbacks(this)
                                    Timber.d("메모장 다이얼로그 참조 해제 완료")
                                }
                            }
                        }, false
                    )
                    
                    Timber.d("메모장 다이얼로그 표시 완료: ${marker.id}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "메모장 다이얼로그 표시 중 오류 발생: ${e.message}")
            currentMemoDialogRef = null
            showError("메모장을 표시할 수 없습니다.")
        }
    }
    
    override fun onMapReady(map: NaverMap) {
        naverMap = map
        
        // 맵 설정
        mapConfigDelegate.setupMap(map)
        
        // MarkerUIDelegate 초기화
        markerUIDelegate.setNaverMap(map)
        markerUIDelegate.setupMarkerClickListener(map) { _marker ->
            Timber.d("마커 클릭 이벤트 발생: markerId=${_marker.id}")
            viewModel.onMarkerClick(_marker.id)
            true
        }
        
        // 위치 추적 활성화를 위한 강화된 코드
        try {
            // 지도 설정 후 즉시 위치 권한/서비스 확인 및 위치 추적 활성화 시도
            if (isAdded && context != null) {
                // 위치 권한 컨트롤러 초기화
                locationPermissionController.init(this, locationSource)
                
                // 위치 권한 컨트롤러에 맵 설정
                locationPermissionController.setNaverMap(map)
                
                // 지도 준비 후 약간의 지연을 두고 위치 권한 체크 및 위치 추적 활성화
                viewLifecycleScope.launch {
                    delay(1000) // 1초 지연
                    
                    // 위치 권한 체크를 통해 위치 추적 활성화
                    locationPermissionController.checkLocationPermission()
                    
                    // ViewModelz에 위치 추적 초기화 요청
                    locationViewModel.forceInitializeLocationTracking()
                    
                    Timber.d("위치 추적 활성화 요청 완료 (지연 후)")
                }
            } else {
                Timber.e("위치 권한 컨트롤러 초기화 불가: Fragment가 준비되지 않음")
            }
        } catch (e: Exception) {
            Timber.e(e, "위치 권한 컨트롤러 초기화 중 오류 발생")
        }
        
        // EditModeManager 초기화 및 리스너 설정
        if (viewModel.editModeManager is com.parker.hotkey.domain.manager.impl.EditModeManagerImpl) {
            val editModeManager = viewModel.editModeManager as com.parker.hotkey.domain.manager.impl.EditModeManagerImpl
            editModeManager.initialize(
                map = map,
                delegate = markerUIDelegate,
                onMapClickInEditMode = { latLng -> 
                    Timber.d("지도 클릭 이벤트 발생 (편집 모드): ${latLng.latitude}, ${latLng.longitude}")
                    viewModel.onMapClicked(latLng) 
                },
                onMapClickInReadMode = { 
                    Timber.d("지도 클릭 이벤트 발생 (읽기 모드) - 쓰기 모드로 전환 메시지 표시")
                    // 스낵바 메시지를 표시하여 사용자에게 쓰기 모드 전환을 물어봄
                    view?.let { safeView ->
                        Snackbar.make(safeView, R.string.write_mode_message, Snackbar.LENGTH_LONG)
                            .apply {
                                setAction(R.string.to_write_mode) {
                                    // 사용자가 확인 시 쓰기 모드로 전환
                                    // 기존 editModeManager.toggleEditMode() 대신 아래 코드로 수정
                                    // 사용자 액션으로 명시적으로 표시하여 백그라운드에서도 타이머 유지
                                    editModeManager.setEditMode(true, true, true)
                                    Timber.d("사용자가 스낵바에서 쓰기 모드 전환 버튼 클릭 - 사용자 액션 표시")
                                }
                                
                                // 스낵바 위치 조정
                                this.view.translationY = -(150f.dp2px(resources))
                            }
                            .show()
                    }
                }
            )
            Timber.d("EditModeManager 초기화 완료")
        } else {
            Timber.e("EditModeManager 타입이 예상과 다릅니다: ${viewModel.editModeManager.javaClass.name}")
        }
        
        // 기존 리스너 추가
        viewModel.editModeManager.addOnModeChangeListener(editModeChangeListener)
        
        // 맵 콜백 설정
        initMapListeners()
        
        // 상태 관찰 설정
        observeState()
        
        // 마커 상태 진단 로그 출력
        Timber.d("맵 초기화 완료 - 마커 상태 진단 실행")
        viewModel.logGeohashAndMarkerInfo()
    }
    
    private fun initMapListeners() {
        naverMap?.addOnCameraIdleListener {
            naverMap?.let { map -> 
                // 카메라 위치 변경 시에는 서클 위치를 업데이트하지 않음
                // 현재 카메라 위치에서 geohash 정보 가져오기
                val cameraPosition = map.cameraPosition
                val cameraZoom = cameraPosition.zoom
            
                // 디바운싱된 마커 로딩 호출 - CPU 과부하 최적화
                Timber.d("카메라 IDLE 이벤트 - 디바운싱된 마커 로딩 요청")
                viewModel.markerViewModel.onCameraIdleDebounced(
                    geohash = currentLocationGeohash ?: "",
                    neighbors = currentLocationNeighbors,
                    zoomLevel = cameraZoom
                )
                
                // 마커 가시성만 즉시 업데이트 (UI 변경은 빠르게)
                updateMarkersVisibility()
            }
        }
        
        // 위치 변경 리스너 추가
        naverMap?.addOnLocationChangeListener { location ->
            val newLocation = LatLng(location.latitude, location.longitude)
            currentUserLocation = newLocation
            // 실제 위치가 변경될 때만 서클 업데이트
            updateGeohashCircle(newLocation)
        }
    }
    
    private fun updateGeohashCircle(center: LatLng) {
        try {
            // 이전 geohash6 값 저장
            val previousGeohash = currentLocationGeohash
            
            // 새로운 geohash6 계산
            currentLocationGeohash = GeoHashUtil.encode(center.latitude, center.longitude, GeohashConstants.GEOHASH_PRECISION)
            currentLocationNeighbors = GeoHashUtil.getNeighbors(currentLocationGeohash!!)
            
            // 매번 마커 가시성 업데이트 (영역이 변경되지 않더라도)
            updateMarkersVisibility()
            
            // 영역이 변경된 경우 로그 출력
            if (previousGeohash != currentLocationGeohash) {
                Timber.d("내 위치 geohash6 변경됨: $previousGeohash -> $currentLocationGeohash")
                Timber.d("Neighbor Geohashes: ${currentLocationNeighbors.joinToString()}")
            }
            
            // 영역 서클 업데이트, 서클 중심으로 map 설정
            if (currentGeohashCircle != null) {
                currentGeohashCircle?.center = center
                currentGeohashCircle?.map = naverMap
            } else {
                currentGeohashCircle = CircleOverlay().apply {
                    this.center = center
                    this.radius = GeohashConstants.GEOHASH_RADIUS_M.toDouble()
                    this.color = Color.argb(0, 0, 0, 0)  // 완전히 투명하게 설정 (내부 색상 없음)
                    this.outlineColor = Color.argb(180, 255, 0, 0)  // 테두리 색상 (진한 빨간색)
                    this.outlineWidth = 3  // 테두리 두께 증가
                    this.map = naverMap
                }
            }
            
            Timber.d("Geohash 서클 업데이트 완료: center=${center.latitude},${center.longitude}")
            
        } catch (e: Exception) {
            Timber.e(e, "Geohash 서클 업데이트 중 오류 발생")
            showError("지도 업데이트 중 오류가 발생했습니다.")
        }
    }
    
    private fun updateMarkersVisibility() {
        // EditModeManager에서 현재 모드 가져오기
        // val isEditMode = viewModel.editModeManager.getCurrentMode() // 더 이상 필요 없음
        
        // DB에 저장된 마커 개수와 UI 마커 개수 비교 (디버깅용)
        viewModel.getMarkerCountFromDB(currentLocationGeohash ?: "")
        
        // MarkerUIDelegate로 마커 가시성 업데이트 위임
        markerUIDelegate.updateMarkersVisibility(
            currentGeohash = currentLocationGeohash,
            neighbors = currentLocationNeighbors
            // isEditMode 파라미터 전달 제거
        )
    }
    
    private fun moveToCurrentLocation() {
        if (!locationPermissionController.hasLocationPermission()) {
            Timber.d("위치 권한 없음 - 현재 위치로 이동 불가")
            return
        }

        try {
            Timber.d("현재 위치로 이동 시도")
            val lastLocation = locationSource.lastLocation
            if (lastLocation != null) {
                val latLng = LatLng(lastLocation.latitude, lastLocation.longitude)
                
                // LocationViewModel을 통해 현재 위치 업데이트
                locationViewModel.updateLocation(latLng, DEFAULT_ZOOM)
                
                // 카메라 이동 시 애니메이션 추가
                val cameraUpdate = CameraUpdate.scrollAndZoomTo(latLng, DEFAULT_ZOOM)
                    .animate(CameraAnimation.Easing)
                naverMap?.moveCamera(cameraUpdate)
                
                // 현재 위치 기준으로 서클 업데이트
                updateGeohashCircle(latLng)
                Timber.d("현재 위치로 이동 완료: lat=${latLng.latitude}, lng=${latLng.longitude}, zoom=${DEFAULT_ZOOM}")
            } else {
                Timber.d("마지막 위치 정보 없음")
                showError("현재 위치를 가져올 수 없습니다. GPS 신호를 확인해주세요.")
            }
        } catch (e: Exception) {
            Timber.e(e, "현재 위치로 이동 중 오류 발생")
            showError("현재 위치로 이동할 수 없습니다.")
        }
    }
    
    override fun onStart() {
        super.onStart()
        // 생명주기 이벤트 전달
        lifecycleAwareComponents.forEach { component ->
            component.onStart()
        }
    }
    
    /**
     * 포그라운드 전환 처리 - MainActivity에서 호출됨
     * 
     * @param needsRefresh 데이터 새로고침 필요 여부
     */
    fun handleForegroundTransition(needsRefresh: Boolean) {
        Timber.d("MapFragment: 포그라운드 전환 처리, 새로고침 필요: $needsRefresh")
        
        // 백그라운드에서 포그라운드로 전환 시 이전 메모장 다이얼로그 닫기
        currentMemoDialog?.dismissAllowingStateLoss()
        currentMemoDialogRef = null
        
        // 항상 최소한의 UI 복원 수행 (깜박임 방지)
        updateMapUIForForeground()
        
        if (needsRefresh) {
            // 필요한 경우에만 데이터 새로고침 
            viewModel.handleForegroundTransition(needsRefresh)
            
            // 데이터 갱신은 별도로 처리하여 UI 깜박임 방지
            viewLifecycleScope.launch {
                delay(100) // UI 안정화 후 데이터 갱신
                viewModel.markerViewModel.handleForegroundTransition(
                    currentGeohash = currentLocationGeohash ?: "",
                    neighbors = currentLocationNeighbors,
                    zoomLevel = naverMap?.cameraPosition?.zoom ?: DEFAULT_ZOOM
                )
            }
        } else {
            // 새로고침이 필요 없는 경우 데이터 갱신 생략
            Timber.d("짧은 백그라운드 후 복귀: 데이터 새로고침 생략")
        }
        
        // EditModeManager에 포그라운드 전환 알림
        if (viewModel.editModeManager is com.parker.hotkey.domain.manager.impl.EditModeManagerImpl) {
            val editModeManager = viewModel.editModeManager as com.parker.hotkey.domain.manager.impl.EditModeManagerImpl
            editModeManager.onAppForeground()
            Timber.d("EditModeManager에 포그라운드 전환 알림 전달")
        }
        
        // 지도가 초기화되지 않았으면 다시 초기화
        if (naverMap == null) {
            Timber.d("네이버맵이 null이므로 다시 초기화합니다")
            binding.mapView.getMapAsync(this)
        }
        
        // 포그라운드 전환 시 메모리 상태 확인 및 최적화
        memoryWatchdog.checkOnStartAndForeground { isAggressiveCleanup ->
            // 마커 메모리 최적화
            markerUIDelegate.onMemoryLow(isAggressiveCleanup)
            
            // 메모리 정보 로깅
            memoryWatchdog.logMemoryInfo()
        }
    }
    
    /**
     * 포그라운드 전환 시 UI만 복원합니다 (데이터 갱신 없음으로 깜박임 방지)
     * - 마커 클릭 리스너만 재설정
     * - 위치 권한 확인
     */
    private fun updateMapUIForForeground() {
        Timber.d("포그라운드 전환 - UI 복원 시작")
        try {
            if (naverMap != null) {
                // 1. 마커 UI 새로고침: 클릭 리스너만 복원 (깜박임 방지)
                markerUIDelegate.handleForegroundTransition()
                
                // 2. 마커 클릭 리스너 명시적 재설정
                setupMarkerClicks()
                
                // 3. 주기적으로 마커 상태 체크 시작
                startMarkerCheckTimer()
                
                // 4. 위치 권한 확인
                locationPermissionController.checkPermission()
                
                Timber.d("포그라운드 UI 복원 완료")
            } else {
                Timber.w("naverMap이 null, 포그라운드 UI 복원 진행 불가")
            }
        } catch (e: Exception) {
            Timber.e(e, "포그라운드 전환 시 UI 복원 오류")
        }
    }
    
    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        
        // 네비게이션 복귀 감지 로직 추가
        if (viewModel.appStateManager.isNavigationReturn()) {
            Timber.d("네비게이션 복귀 감지 - 로컬 데이터만 사용")
            viewModel.loadLocalDataOnly()
        }
        // 나머지 포그라운드 전환 처리는 handleForegroundTransition에서 수행
        
        Timber.d("MapFragment: onResume 완료")
    }
    
    /**
     * 다시 포그라운드로 돌아올 때 맵 업데이트
     * - 마커를 갱신하고 클릭 리스너를 재설정합니다
     * - 위치 권한을 확인하고 위치 업데이트를 요청합니다
     */
    private fun updateMapForForeground() {
        Timber.d("포그라운드 전환 - 맵 업데이트 시작")
        try {
            if (naverMap != null) {
                // 1. 마커 UI 새로고침: 최적화된 포그라운드 전환 처리 사용
                markerUIDelegate.handleForegroundTransition()
                
                // 2. 마커 클릭 리스너 명시적 재설정
                setupMarkerClicks()
                
                // 3. 주기적으로 마커 상태 체크 시작
                startMarkerCheckTimer()
                
                // 4. 위치 권한 확인 및 위치 업데이트
                locationPermissionController.checkPermission()
                
                // 5. 타임아웃을 설정하여 위치 데이터 대기
                viewLifecycleScope.launch {
                    // 위치 데이터를 폴링 방식 대신 이벤트 기반으로 처리
                    // 위치 데이터가 수신되면 즉시 백엔드 데이터 동기화 수행
                    try {
                        withTimeoutOrNull(5000L) {
                            // 위치 데이터 Flow 구독
                            locationViewModel.locationState
                                .filter { state -> 
                                    state.currentLocation.latitude != 0.0 &&
                                    state.currentLocation.longitude != 0.0
                                }
                                .first()
                        }
                        
                        Timber.d("위치 데이터 수신됨, 마커 데이터 갱신 시작")
                        // 위치 데이터 확인 후 백엔드 데이터 갱신 요청
                        viewModel.refreshMarkersForForeground()
                    } catch (e: Exception) {
                        Timber.e(e, "위치 데이터 구독 중 오류 발생")
                        if (locationViewModel.getLastKnownLocation() == null) {
                            Timber.w("위치 데이터 수신 타임아웃")
                        }
                        
                        // 오류가 발생하더라도 데이터 갱신은 시도
                        viewModel.refreshMarkersForForeground()
                    }
                }
            } else {
                Timber.w("naverMap이 null, 포그라운드 업데이트 진행 불가")
            }
        } catch (e: Exception) {
            Timber.e(e, "포그라운드 전환 시 맵 업데이트 오류")
        }
    }
    
    /**
     * 마커 클릭 리스너를 명시적으로 설정합니다.
     * 백그라운드/포그라운드 전환 후에 호출됩니다.
     */
    private fun setupMarkerClicks() {
        try {
            Timber.d("마커 클릭 리스너 설정 시작")
            // 명시적으로 MarkerUIDelegate의 클릭 리스너 설정 메서드 호출
            naverMap?.let { map ->
                markerUIDelegate.setupMarkerClickListener(map) { _marker ->
                    handleMarkerClick(_marker)
                }
                Timber.d("마커 클릭 리스너 설정 완료")
            } ?: Timber.e("마커 클릭 리스너 설정 실패: naverMap이 null입니다")
        } catch (e: Exception) {
            Timber.e(e, "마커 클릭 리스너 설정 중 오류 발생")
        }
    }
    
    /**
     * 마커 클릭 이벤트를 처리합니다.
     */
    private fun handleMarkerClick(marker: com.parker.hotkey.domain.model.Marker): Boolean {
        Timber.d("마커 클릭됨: ${marker.id}")
        try {
            // 메모 매니저를 통해 메모 다이얼로그 표시
            memoManager.showMemoDialog(marker.id)
            return true
        } catch (e: Exception) {
            Timber.e(e, "마커 클릭 처리 중 오류 발생: ${e.message}")
            return false
        }
    }
    
    /**
     * 주기적으로 마커 상태를 체크하는 타이머를 시작합니다.
     */
    private fun startMarkerCheckTimer() {
        try {
            // 기존 타이머가 있으면 취소
            markerCheckTimer?.cancel()
            
            // 새 타이머 생성 및 시작 - 30초마다 마커 상태 체크
            markerCheckTimer = viewLifecycleScope.launch {
                while (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    checkAndUpdateMarkers()
                    delay(30000) // 30초 간격
                }
            }
            Timber.d("마커 체크 타이머 시작됨")
        } catch (e: Exception) {
            Timber.e(e, "마커 체크 타이머 시작 중 오류 발생")
        }
    }
    
    /**
     * 모든 마커의 상태를 확인하고 문제가 있으면 업데이트합니다.
     */
    private fun checkAndUpdateMarkers() {
        try {
            if (naverMap == null) return
            
            Timber.d("마커 상태 점검 시작")
            // 마커 UI 새로고침 (클릭 리스너 복구 포함)
            markerUIDelegate.refreshMarkersUI()
            
            // 명시적 클릭 리스너 재설정 (추가 안전장치)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                // 모든 마커의 클릭 가능 상태 확인
                val clickableIssueCount = markerUIDelegate.checkAllMarkersClickable()
                if (clickableIssueCount > 0) {
                    Timber.w("${clickableIssueCount}개 마커에 클릭 문제 발견, 클릭 리스너 재설정")
                    setupMarkerClicks()
                }
            }
            Timber.d("마커 상태 점검 완료")
        } catch (e: Exception) {
            Timber.e(e, "마커 상태 점검 중 오류 발생")
        }
    }
    
    // 마커 체크 타이머 변수 추가
    private var markerCheckTimer: Job? = null
    
    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        viewModel.onPause()
        
        // MarkerViewModel에 백그라운드 전환 알림 - CPU 과부하 최적화
        viewModel.markerViewModel.onLifecyclePause()
        
        // EditModeManager에 백그라운드 전환 알림
        if (viewModel.editModeManager is com.parker.hotkey.domain.manager.impl.EditModeManagerImpl) {
            val editModeManager = viewModel.editModeManager as com.parker.hotkey.domain.manager.impl.EditModeManagerImpl
            editModeManager.onAppBackground()
            Timber.d("EditModeManager에 백그라운드 전환 알림 전달")
        }
        
        // 위치 추적 중지
        locationViewModel.stopLocationTracking()
        
        // 위치 소스 일시 중지
        if (::locationSource.isInitialized) {
            locationSource.deactivate()
        }
        
        // 화면 전환 시 메모장 다이얼로그 닫기
        currentMemoDialog?.dismissAllowingStateLoss()
        currentMemoDialogRef = null
    }
    
    override fun onStop() {
        // 생명주기 이벤트 전달
        lifecycleAwareComponents.forEach { component ->
            component.onStop()
        }
        super.onStop()
    }
    
    override fun onDestroyView() {
        // 생명주기 인식 컴포넌트들의 onDestroy 호출
        lifecycleAwareComponents.forEach { component ->
            component.onDestroy()
        }
        
        // 목록 초기화
        lifecycleAwareComponents.clear()
        
        // 약한 참조 패턴을 적용한 싱글톤 클래스의 리소스 정리
        cleanupWeakReferences()
        
        try {
            Timber.d("MapFragment onDestroyView 시작")
            
            // 모든 코루틴 작업 취소
            fragmentJobs.forEach { job ->
                try {
                    job.cancel()
                } catch (e: Exception) {
                    Timber.e(e, "코루틴 작업 취소 중 오류 발생")
                }
            }
            fragmentJobs.clear()
            
            // 마커 객체 정리
            markerUIDelegate.cleanup()
            
            // 위치 권한 컨트롤러 정리
            // locationPermissionController.cleanup() // LifecycleAware를 통해 이미 호출됨

            // 진행 중인 작업 취소
            cameraUpdateJob?.cancel()
            cameraUpdateJob = null
            markerCheckTimer?.cancel()
            markerCheckTimer = null
            
            // 위치 추적 중지
            locationViewModel.stopLocationTracking()
            
            // 위치 소스 정리
            if (::locationSource.isInitialized) {
                locationSource.deactivate()
            }
            
            // View 참조 해제
            progressBar = null
            modeText = null
            menuIcon = null
            modeSwitch = null
            editModeTimer = null
            
            // MemoListDialog가 표시 중이면 닫기
            dismissMemoDialog()
            currentMemoDialogRef = null
            
            // EditModeManager 리스너 해제
            viewModel.editModeManager.removeOnModeChangeListener(editModeChangeListener)
            
            // EditModeManager UI 참조 정리
            viewModel.editModeManager.clearUIReferences()
            
            // 현재 geohash 서클 정리
            currentGeohashCircle?.map = null
            currentGeohashCircle = null
            
            // 네이버 지도 마커 리스너 제거 및 참조 해제
            try {
                if (naverMap != null) {
                    // 마커 맵 리스너 제거
                    markerUIDelegate.clearAllMarkers()
                    // 지도의 모든 리스너 제거
                    naverMap?.setOnMapClickListener(null)
                    naverMap?.setOnMapLongClickListener(null)
                    // 카메라 리스너 및 옵션 리스너 제거
                    // naverMap?.onCameraChange = null  // 이 방식은 올바르지 않음
                    // naverMap?.onCameraIdle = null    // 이 방식은 올바르지 않음
                    // naverMap?.onOptionChange = null  // 이 방식은 올바르지 않음
                    
                    // 다른 주요 리소스 제거
                    naverMap?.locationOverlay?.isVisible = false
                    naverMap?.locationSource = null
                    naverMap = null
                }
            } catch (e: Exception) {
                Timber.e(e, "네이버 지도 참조 해제 중 오류 발생")
            }
            
            // MapView 수명주기 메서드 호출
            binding.mapView.onDestroy()
            
            // 명시적으로 rootView 참조 끊기 (메모리 누수 해결)
            try {
                val rootView = binding.root as ViewGroup
                rootView.removeAllViews()
                
                // 모든 뷰 리스너 해제
                clearAllViewListeners(rootView)
            } catch (e: Exception) {
                Timber.e(e, "뷰 정리 중 오류 발생")
            }
            
            // ViewBinding 참조 정리
            clearViewBinding()
            
            Timber.d("MapFragment onDestroyView 완료")
        } catch (e: Exception) {
            Timber.e(e, "MapFragment onDestroyView 중 오류 발생")
        }
        
        super.onDestroyView()
    }
    
    /**
     * ViewBinding 참조를 정리합니다.
     * ViewBinding 참조로 인한 메모리 누수를 방지합니다.
     */
    private fun clearViewBinding() {
        try {
            // by viewBinding delegate 사용 시 별도 정리 필요 없음
            // delegate가 자동으로 View 생명주기에 따라 바인딩을 관리함
            Timber.d("ViewBinding - delegate에 의해 자동 관리됨")
        } catch (e: Exception) {
            Timber.e(e, "ViewBinding 참조 정리 실패")
        }
    }
    
    /**
     * 뷰와 그 자식들의 모든 리스너를 해제합니다.
     */
    private fun clearAllViewListeners(view: View?) {
        if (view == null) return
        
        try {
            // 모든 클릭 리스너 제거
            view.setOnClickListener(null)
            
            // OnTouchListener 제거
            view.setOnTouchListener(null)
            
            // ViewTreeObserver 리스너 제거
            val observer = view.viewTreeObserver
            if (observer.isAlive) {
                observer.removeOnGlobalLayoutListener(null)
                observer.removeOnPreDrawListener(null)
                observer.removeOnDrawListener(null)
            }
            
            // ViewGroup인 경우 모든 자식에 대해 재귀적으로 처리
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    clearAllViewListeners(view.getChildAt(i))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "리스너 해제 중 오류 발생")
        }
    }
    
    override fun onDestroy() {
        try {
            Timber.d("MapFragment onDestroy 시작")
            
            // onDestroyView에서 정리되지 않은 자원 정리
            // 마커 풀 정리
            if (::markerUIDelegate.isInitialized) {
                try {
                    // 강제로 메모리 정리 (풀에 있는 마커도 모두 해제)
                    markerUIDelegate.onMemoryLow(true)
                } catch (e: Exception) {
                    Timber.e(e, "마커 정리 중 오류 발생")
                }
            }
            
            // 프래그먼트 매니저 참조 정리
            try {
                // FragmentManager의 보류 중인 트랜잭션만 실행
                childFragmentManager.executePendingTransactions()
                
                Timber.d("프래그먼트 매니저 정리 완료")
            } catch (e: Exception) {
                Timber.e(e, "프래그먼트 매니저 정리 중 오류 발생")
            }
            
            // 한 번 더 명시적으로 지도 관련 참조 제거
            naverMap = null
            
            Timber.d("MapFragment onDestroy 완료")
        } catch (e: Exception) {
            Timber.e(e, "MapFragment onDestroy 중 오류 발생")
        }
        
        super.onDestroy()
    }
    
    /**
     * 프래그먼트가 액티비티에서 분리될 때 호출됩니다.
     * 모든 액티비티 참조를 해제하는 마지막 단계입니다.
     */
    override fun onDetach() {
        try {
            Timber.d("MapFragment onDetach 시작")
            
            // 모든 액티비티 참조 해제
            val activity = requireActivity()
            if (activity is MainActivity) {
                // 메인 액티비티 참조 정리
            }
            
            // 네이버 지도 참조 해제 (마지막 안전장치)
            if (naverMap != null) {
                naverMap?.locationOverlay?.isVisible = false
                naverMap?.locationSource = null
                naverMap = null
                Timber.d("onDetach에서 네이버 맵 참조 해제")
            }
            
            // 모든 마커 참조 해제 (마지막 안전장치)
            if (::markerUIDelegate.isInitialized) {
                markerUIDelegate.clearAllMarkers()
                markerUIDelegate.cleanup()
                Timber.d("onDetach에서 마커 참조 해제")
            }
            
            // 시스템에 GC 힌트
            System.gc()
            
            Timber.d("MapFragment onDetach 완료")
        } catch (e: Exception) {
            Timber.e(e, "MapFragment onDetach 중 오류 발생")
        }
        super.onDetach()
    }
    
    /**
     * 맵뷰 정리를 위한 추가 메서드
     * 외부에서 명시적 정리가 필요할 때 호출
     */
    fun destroyMapView() {
        try {
            // 맵뷰 생명주기 메서드 명시적 호출
            binding.mapView.onDestroy()
            
            // 맵뷰 참조 제거 (NaverMap SDK에서 제공하는 공식 API만 사용)
            naverMap = null
            
            // 부모 뷰그룹에서 제거
            val parentView = binding.mapView.parent as? ViewGroup
            parentView?.removeView(binding.mapView)
            
            Timber.d("맵뷰 수동 정리 완료")
        } catch (e: Exception) {
            Timber.e(e, "맵뷰 수동 정리 중 오류 발생")
        }
    }
    
    /**
     * 모든 마커 객체 정리
     */
    private fun clearAllMarkers() {
        try {
            // 마커 맵 순회하며 각 마커 정리
            markers.forEach { (_, marker) ->
                marker.map = null
                marker.onClickListener = null
            }
            markers.clear()
        } catch (e: Exception) {
            Timber.e(e, "마커 정리 중 오류 발생")
        }
    }
    
    /**
     * MemoListDialog가 표시 중이면 닫기
     */
    private fun dismissMemoDialog() {
        try {
            val fragmentManager = childFragmentManager
            val memoDialog = fragmentManager.findFragmentByTag("memo_list") as? MemoListDialog
            memoDialog?.dismiss()
        } catch (e: Exception) {
            Timber.e(e, "메모 다이얼로그 닫기 중 오류 발생")
        }
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        
        // 시스템 메모리 부족 알림 시 적극적인 메모리 최적화 수행
        Timber.w("시스템 메모리 부족 알림 감지")
        markerUIDelegate.onMemoryLow(true)
        
        binding.mapView.onLowMemory()
    }
    
    // 위치 권한 확인 및 위치 추적 활성화
    private fun checkLocationPermissionAndEnableTracking() {
        try {
            Timber.d("위치 권한 확인 및 위치 추적 활성화 시작")
            
            // 위치 권한 컨트롤러를 사용하여 권한 확인 및 처리
            locationPermissionController.setCallbacks(
                onPermissionGranted = {
                    Timber.d("위치 권한 승인됨, 위치 추적 활성화")
                    // 권한이 있는 경우 위치 추적 활성화
                    locationViewModel.checkLocationPermission()
                    
                    // 알림 권한 확인 및 요청
                    checkAndRequestNotificationPermission()
                },
                onPermissionDenied = {
                    Timber.d("위치 권한 거부됨")
                    showLocationPermissionDeniedMessage()
                },
                showError = { errorMessage ->
                    Timber.e("위치 권한 확인 중 오류: $errorMessage")
                    showError(errorMessage)
                },
                onFirstLocationUpdate = { location ->
                    Timber.d("첫 위치 업데이트: lat=${location.latitude}, lng=${location.longitude}")
                    locationViewModel.updateUserLocation(location)
                }
            )
            
            // 권한 확인 및 요청
            locationPermissionController.checkPermission()
            
            Timber.d("위치 권한 확인 및 위치 추적 활성화 완료")
        } catch (e: Exception) {
            Timber.e(e, "위치 권한 확인 중 오류 발생")
        }
    }

    fun setMapZoomLevel(zoomLevel: Double) {
        naverMap?.moveCamera(CameraUpdate.zoomTo(zoomLevel))
    }

    /**
     * 모드 상태에 따라 UI 업데이트
     */
    private fun updateModeBasedOnState(state: MapState.Success) {
        // 편집 모드 UI는 이제 EditModeManager에서 직접 처리
        Timber.d("상태 기반 모드 업데이트: editMode=${state.editMode}")
        
        // 중요: 사용자의 직접 모드 변경 후 짧은 시간 동안은 상태 동기화를 수행하지 않음
        // 이렇게 하면 사용자가 직접 변경한 모드가 자동으로 되돌아가는 것을 방지
        val userJustChangedMode = lastUserModeChangeTime > 0 && 
                                 System.currentTimeMillis() - lastUserModeChangeTime < 2000 // 2초 동안 보호
        
        if (userJustChangedMode) {
            Timber.d("사용자가 최근에 모드를 직접 변경함 (${System.currentTimeMillis() - lastUserModeChangeTime}ms 전) - 상태 동기화 생략")
            return
        }
        
        // 필요시 EditModeManager의 상태 동기화 (사용자 변경이 아닌 경우에만)
        if (viewModel.editModeManager.getCurrentMode() != state.editMode) {
            Timber.d("편집 모드 상태 불일치 감지: manager=${viewModel.editModeManager.getCurrentMode()}, state=${state.editMode}")
            
            // 상태 불일치 시에만 명시적으로 setEditMode 호출
            viewModel.editModeManager.setEditMode(state.editMode, false, false)
        }
        
        // 타이머 UI도 EditModeManager에서 처리
        if (state.editMode) {
            Timber.d("타이머 상태 업데이트: ${state.editModeTimeRemaining}ms")
        }
    }
    
    // 사용자 모드 변경 시간 추적을 위한 변수 추가
    private var lastUserModeChangeTime: Long = 0

    /**
     * 마커 ID로 마커 정보를 가져와 메모장 다이얼로그를 표시합니다.
     */
    private fun fetchMarkerAndShowMemoDialog(markerId: String) {
        try {
            Timber.d("마커 메모 다이얼로그 표시 요청: markerId=$markerId")
            
            // 비동기적으로 마커 데이터 로드 및 메모장 표시
            viewLifecycleScope.launch {
                try {
                    // 먼저 마커가 임시 마커인지 확인
                    val isTemporary = viewModel.isTemporaryMarker(markerId)
                    Timber.d("마커 상태 확인: markerId=$markerId, 임시 마커 여부=$isTemporary")
                    
                    // 임시 마커일 경우 메모리에서 가져오는 방식 우선 시도 후 빈 마커 객체 사용
                    if (isTemporary) {
                        Timber.d("임시 마커 확인됨. 먼저 메모리에서 가져오기 시도: $markerId")
                        
                        // 1. 먼저 메모리에서 마커 찾기 시도
                        val markerFromMemory = try {
                            // 마커 매니저에서 직접 찾기
                            viewModel.markerManager.markers.value.find { it.id == markerId } ?:
                            // 모든 마커를 한 번 더 확인
                            viewModel.markerManager.getMarkerById(markerId)
                        } catch (e: Exception) {
                            Timber.e(e, "임시 마커 메모리 검색 중 오류: $markerId")
                            null
                        }
                        
                        if (markerFromMemory != null) {
                            Timber.d("임시 마커를 메모리에서 찾음: $markerId")
                            showMarkerDetail(markerFromMemory, true)
                            return@launch
                        }
                        
                        // 2. 메모리에서 찾지 못한 경우 빈 마커 객체 사용
                        Timber.d("임시 마커를 메모리에서 찾지 못해 빈 마커 객체 사용: $markerId")
                        val emptyMarker = com.parker.hotkey.domain.model.Marker(
                            id = markerId,
                            userId = viewModel.getUserId() ?: "",  // 유효한 사용자 ID 사용
                            latitude = 0.0,
                            longitude = 0.0, 
                            geohash = "",
                            modifiedAt = System.currentTimeMillis()
                        )
                        
                        showMarkerDetail(emptyMarker, true)
                        return@launch
                    }
                    
                    // 일반 마커인 경우 재시도 로직 사용 - 백업 계획도 추가
                    val marker = RetryUtil.retry(
                        maxRetries = 5,
                        initialDelayMillis = 100,
                        maxDelayMillis = 800
                    ) { attempt ->
                        // 1. 기본 방식으로 시도
                        var marker = viewModel.markerManager.getMarkerById(markerId)
                        
                        // 2. 기본 방식 실패 시 대체 방법 시도
                        if (marker == null && attempt > 0) {
                            Timber.d("기본 방식 실패, 대체 방법 시도 중 (시도 ${attempt+1}): $markerId")
                            // 모든 마커 목록에서 직접 검색
                            marker = viewModel.markerManager.markers.value.find { it.id == markerId }
                        }
                        
                        if (marker != null) {
                            Timber.d("마커 정보 로드 성공 (시도 ${attempt+1}): $markerId")
                        } else {
                            Timber.d("마커 정보 로드 시도 ${attempt+1} 실패: $markerId")
                        }
                        marker
                    }
                    
                    if (marker != null) {
                        // 마커 정보 로드 성공, 메모장 표시
                        Timber.d("마커 정보 로드 성공, 메모장 표시: $markerId")
                        showMarkerDetail(marker, isTemporary)
                    } else {
                        // 최대 재시도 후에도 마커 정보를 가져오지 못한 경우
                        Timber.e("마커 정보를 최대 재시도 후에도 로드 실패: $markerId")
                        
                        // 마커가 존재하지 않는 경우를 위한 대체 방법
                        try {
                            // 마커 매니저에서 모든 마커 목록 확인 (마지막 시도)
                            val allMarkers = viewModel.markerManager.markers.value
                            val foundMarker = allMarkers.find { it.id == markerId }
                            
                            if (foundMarker != null) {
                                // 목록에서 마커를 찾은 경우
                                Timber.d("목록에서 마커 발견, 메모장 표시: $markerId")
                                showMarkerDetail(foundMarker, isTemporary)
                                return@launch
                            }
                            
                            // 마커를 찾을 수 없어 새로고침 요청
                            Timber.d("마커를 찾을 수 없어 마커 새로고침 요청")
                            viewModel.refreshMarkersForForeground()
                            delay(300) // 새로고침 대기
                            
                            // 새로고침 후 다시 시도
                            val refreshedMarkers = viewModel.markerManager.markers.value
                            val refreshedMarker = refreshedMarkers.find { it.id == markerId }
                            
                            if (refreshedMarker != null) {
                                Timber.d("새로고침 후 마커 발견, 메모장 표시: $markerId")
                                showMarkerDetail(refreshedMarker, isTemporary)
                                return@launch
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "마커 목록 확인 중 예외 발생: $markerId")
                        }
                        
                        // 최종 대안: 빈 마커 객체 생성하여 메모장은 표시
                        val emptyMarker = com.parker.hotkey.domain.model.Marker(
                            id = markerId,
                            userId = viewModel.getUserId() ?: "",
                            latitude = 0.0,
                            longitude = 0.0, 
                            geohash = "",
                            modifiedAt = System.currentTimeMillis()
                        )
                        
                        showMarkerDetail(emptyMarker, isTemporary)
                        
                        // 오류 메시지 표시
                        showError("마커 정보를 불러오는데 문제가 발생했습니다.")
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Timber.e(e, "메모장 표시 중 오류 발생: $markerId")
                    showError("메모장을 표시할 수 없습니다.")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "메모장 표시 함수 실행 중 오류 발생: $markerId")
            showError("메모장을 표시할 수 없습니다.")
        }
    }

    /**
     * MemoViewModel 인스턴스를 반환합니다.
     * MainActivity에서 호출하여 MemoListDialog에서 사용할 수 있도록 합니다.
     */
    fun provideMemoViewModel(): MemoViewModel {
        return memoViewModel
    }

    // LocationViewModel 상태 관찰 메서드 추가
    private fun observeLocationState() {
        viewLifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                locationViewModel.locationState.collect { state ->
                    // 위치 상태 처리
                    currentUserLocation = state.currentLocation
                    
                    // 위치 정보 업데이트 시 지오해시 업데이트
                    if (state.geohash != currentLocationGeohash) {
                        currentLocationGeohash = state.geohash
                        currentLocationNeighbors = state.neighborGeohashes
                        updateGeohashCircle(state.currentLocation)
                    }
                    
                    // 에러 처리
                    state.lastError?.let { error ->
                        showError("위치 서비스 오류: ${error.localizedMessage}")
                    }
                }
            }
        }
    }

    /**
     * 일반 스낵바를 표시합니다.
     */
    private fun showSnackbar(message: String) {
        try {
            binding.root.let { safeView ->
                val snackbar = Snackbar.make(safeView, message, Snackbar.LENGTH_LONG)
                
                // 텍스트 색상 설정
                snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                    ?.setTextColor(Color.WHITE)

                // 스낵바 위치를 지도 중간으로 조정 (화면 하단에서 위로 이동)
                snackbar.view.translationY = -(150f.dp2px(resources))
                
                // 스낵바 표시
                snackbar.show()
                Timber.e("일반 스낵바 표시 완료")
            }
        } catch (e: Exception) {
            Timber.e(e, "스낵바 표시 중 오류 발생")
        }
    }

    /**
     * 확인 액션이 있는 스낵바를 표시합니다.
     */
    private fun showConfirmationSnackbar(
        message: String,
        actionText: String,
        onAction: () -> Unit
    ) {
        try {
            binding.root.let { safeView ->
                val snackbar = Snackbar.make(safeView, message, Snackbar.LENGTH_LONG)
                    .setAction(actionText) { onAction() }
                    .setActionTextColor(ContextCompat.getColor(requireContext(), R.color.teal_200))

                // 버튼 스타일 커스터마이징 (선택사항)
                snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                    ?.setTextColor(Color.WHITE)
                
                // 스낵바 위치를 지도 중간으로 조정 (화면 하단에서 위로 이동)
                snackbar.view.translationY = -(150f.dp2px(resources))
                
                // 스낵바 표시
                snackbar.show()
                Timber.e("확인 스낵바 표시 완료")
            }
        } catch (e: Exception) {
            Timber.e(e, "스낵바 표시 중 오류 발생")
        }
    }

    /**
     * Android 13(API 33) 이상에서 알림 권한 확인 및 요청
     */
    private fun checkAndRequestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val notificationPermission = android.Manifest.permission.POST_NOTIFICATIONS
            val permissionStatus = context?.checkSelfPermission(notificationPermission)
            
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                Timber.d("알림 권한 요청 시작")
                notificationPermissionRequest.launch(notificationPermission)
            } else {
                Timber.d("알림 권한이 이미 허용되어 있음")
            }
        } else {
            Timber.d("Android 13 미만 버전에서는 알림 권한 요청이 필요하지 않음")
        }
    }

    private fun setupMapUI(height: Int) {
        try {
            // 지도가 초기화되지 않은 경우 빠른 종료
            if (naverMap == null) {
                Timber.d("지도가 아직 초기화되지 않아 UI 설정을 건너뜁니다.")
                return
            }
            
            Timber.d("지도 UI 설정 시작")
            
            // 모드바 변경으로 인한 지도 패딩 조정
            val offset = context?.resources?.getDimensionPixelSize(R.dimen.map_padding) ?: 0
            
            // 지도에 패딩 설정
            naverMap?.let { map ->
                map.setContentPadding(offset, height + offset, offset, offset)
                Timber.d("지도 패딩 설정: top=${height + offset}, left/right/bottom=$offset")
                
                // 내 위치 버튼과 나침반 기능 설정은 MapConfigDelegate로 위임
                
                // 위치 추적 버튼 초기화
                if (!locationPermissionController.hasLocationPermission()) {
                    Timber.d("위치 권한 없음 - 위치 버튼 비활성화")
                    mapConfigDelegate.disableLocationTracking()
                }
            }
            Timber.d("지도 UI 설정 완료")
        } catch (e: Exception) {
            Timber.e(e, "지도 UI 설정 중 오류 발생")
        }
    }

    private fun handleMyLocationButtonClick() {
        try {
            if (locationPermissionController.hasLocationPermission()) {
                // ... existing code ...
            }
        } catch (e: Exception) {
            Timber.e(e, "위치 버튼 클릭 중 오류 발생")
        }
    }

    // 위치 권한 거부 메시지 표시
    private fun showLocationPermissionDeniedMessage() {
        binding.root.let { view ->
            Snackbar.make(view, R.string.location_permission_denied, Snackbar.LENGTH_LONG)
                .setAction(R.string.settings) {
                    locationViewModel.openAppSettings()
                }
                .show()
        }
    }
    
    // 위치 서비스 비활성화 메시지 표시 (안드로이드 10 이상 대응)
    private fun showLocationServiceDisabledMessage() {
        binding.root.let { view ->
            Snackbar.make(view, "위치 서비스가 꺼져 있습니다. 위치 서비스를 켜주세요.", Snackbar.LENGTH_LONG)
                .setAction("설정") {
                    try {
                        // 위치 설정 페이지로 이동
                        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        startActivity(intent)
                    } catch (e: Exception) {
                        Timber.e(e, "위치 설정 페이지 열기 실패")
                    }
                }
                .show()
        }
    }

    // 위치 권한 및 서비스 체크
    private fun checkLocationPermissionAndServices() {
        try {
            // 안드로이드 10 이상에서는 위치 서비스 활성화 여부도 확인
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                val isLocationEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) || 
                                        locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
                
                if (!isLocationEnabled) {
                    Timber.w("위치 서비스가 비활성화되어 있습니다.")
                    showLocationServiceDisabledMessage()
                    return
                }
            }
            
            // 위치 권한 확인 및 요청
            checkLocationPermissionAndEnableTracking()
        } catch (e: Exception) {
            Timber.e(e, "위치 권한/서비스 확인 중 오류 발생")
        }
    }

    /**
     * 맵 상태에 따라 UI 업데이트
     */
    private fun updateMapBasedOnState(state: MapState.Success) {
        // 현재 위치가 있으면 지도 이동 (필요시)
        if (state.currentLocation.latitude != 0.0 && state.currentLocation.longitude != 0.0) {
            // 실제 구현에서는 필요한 경우에만 카메라 이동
            Timber.d("현재 위치 정보 업데이트: ${state.currentLocation}")
        }
        
        // 추가: MapUIController를 통한 UI 업데이트
        mapUIController.updateUI(state)
    }
    
    /**
     * 마커 상태에 따라 UI 업데이트
     */
    private fun updateMarkersBasedOnState(state: MapState.Success) {
        // 마커 UI 업데이트
        markerUIDelegate.updateMarkers(state.markers)
        
        // 선택된 마커가 있으면 UI에 표시
        if (state.selectedMarker != null) {
            Timber.d("선택된 마커 업데이트: ${state.selectedMarker.id}")
            // 필요한 경우 선택된 마커 UI 처리
        }
    }

    /**
     * MapError 타입의 오류를 처리하고 적절한 오류 메시지를 표시합니다.
     */
    private fun handleError(error: MapError) {
        // MapUIController로 에러 처리 위임
        mapUIController.showError(error)
    }

    /**
     * 화면 전환 시 마커 상태를 보존합니다.
     * 마커 풀링을 통해 화면 회전 후에도 마커가 깜박이지 않도록 합니다.
     */
    private fun preserveMarkersOnConfigChange() {
        if (naverMap == null) return
        Timber.d("화면 전환 시 마커 상태 보존")
        
        val markersToPreserve = markerUIDelegate.getMarkers()
        Timber.d("보존할 마커 수: ${markersToPreserve.size}개")
        
        // 마커 풀 상태 로깅
        viewModel.logMarkerPoolStatus()
        
        // 화면 전환 후 즉시 마커를 모두 표시
        // 마커 풀에서 마커를 재사용하므로 화면 전환 시 깜박임 방지
        if (markersToPreserve.isNotEmpty()) {
            // 즉시 마커 표시 트리거
            viewModel.forceRefreshMarkers()
        }
    }
    
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        Timber.d("화면 구성 변경됨")
        preserveMarkersOnConfigChange()
    }

    // 메모 다이얼로그 표시 함수
    private fun showMemoListDialog(markerId: String) {
        try {
            Timber.d("마커 메모 다이얼로그 표시 요청: markerId=$markerId")
            
            // 비동기적으로 마커 데이터 로드 및 메모장 표시
            viewLifecycleScope.launch {
                try {
                    // 먼저 마커가 임시 마커인지 확인
                    val isTemporary = viewModel.isTemporaryMarker(markerId)
                    Timber.d("마커 상태 확인: markerId=$markerId, 임시 마커 여부=$isTemporary")
                    
                    // 임시 마커일 경우 메모리에서 가져오는 방식 우선 시도 후 빈 마커 객체 사용
                    if (isTemporary) {
                        Timber.d("임시 마커 확인됨. 먼저 메모리에서 가져오기 시도: $markerId")
                        
                        // 1. 먼저 메모리에서 마커 찾기 시도
                        val markerFromMemory = try {
                            // 마커 매니저에서 직접 찾기
                            viewModel.markerManager.markers.value.find { it.id == markerId } ?:
                            // 모든 마커를 한 번 더 확인
                            viewModel.markerManager.getMarkerById(markerId)
                        } catch (e: Exception) {
                            Timber.e(e, "임시 마커 메모리 검색 중 오류: $markerId")
                            null
                        }
                        
                        if (markerFromMemory != null) {
                            Timber.d("임시 마커를 메모리에서 찾음: $markerId")
                            showMarkerDetail(markerFromMemory, true)
                            return@launch
                        }
                        
                        // 2. 메모리에서 찾지 못한 경우 빈 마커 객체 사용
                        Timber.d("임시 마커를 메모리에서 찾지 못해 빈 마커 객체 사용: $markerId")
                        val emptyMarker = com.parker.hotkey.domain.model.Marker(
                            id = markerId,
                            userId = viewModel.getUserId() ?: "",  // 유효한 사용자 ID 사용
                            latitude = 0.0,
                            longitude = 0.0, 
                            geohash = "",
                            modifiedAt = System.currentTimeMillis()
                        )
                        
                        showMarkerDetail(emptyMarker, true)
                        return@launch
                    }
                    
                    // 일반 마커인 경우 재시도 로직 사용 - 백업 계획도 추가
                    val marker = RetryUtil.retry(
                        maxRetries = 5,
                        initialDelayMillis = 100,
                        maxDelayMillis = 800
                    ) { attempt ->
                        // 1. 기본 방식으로 시도
                        var marker = viewModel.markerManager.getMarkerById(markerId)
                        
                        // 2. 기본 방식 실패 시 대체 방법 시도
                        if (marker == null && attempt > 0) {
                            Timber.d("기본 방식 실패, 대체 방법 시도 중 (시도 ${attempt+1}): $markerId")
                            // 모든 마커 목록에서 직접 검색
                            marker = viewModel.markerManager.markers.value.find { it.id == markerId }
                        }
                        
                        if (marker != null) {
                            Timber.d("마커 정보 로드 성공 (시도 ${attempt+1}): $markerId")
                        } else {
                            Timber.d("마커 정보 로드 시도 ${attempt+1} 실패: $markerId")
                        }
                        marker
                    }
                    
                    if (marker != null) {
                        // 마커 정보 로드 성공, 메모장 표시
                        Timber.d("마커 정보 로드 성공, 메모장 표시: $markerId")
                        showMarkerDetail(marker, isTemporary)
                    } else {
                        // 최대 재시도 후에도 마커 정보를 가져오지 못한 경우
                        Timber.e("마커 정보를 최대 재시도 후에도 로드 실패: $markerId")
                        
                        // 마커가 존재하지 않는 경우를 위한 대체 방법
                        try {
                            // 마커 매니저에서 모든 마커 목록 확인 (마지막 시도)
                            val allMarkers = viewModel.markerManager.markers.value
                            val foundMarker = allMarkers.find { it.id == markerId }
                            
                            if (foundMarker != null) {
                                // 목록에서 마커를 찾은 경우
                                Timber.d("목록에서 마커 발견, 메모장 표시: $markerId")
                                showMarkerDetail(foundMarker, isTemporary)
                                return@launch
                            }
                            
                            // 마커를 찾을 수 없어 새로고침 요청
                            Timber.d("마커를 찾을 수 없어 마커 새로고침 요청")
                            viewModel.refreshMarkersForForeground()
                            delay(300) // 새로고침 대기
                            
                            // 새로고침 후 다시 시도
                            val refreshedMarkers = viewModel.markerManager.markers.value
                            val refreshedMarker = refreshedMarkers.find { it.id == markerId }
                            
                            if (refreshedMarker != null) {
                                Timber.d("새로고침 후 마커 발견, 메모장 표시: $markerId")
                                showMarkerDetail(refreshedMarker, isTemporary)
                                return@launch
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "마커 목록 확인 중 예외 발생: $markerId")
                        }
                        
                        // 최종 대안: 빈 마커 객체 생성하여 메모장은 표시
                        val emptyMarker = com.parker.hotkey.domain.model.Marker(
                            id = markerId,
                            userId = viewModel.getUserId() ?: "",
                            latitude = 0.0,
                            longitude = 0.0, 
                            geohash = "",
                            modifiedAt = System.currentTimeMillis()
                        )
                        
                        showMarkerDetail(emptyMarker, isTemporary)
                        
                        // 오류 메시지 표시
                        showError("마커 정보를 불러오는데 문제가 발생했습니다.")
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Timber.e(e, "메모장 표시 중 오류 발생: $markerId")
                    showError("메모장을 표시할 수 없습니다.")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "메모장 표시 함수 실행 중 오류 발생: $markerId")
            showError("메모장을 표시할 수 없습니다.")
        }
    }

    companion object {
        fun newInstance(): MapFragment {
            return MapFragment()
        }
        
        /**
         * 스태틱 참조 정리 메서드
         * Fragment가 완전히 정리되었을 때 호출
         */
        fun clearStaticReferences() {
            try {
                // 필요한 정적 변수가 있다면 명시적으로 null 설정
                // 예: staticInstance = null
                
                Timber.d("MapFragment 스태틱 참조 정리 완료")
            } catch (e: Exception) {
                Timber.e(e, "스태틱 참조 정리 중 오류 발생")
            }
        }
    }
    
    /**
     * 약한 참조 패턴을 적용한 싱글톤 클래스의 리소스 정리
     */
    private fun cleanupWeakReferences() {
        Timber.d("싱글톤 클래스의 약한 참조 정리")
        
        // 각 싱글톤 클래스의 정리 메서드 호출
        memoryWatchdog.cleanup()
        connectionStateMonitor.cleanup()
        sharedPrefsManager.cleanup()
        userPreferencesManager.cleanup()
    }

    /**
     * FragmentManager에 등록된 콜백 목록을 가져오는 확장 함수
     * 리플렉션 대신 등록된 콜백을 직접 관리하는 방식으로 변경
     */
    private fun FragmentManager.getFragmentManagerCallbacks(): List<FragmentManager.FragmentLifecycleCallbacks> {
        // NOTE: 안드로이드 SDK에서는 등록된 콜백 목록을 직접 가져오는 공식 API가 없음
        // 앱에서 직접 콜백을 등록/추적하는 방식으로 변경하거나, 필요없는 경우 빈 목록 반환
        return emptyList()
    }

    /**
     * 앱이 포그라운드로 돌아왔을 때 호출되는 메서드
     * 편집 모드 상태를 유지하도록 처리
     */
    fun onAppForeground() {
        try {
            // EditModeManager에 앱이 포그라운드로 돌아왔음을 알림
            viewModel.editModeManager.onAppForeground()
            Timber.d("MapFragment - 앱이 포그라운드로 돌아옴, EditModeManager에 알림")
        } catch (e: Exception) {
            Timber.e(e, "EditModeManager 호출 중 오류 발생")
        }
    }

    // MainActivity에서 호출하는 비동기 초기화 및 업데이트 메서드들
    
    /**
     * 지도를 비동기적으로 초기화하는 메서드
     * MainActivity에서 호출됩니다.
     */
    suspend fun initializeMapAsync() {
        Timber.d("MapFragment: 지도 비동기 초기화 시작")
        
        if (naverMap == null) {
            Timber.d("MapFragment: NaverMap이 초기화되지 않았습니다. 초기화 대기 중...")
            
            // NaverMap이 준비될 때까지 최대 5초 대기
            val result = withTimeoutOrNull(5000L) {
                while (naverMap == null) {
                    delay(100)
                }
                true
            }
            
            if (result == null) {
                Timber.e("MapFragment: NaverMap 초기화 타임아웃")
            }
        }
        
        // UI 스레드에서 작업 실행
        withContext(Dispatchers.Main) {
            naverMap?.let { map ->
                // 지도 UI 관련 설정 적용
                mapConfigDelegate.applyMapSettings(map)
                Timber.d("MapFragment: 지도 설정 적용 완료")
            }
        }
    }
    
    /**
     * 마커를 비동기적으로 로드하는 메서드
     * MainActivity에서 호출됩니다.
     */
    suspend fun loadMarkersAsync() {
        Timber.d("MapFragment: 마커 비동기 로딩 시작")
        
        // 현재 위치의 geohash 정보가 있으면 해당 지역의 마커 로드
        currentLocationGeohash?.let { geohash ->
            viewModel.loadMarkersInArea(geohash, currentLocationNeighbors)
            Timber.d("MapFragment: 지역 마커 로딩 요청 완료 - geohash: $geohash, 인접 지역: ${currentLocationNeighbors.size}개")
        }
    }
    
    /**
     * 위치 서비스를 비동기적으로 초기화하는 메서드
     * MainActivity에서 호출됩니다.
     */
    suspend fun initializeLocationAsync() {
        Timber.d("MapFragment: 위치 서비스 비동기 초기화 시작")
        
        // 위치 권한 확인 및 위치 추적 활성화
        withContext(Dispatchers.Main) {
            locationPermissionController.checkPermission()
            Timber.d("MapFragment: 위치 권한 확인 완료")
        }
    }
    
    /**
     * 지도를 비동기적으로 업데이트하는 메서드
     * MainActivity에서 메뉴에서 지도로 돌아올 때 호출됩니다.
     */
    suspend fun updateMapAsync() {
        Timber.d("MapFragment: 지도 비동기 업데이트 시작")
        
        withContext(Dispatchers.Main) {
            naverMap?.let { map ->
                // 카메라 위치 업데이트 (현재 위치로 이동)
                currentUserLocation?.let { location ->
                    val cameraUpdate = CameraUpdate.scrollTo(location)
                        .animate(CameraAnimation.Easing)
                    map.moveCamera(cameraUpdate)
                    Timber.d("MapFragment: 카메라 위치 업데이트 완료 - 위치: $location")
                }
            }
        }
    }
    
    /**
     * 마커를 비동기적으로 업데이트하는 메서드
     * MainActivity에서 메뉴에서 지도로 돌아올 때 호출됩니다.
     */
    suspend fun updateMarkersAsync() {
        Timber.d("MapFragment: 마커 비동기 업데이트 시작")
        
        // AppStateManager의 상태 확인
        val appStateManager = (requireActivity() as? MainActivity)?.appStateManager
        val isNavigationReturn = appStateManager?.isNavigationReturn() == true
        
        if (isNavigationReturn) {
            // 네비게이션 복귀 상태인 경우 API 호출 없이 기존 마커만 표시
            Timber.d("MapFragment: 네비게이션 복귀 상태 - API 호출 없이 기존 마커만 표시")
            return
        }
        
        // 현재 위치의 geohash 정보가 있으면 해당 지역의 마커 리로드
        currentLocationGeohash?.let { geohash ->
            viewModel.reloadMarkersInArea(geohash, currentLocationNeighbors)
            Timber.d("MapFragment: 지역 마커 업데이트 요청 완료 - geohash: $geohash, 인접 지역: ${currentLocationNeighbors.size}개")
        }
    }
} 