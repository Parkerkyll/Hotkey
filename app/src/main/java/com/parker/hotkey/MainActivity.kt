package com.parker.hotkey

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import com.parker.hotkey.presentation.login.LoginTestActivity
import com.parker.hotkey.presentation.login.LoginViewModel
import com.parker.hotkey.presentation.main.MainViewModel
import com.parker.hotkey.presentation.map.MapViewModel
import com.parker.hotkey.presentation.map.MapFragment
import com.parker.hotkey.presentation.memo.MemoViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import java.lang.ref.WeakReference
import androidx.navigation.NavController
import com.google.android.material.snackbar.Snackbar
import android.view.View
import com.parker.hotkey.data.manager.LoadingManager
import com.parker.hotkey.data.manager.UserPreferencesManager
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textview.MaterialTextView
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import android.widget.ImageView
import android.widget.TextView
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import android.view.Window
import android.view.WindowManager
import android.view.WindowInsetsController
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import androidx.navigation.NavDestination
import com.parker.hotkey.domain.util.AppStateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    // ViewModel 인스턴스 생성
    private val loginViewModel: LoginViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()
    
    @Inject
    lateinit var userPreferencesManager: UserPreferencesManager
    
    @Inject
    lateinit var loadingManager: LoadingManager
    
    @Inject
    lateinit var memoryWatchdog: com.parker.hotkey.util.MemoryWatchdog
    
    @Inject
    lateinit var connectionStateMonitor: com.parker.hotkey.data.remote.network.ConnectionStateMonitor
    
    @Inject
    lateinit var sharedPrefsManager: com.parker.hotkey.util.SharedPrefsManager
    
    @Inject
    lateinit var appStateManager: AppStateManager
    
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var appBarConfiguration: AppBarConfiguration
    private var navController: NavController? = null
    
    private var mapViewModelWeakReference: WeakReference<MapViewModel>? = null
    private var memoViewModelWeakReference: WeakReference<MemoViewModel>? = null
    
    // 드로어 리스너를 멤버 변수로 선언
    private lateinit var drawerListener: DrawerLayout.DrawerListener
    
    // 윈도우 인셋 리스너 저장 변수 추가
    private var windowInsetsListener: androidx.core.view.OnApplyWindowInsetsListener? = null

    // 백그라운드에서 포그라운드로 돌아왔는지 확인하는 변수
    private var wasInBackground = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 레이아웃 설정
        setContentView(R.layout.activity_main)
        
        // LoadingManager에 Activity context 설정 및 초기화 (뷰가 준비된 후에 호출)
        Timber.d("MainActivity: LoadingManager 초기화 시작")
        loadingManager.setActivityContext(this)
        
        // 로딩 뷰가 성공적으로 초기화되었는지 확인하기 위한 테스트 로딩 표시
        lifecycleScope.launch {
            try {
                Timber.d("MainActivity: 로딩 화면 테스트 시작")
                delay(300) // 약간의 지연 후 시작 (뷰가 완전히 준비되도록)
                
                // 앱 시작 시 로딩 화면이 정상적으로 표시되는지 확인
                loadingManager.showLoading("초기화 테스트 중...")
                
                // 2초 동안 표시 (로딩 화면이 제대로 보이는지 확인)
                Timber.d("MainActivity: 로딩 화면 테스트 표시 중 (2초)")
                delay(2000)
                
                // 테스트 로딩 숨김
                Timber.d("MainActivity: 로딩 화면 테스트 종료")
                loadingManager.hideLoading(lifecycleScope)
            } catch (e: Exception) {
                Timber.e(e, "로딩 화면 초기화 테스트 중 오류 발생")
            }
        }
        
        // 권한 체크 및 요청
        checkAndRequestPermissions()
        
        // Edge-to-edge 설정
        enableEdgeToEdge()
        
        // Edge-to-edge 설정 후 상태 표시줄 색상을 흰색으로 설정
        window.statusBarColor = ContextCompat.getColor(this, R.color.white)
        
        // 상태 표시줄 아이콘 색상을 어두운 색으로 설정 (흰색 배경에서 보이게)
        setStatusBarColor()
        
        // WindowInsetsListener를 멤버 변수로 저장하여 나중에 제거 가능하도록 함
        windowInsetsListener = androidx.core.view.OnApplyWindowInsetsListener { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        // 저장한 리스너 설정
        findViewById<View>(R.id.main)?.let { view ->
            ViewCompat.setOnApplyWindowInsetsListener(view, windowInsetsListener)
        }

        // 네비게이션 드로어 설정
        setupNavigationDrawer()

        // 로그인 상태 관찰 설정
        observeLoginState()
        
        // 네비게이션 상태 관찰 설정
        observeNavigationState()
        
        // 드로어 상태 관찰 설정
        observeDrawerState()
        
        // 포그라운드 전환 이벤트 관찰 시작
        observeForegroundTransitions()
        
        // 자동 로그인 체크 실행
        loginViewModel.checkAutoLogin()

        // 뒤로가기 처리
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // ViewModel에 뒤로가기 이벤트 위임
                if (!mainViewModel.onBackPressed()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        
        // 인텐트 처리
        handleIntent(intent)
        
        // 도움말 안내 팝업 표시 (필요한 경우에만)
        showHelpGuideIfNeeded()

        // 약한 참조 패턴 설정
        setupWeakReferences()

        // 해시키 출력 코드 추가
        try {
            val info = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            val signatures = info.signingInfo.apkContentsSigners
            for (signature in signatures) {
                val md = java.security.MessageDigest.getInstance("SHA")
                md.update(signature.toByteArray())
                val hashKey = android.util.Base64.encodeToString(md.digest(), android.util.Base64.DEFAULT)
                android.util.Log.d("HashKey", "해시키: $hashKey")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun onStart() {
        super.onStart()
        
        // 앱이 백그라운드에서 포그라운드로 돌아올 때의 처리는 이제 observeForegroundTransitions에서 담당
        // AppStateManager가 이벤트를 발행하면 observeForegroundTransitions에서 수신하여 처리함
        Timber.d("onStart: 포그라운드 전환 이벤트는 AppStateManager에서 처리됨")
    }
    
    override fun onStop() {
        super.onStop()
        // 앱이 백그라운드로 이동할 때 플래그 설정 (AppStateManager에서도 처리하지만 안전장치로 유지)
        wasInBackground = true
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: Intent) {
        // 드로어 열기 인텐트 확인
        if (intent.getBooleanExtra("OPEN_DRAWER", false)) {
            // 드로어 열기
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    /**
     * LoginViewModel의 상태를 관찰하는 메서드
     */
    private fun observeLoginState() {
        lifecycleScope.launch {
            loginViewModel.loginState.collect { state ->
                when (state) {
                    is LoginViewModel.LoginState.NotLoggedIn, 
                    is LoginViewModel.LoginState.Error -> {
                        Timber.d("로그인 필요: $state")
                        startLoginActivity()
                    }
                    is LoginViewModel.LoginState.Success -> {
                        Timber.d("로그인 성공: 토큰 정상")
                        // 로그인 성공 시 로딩 화면 표시 및 초기화 작업 시작
                        showLoadingAndInitMap()
                    }
                    else -> {
                        // 초기 상태나 로딩 상태는 특별한 처리가 필요 없음
                    }
                }
            }
        }
    }
    
    /**
     * 로딩 화면을 표시하고 지도 초기화 작업을 병렬로 수행하는 메서드
     */
    private fun showLoadingAndInitMap() {
        // 로딩 화면 표시
        loadingManager.showLoading("지도 준비중")
        
        // 병렬로 초기화 작업 수행
        lifecycleScope.launch {
            try {
                // 병렬로 여러 작업 실행
                val mapInitJob = async { initializeMap() }
                val markersJob = async { loadMarkers() }
                val locationJob = async { initializeLocation() }
                
                // 모든 작업 완료 대기
                awaitAll(mapInitJob, markersJob, locationJob)
                
                // 모든 작업 완료 후 로딩 화면 숨김
                loadingManager.hideLoading(lifecycleScope)
                
                Timber.d("모든 초기화 작업 완료")
            } catch (e: Exception) {
                Timber.e(e, "초기화 작업 중 오류 발생")
                // 오류 발생 시에도 로딩 화면 숨김
                loadingManager.hideLoading(lifecycleScope)
            }
        }
    }
    
    /**
     * 지도 초기화 작업을 수행하는 메서드
     */
    private suspend fun initializeMap() {
        Timber.d("지도 초기화 작업 시작")
        // 실제 지도 초기화 작업 - MapFragment에 위임
        val mapFragment = getCurrentFragmentAsMapFragment()
        mapFragment?.initializeMapAsync()
        
        // 예시를 위한 딜레이 (실제 코드에서는 제거하거나 실제 작업으로 대체)
        kotlinx.coroutines.delay(500)
        Timber.d("지도 초기화 작업 완료")
    }
    
    /**
     * 마커 로딩 작업을 수행하는 메서드
     */
    private suspend fun loadMarkers() {
        Timber.d("마커 로딩 작업 시작")
        // 실제 마커 로딩 작업 - MapFragment에 위임
        val mapFragment = getCurrentFragmentAsMapFragment()
        mapFragment?.loadMarkersAsync()
        
        // 예시를 위한 딜레이 (실제 코드에서는 제거하거나 실제 작업으로 대체)
        kotlinx.coroutines.delay(800)
        Timber.d("마커 로딩 작업 완료")
    }
    
    /**
     * 위치 초기화 작업을 수행하는 메서드
     */
    private suspend fun initializeLocation() {
        Timber.d("위치 초기화 작업 시작")
        // 실제 위치 초기화 작업 - MapFragment에 위임
        val mapFragment = getCurrentFragmentAsMapFragment()
        mapFragment?.initializeLocationAsync()
        
        // 예시를 위한 딜레이 (실제 코드에서는 제거하거나 실제 작업으로 대체)
        kotlinx.coroutines.delay(300)
        Timber.d("위치 초기화 작업 완료")
    }
    
    /**
     * 현재 활성화된 프래그먼트를 MapFragment로 캐스팅하여 반환하는 메서드
     */
    private fun getCurrentFragmentAsMapFragment(): MapFragment? {
        val currentFragment = getCurrentFragment()
        return if (currentFragment is MapFragment) currentFragment else null
    }
    
    /**
     * 현재 활성화된 프래그먼트를 반환하는 메서드
     */
    private fun getCurrentFragment(): Fragment? {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        return navHostFragment?.childFragmentManager?.primaryNavigationFragment
    }
    
    /**
     * 네비게이션 상태와 이벤트를 관찰하는 메서드
     * MainViewModel의 이벤트와 NavController의 상태 변경을 모두 처리
     */
    private fun observeNavigationState() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        
        // 네비게이션 컨트롤러에 대한 이동 리스너 설정
        navController?.addOnDestinationChangedListener { _, destination, _ ->
            Timber.d("네비게이션 대상 변경: ${destination.label}")
            handleDestinationChanged(destination)
        }
        
        // MainViewModel의 네비게이션 이벤트 관찰
        lifecycleScope.launch {
            mainViewModel.navigationEvent.collect { event ->
                val navController = (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController
                
                // 현재 프래그먼트 처리
                val currentFragment = getCurrentFragment()
                if (currentFragment is MapFragment) {
                    // MapFragment에서 다른 화면으로 이동할 때 메모리 정리
                    try {
                        // 이미 MapFragment인 경우 메모리 정리 수행
                        Timber.d("MapFragment 메모리 정리 시작")
                        currentFragment.destroyMapView()
                        com.parker.hotkey.presentation.map.MapFragment.clearStaticReferences()
                        Timber.d("MapFragment 메모리 정리 완료")
                    } catch (e: Exception) {
                        Timber.e(e, "MapFragment 메모리 정리 중 오류 발생")
                    }
                }
                
                when (event) {
                    is MainViewModel.NavigationEvent.NavigateToNotice -> {
                        navController.navigate(R.id.nav_notice)
                        // 네비게이션 후 드로어 닫기
                        drawerLayout.closeDrawer(GravityCompat.START)
                    }
                    is MainViewModel.NavigationEvent.NavigateToHelp -> {
                        navController.navigate(R.id.nav_help)
                        // 네비게이션 후 드로어 닫기
                        drawerLayout.closeDrawer(GravityCompat.START)
                    }
                    is MainViewModel.NavigationEvent.NavigateToProfile -> {
                        navController.navigate(R.id.nav_profile)
                        // 네비게이션 후 드로어 닫기
                        drawerLayout.closeDrawer(GravityCompat.START)
                    }
                    is MainViewModel.NavigationEvent.NavigateToReportBug -> {
                        navController.navigate(R.id.nav_report_bug)
                        // 네비게이션 후 드로어 닫기
                        drawerLayout.closeDrawer(GravityCompat.START)
                    }
                    is MainViewModel.NavigationEvent.NavigateToMap -> {
                        // 로딩 화면 표시
                        loadingManager.showLoading("지도 준비중")
                        
                        // 현재 프래그먼트 처리
                        val mapFragmentInstance = getCurrentFragment()
                        if (mapFragmentInstance is MapFragment) {
                            try {
                                // 이미 MapFragment인 경우 메모리 정리 수행
                                Timber.d("MapFragment 메모리 정리 시작")
                                mapFragmentInstance.destroyMapView()
                                com.parker.hotkey.presentation.map.MapFragment.clearStaticReferences()
                                Timber.d("MapFragment 메모리 정리 완료")
                            } catch (e: Exception) {
                                Timber.e(e, "MapFragment 메모리 정리 중 오류 발생")
                            }
                        }
                        
                        // 네비게이션 상태 업데이트 - 지도로 돌아감을 명시적으로 알림
                        try {
                            // 명확하게 NAVIGATION_RETURN 상태로 설정 (API 호출 방지)
                            appStateManager.checkNavigationReturn(R.id.mapFragment, R.id.mapFragment)
                            val isNavigationReturn = appStateManager.isNavigationReturn()
                            Timber.d("네비게이션 상태 업데이트: 지도로 돌아감, NAVIGATION_RETURN 상태: $isNavigationReturn")
                            
                            // 모든 API 호출 차단을 위한 추가 로그
                            if (isNavigationReturn) {
                                Timber.d("지도로 돌아가기: 모든 API 호출 차단됨, 리셋 타이머 ${AppStateManager.NAVIGATION_RETURN_RESET_DELAY_MS / 1000}초")
                            } else {
                                Timber.w("지도로 돌아가기: 네비게이션 복귀 상태 설정 실패")
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "네비게이션 상태 업데이트 중 오류 발생")
                        }
                        
                        // 지도로 이동
                        navController.navigate(R.id.mapFragment)
                        drawerLayout.closeDrawer(GravityCompat.START)
                        
                        // 지도 업데이트 작업 실행
                        lifecycleScope.launch {
                            try {
                                // 네비게이션 복귀 상태 확인
                                if (appStateManager.isNavigationReturn()) {
                                    Timber.d("네비게이션 복귀 상태에서 지도 업데이트 작업 최소화")
                                    // 최소한의 UI 업데이트만 수행
                                    updateMapUI()
                                    // 로딩 화면 숨김
                                    loadingManager.hideLoading(lifecycleScope)
                                } else {
                                    // 일반적인 지도 업데이트 작업 수행
                                    val mapUpdateJob = async { updateMap() }
                                    val markersUpdateJob = async { updateMarkers() }
                                    
                                    // 모든 작업 완료 대기
                                    awaitAll(mapUpdateJob, markersUpdateJob)
                                    
                                    // 모든 작업 완료 후 로딩 화면 숨김
                                    loadingManager.hideLoading(lifecycleScope)
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "지도 업데이트 작업 중 오류 발생")
                                // 오류 발생 시에도 로딩 화면 숨김
                                loadingManager.hideLoading(lifecycleScope)
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 네비게이션 대상 변경을 처리하는 메서드
     */
    private fun handleDestinationChanged(destination: NavDestination) {
        try {
            // 맵 프래그먼트 ID 확인
            val mapFragmentId = R.id.mapFragment
            
            // 현재 프래그먼트 ID 확인
            val currentFragmentId = destination.id
            
            // AppStateManager에 네비게이션 상태 알림
            Timber.d("네비게이션 상태 업데이트: currentFragmentId=$currentFragmentId, mapFragmentId=$mapFragmentId")
            appStateManager.checkNavigationReturn(currentFragmentId, mapFragmentId)
            
            // 현재 프래그먼트가 맵 프래그먼트인 경우 추가 처리
            if (currentFragmentId == mapFragmentId) {
                Timber.d("맵 프래그먼트로 네비게이션 됨")
                // 추가 처리 필요 시 구현
            }
        } catch (e: Exception) {
            Timber.e(e, "네비게이션 대상 변경 처리 중 오류 발생")
        }
    }
    
    /**
     * 지도 업데이트 작업을 수행하는 메서드
     */
    private suspend fun updateMap() {
        Timber.d("지도 업데이트 작업 시작")
        
        // AppStateManager의 상태 확인
        val isNavigationReturn = appStateManager.isNavigationReturn()
        
        // 실제 지도 업데이트 작업 - MapFragment에 위임
        val mapFragment = getCurrentFragmentAsMapFragment()
        
        if (isNavigationReturn) {
            // 네비게이션 복귀 상태인 경우 - 카메라 이동 없이 지도만 초기화
            Timber.d("지도 업데이트: 네비게이션 복귀 상태 - 최소한의 업데이트만 수행")
            // 지도 UI 관련 초기화만 수행 (API 호출 없음)
            mapFragment?.initializeMapAsync()
        } else {
            // 일반 상태인 경우 - 기존 업데이트 로직 수행
            mapFragment?.updateMapAsync()
        }
        
        // 예시를 위한 딜레이 (실제 코드에서는 제거하거나 실제 작업으로 대체)
        kotlinx.coroutines.delay(300)
        Timber.d("지도 업데이트 작업 완료")
    }
    
    /**
     * 마커 업데이트 작업을 수행하는 메서드
     */
    private suspend fun updateMarkers() {
        Timber.d("마커 업데이트 작업 시작")
        
        // AppStateManager의 상태 확인
        val isNavigationReturn = appStateManager.isNavigationReturn()
        
        // 실제 마커 업데이트 작업 - MapFragment에 위임
        val mapFragment = getCurrentFragmentAsMapFragment()
        
        if (isNavigationReturn) {
            // 네비게이션 복귀 상태인 경우 - API 호출 없음
            Timber.d("마커 업데이트: 네비게이션 복귀 상태 - API 호출 생략")
            // 기존에 로드된 마커 정보만 표시 (선택적으로 필요한 경우)
            // 여기서는 updateMarkersAsync에서 이미 처리되므로 추가 작업 없음
        } else {
            // 일반 상태인 경우 - 기존 업데이트 로직 수행
            mapFragment?.updateMarkersAsync()
        }
        
        // 예시를 위한 딜레이 (실제 코드에서는 제거하거나 실제 작업으로 대체)
        kotlinx.coroutines.delay(500)
        Timber.d("마커 업데이트 작업 완료")
    }
    
    /**
     * MainViewModel의 드로어 상태를 관찰하는 메서드
     */
    private fun observeDrawerState() {
        lifecycleScope.launch {
            mainViewModel.isDrawerOpen.collect { isOpen ->
                if (isOpen && !drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.openDrawer(GravityCompat.START)
                } else if (!isOpen && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
            }
        }
    }

    private fun setupNavigationDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        // 앱바 설정
        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.mapFragment), drawerLayout
        )
        
        // 네비게이션뷰와 컨트롤러 연결
        navView.setupWithNavController(navController)
        
        // 헤더 뷰 참조 가져오기 및 사용자 정보 설정
        val headerView = navView.getHeaderView(0)
        val headerImage = headerView.findViewById<ImageView>(R.id.nav_header_image)
        val headerTitle = headerView.findViewById<TextView>(R.id.nav_header_title)
        val headerSubtitle = headerView.findViewById<TextView>(R.id.nav_header_subtitle)

        // 앱 로고 설정
        headerImage.setImageResource(R.drawable.hotkey_logo)
        
        // 앱 이름 설정
        headerTitle.text = getString(R.string.app_name)
        
        // 앱 버전 정보 설정
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            headerSubtitle.text = "Version $versionName"
        } catch (e: Exception) {
            Timber.e(e, "패키지 정보 가져오기 실패")
            headerSubtitle.text = "Version 1.0" // 기본값
        }

        // 메뉴 아이템 클릭 리스너 설정
        navView.setNavigationItemSelectedListener { menuItem ->
            // 드로어를 여기서 닫지 않고 네비게이션 이벤트 처리 후 닫음
            Timber.d("메뉴 아이템 클릭: ${menuItem.title}")
            
            when (menuItem.itemId) {
                R.id.nav_notice -> {
                    mainViewModel.onNavigationItemSelected(menuItem.itemId)
                    true
                }
                R.id.nav_profile -> {
                    mainViewModel.onNavigationItemSelected(menuItem.itemId)
                    true
                }
                R.id.nav_report_bug -> {
                    mainViewModel.onNavigationItemSelected(menuItem.itemId)
                    true
                }
                R.id.nav_help -> {
                    mainViewModel.onNavigationItemSelected(menuItem.itemId)
                    true
                }
                R.id.nav_back_to_map -> {
                    // 로딩 화면 표시
                    loadingManager.showLoading("지도 준비중")
                    
                    // 현재 프래그먼트 처리
                    val currentFragment = getCurrentFragment()
                    if (currentFragment is MapFragment) {
                        try {
                            // 이미 MapFragment인 경우 메모리 정리 수행
                            Timber.d("MapFragment 메모리 정리 시작")
                            currentFragment.destroyMapView()
                            com.parker.hotkey.presentation.map.MapFragment.clearStaticReferences()
                            Timber.d("MapFragment 메모리 정리 완료")
                        } catch (e: Exception) {
                            Timber.e(e, "MapFragment 메모리 정리 중 오류 발생")
                        }
                    }
                    
                    // 네비게이션 상태 업데이트 - 지도로 돌아감을 명시적으로 알림
                    try {
                        // 명확하게 NAVIGATION_RETURN 상태로 설정 (API 호출 방지)
                        // 맵 프래그먼트 확인을 위한 로깅 추가
                        val mapFragmentId = R.id.mapFragment
                        Timber.d("네비게이션 복귀 상태 설정: mapFragmentId=$mapFragmentId")
                        
                        // 강제로 네비게이션 복귀 상태 설정 (checkNavigationReturn이 상황에 따라 상태를 설정하지 않을 수 있음)
                        appStateManager.forceNavigationReturnState()
                        val isNavigationReturn = appStateManager.isNavigationReturn()
                        
                        Timber.d("네비게이션 상태 업데이트: 지도로 돌아감, NAVIGATION_RETURN 상태: $isNavigationReturn")
                        
                        // 모든 API 호출 차단을 위한 추가 로그
                        if (isNavigationReturn) {
                            Timber.d("지도로 돌아가기: 모든 API 호출 차단됨, 리셋 타이머 ${AppStateManager.NAVIGATION_RETURN_RESET_DELAY_MS / 1000}초")
                        } else {
                            Timber.w("지도로 돌아가기: 네비게이션 복귀 상태 설정 실패")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "네비게이션 상태 업데이트 중 오류 발생")
                    }
                    
                    // MainViewModel을 통해 지도로 이동 (다른 프래그먼트들과 동일한 방식으로 처리)
                    mainViewModel.navigateToMap()
                    
                    // 드로어 닫기
                    drawerLayout.closeDrawer(GravityCompat.START)
                    
                    true
                }
                else -> false
            }
        }

        // 드로어 리스너 설정
        drawerListener = object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: android.view.View, slideOffset: Float) {}
            
            override fun onDrawerOpened(drawerView: android.view.View) {
                mainViewModel.openDrawer()
            }
            
            override fun onDrawerClosed(drawerView: android.view.View) {
                mainViewModel.closeDrawer()
            }
            
            override fun onDrawerStateChanged(newState: Int) {}
        }
        
        drawerLayout.addDrawerListener(drawerListener)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
    
    private fun startLoginActivity() {
        val intent = Intent(this, LoginTestActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    /**
     * 스낵바 메시지를 표시합니다.
     */
    private fun showSnackbar(message: String) {
        val rootView = findViewById<View>(android.R.id.content)
        Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT).show()
    }

    /**
     * MapViewModel 인스턴스를 반환합니다.
     */
    fun getMapViewModel(): MapViewModel? {
        // 캐시된 약한 참조 확인
        mapViewModelWeakReference?.get()?.let { return it }
        
        try {
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment ?: return null
            val currentFragment = navHostFragment.childFragmentManager.primaryNavigationFragment
            
            if (currentFragment != null && currentFragment.javaClass.simpleName.contains("MapFragment")) {
                val viewModel = (currentFragment as? androidx.lifecycle.ViewModelStoreOwner)?.let { owner ->
                    androidx.lifecycle.ViewModelProvider(owner)[MapViewModel::class.java]
                }
                
                // 약한 참조로 저장
                if (viewModel != null) {
                    mapViewModelWeakReference = WeakReference(viewModel)
                }
                
                return viewModel
            }
        } catch (e: Exception) {
            Timber.e(e, "MapViewModel 가져오기 실패")
        }
        
        return null
    }

    /**
     * MemoViewModel 인스턴스를 반환합니다.
     */
    fun getMemoViewModel(): MemoViewModel? {
        // 캐시된 약한 참조 확인
        memoViewModelWeakReference?.get()?.let { return it }
        
        try {
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) ?: return null
            val fragment = navHostFragment.childFragmentManager.fragments.firstOrNull() ?: return null
            
            if (fragment is MapFragment) {
                val viewModel = fragment.provideMemoViewModel()
                
                // 약한 참조로 저장
                memoViewModelWeakReference = WeakReference(viewModel)
                
                return viewModel
            }
            return null
        } catch (e: Exception) {
            Timber.e(e, "MemoViewModel 반환 중 오류 발생")
            return null
        }
    }

    /**
     * 드로어를 열도록 하는 공개 메서드
     * MapFragment 및 다른 화면에서 이 메서드를 호출할 수 있음
     */
    fun openDrawer() {
        if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.openDrawer(GravityCompat.START)
            Timber.d("MainActivity: 드로어 열기 메서드 호출됨")
        }
    }

    /**
     * 드로어를 닫도록 하는 공개 메서드
     */
    fun closeDrawer() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
            Timber.d("MainActivity: 드로어 닫기 메서드 호출됨")
        }
    }

    override fun onDestroy() {
        try {
            Timber.d("MainActivity onDestroy 시작")
            
            // 윈도우 인셋 리스너 제거
            findViewById<View>(R.id.main)?.let { view ->
                windowInsetsListener?.let { _ ->
                    ViewCompat.setOnApplyWindowInsetsListener(view, null)
                }
            }
            
            // 드로어 리스너 제거
            if (::drawerLayout.isInitialized && ::drawerListener.isInitialized) {
                try {
                    drawerLayout.removeDrawerListener(drawerListener)
                    Timber.d("드로어 리스너 제거됨")
                } catch (e: Exception) {
                    Timber.e(e, "드로어 리스너 제거 중 오류 발생")
                }
            }
            
            // 프래그먼트 참조 정리
            cleanupFragmentReferences()
            
            // 약한 참조 정리
            mapViewModelWeakReference = null
            memoViewModelWeakReference = null
            
            // LoadingManager 정리
            loadingManager.cleanup()
            
            // NavController 참조 제거
            navController = null
            
            // 명시적 GC 힌트
            
            Timber.d("MainActivity onDestroy 완료")
        } catch (e: Exception) {
            Timber.e(e, "MainActivity onDestroy 중 오류 발생")
        }
        
        super.onDestroy()
    }
    
    /**
     * 프래그먼트 참조를 정리합니다.
     * Fragment 메모리 누수를 방지합니다.
     */
    private fun cleanupFragmentReferences() {
        try {
            // 현재 표시된 모든 프래그먼트에 대해 메모리 정리 요청
            val fragments = supportFragmentManager.fragments
            for (fragment in fragments) {
                if (fragment is MapFragment) {
                    // MapFragment 정적 참조 정리 메서드 호출
                    MapFragment.clearStaticReferences()
                }
            }
            
            // 프래그먼트 트랜잭션 완료 처리
            supportFragmentManager.executePendingTransactions()
            
            Timber.d("프래그먼트 참조 정리 완료")
        } catch (e: Exception) {
            Timber.e(e, "프래그먼트 참조 정리 중 오류 발생")
        }
    }

    /**
     * 약한 참조 패턴을 적용한 싱글톤 클래스에 액티비티 참조 설정
     */
    private fun setupWeakReferences() {
        Timber.d("MainActivity: 싱글톤 클래스에 약한 참조 설정")
        
        // MemoryWatchdog에 Activity 참조 및 콜백 설정
        memoryWatchdog.setActivity(this)
        memoryWatchdog.setLowMemoryCallback { isAggressive ->
            if (isAggressive) {
                Timber.w("MainActivity: 심각한 메모리 부족 상태 감지 - 공격적 메모리 정리 수행")
                // 심각한 메모리 부족 상황 처리
            } else {
                Timber.w("MainActivity: 메모리 부족 상태 감지 - 일반 메모리 정리 수행")
                // 일반적인 메모리 부족 상황 처리
            }
        }
        
        // ConnectionStateMonitor에 Activity 참조 및 콜백 설정
        connectionStateMonitor.setActivity(this)
        connectionStateMonitor.setConnectionChangedCallback { isConnected ->
            lifecycleScope.launch {
                if (!isConnected) {
                    Snackbar.make(
                        findViewById(R.id.main),
                        "네트워크 연결이 끊겼습니다. 일부 기능이 제한될 수 있습니다.",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
        
        // UserPreferencesManager에 Activity 참조 설정
        userPreferencesManager.setActivity(this)
        
        // SharedPrefsManager 설정
        sharedPrefsManager.registerPreferenceChangedCallback("theme") {
            lifecycleScope.launch {
                // 테마 변경 등 앱 설정 변경 시 UI 업데이트
                Timber.d("MainActivity: 앱 테마 설정 변경됨")
            }
        }
    }

    /**
     * 약한 참조 패턴을 적용한 싱글톤 클래스의 리소스 정리
     */
    private fun cleanupWeakReferences() {
        Timber.d("MainActivity: 싱글톤 클래스의 약한 참조 정리")
        
        // 각 싱글톤 클래스의 정리 메서드 호출
        memoryWatchdog.cleanup()
        connectionStateMonitor.cleanup()
        sharedPrefsManager.cleanup()
        userPreferencesManager.cleanup()
        
        // 윈도우 인셋 리스너 제거
        findViewById<View>(R.id.main)?.let { view ->
            windowInsetsListener?.let { _ ->
                ViewCompat.setOnApplyWindowInsetsListener(view, null)
            }
        }
        windowInsetsListener = null
    }

    /**
     * 권한 체크 및 요청 메서드
     */
    private fun checkAndRequestPermissions() {
        try {
            Timber.d("권한 체크 및 요청 시작")
            val permissions = mutableListOf<String>()
            
            // 위치 권한 체크
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            
            // 안드로이드 13(API 33) 이상에서 알림 권한 체크
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            
            // 안드로이드 10(API 29) 에서 위치 서비스 상태 확인 및 활성화 유도
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                val locationManager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
                val isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                val isNetworkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
                
                if (!isGpsEnabled && !isNetworkEnabled) {
                    Timber.d("안드로이드 10: 위치 서비스 비활성화됨, 설정 페이지로 이동 유도")
                    showLocationSettingsDialog()
                }
            }
            
            // 필요한 권한이 있으면 요청
            if (permissions.isNotEmpty()) {
                Timber.d("권한 요청: ${permissions.joinToString()}")
                permissionLauncher.launch(permissions.toTypedArray())
            } else {
                Timber.d("모든 필요 권한이 이미 허용됨")
            }
        } catch (e: Exception) {
            Timber.e(e, "권한 체크 중 오류 발생")
        }
    }
    
    // 위치 설정 다이얼로그 표시 메서드 추가
    private fun showLocationSettingsDialog() {
        try {
            // 위치 설정 다이얼로그 표시
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("위치 서비스 필요")
                .setMessage("이 앱은 위치 서비스가 활성화되어야 정상적으로 작동합니다. 위치 설정을 활성화하시겠습니까?")
                .setPositiveButton("설정") { _, _ ->
                    // 위치 설정 화면으로 이동
                    val intent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    startActivity(intent)
                }
                .setNegativeButton("취소", null)
                .setCancelable(false)
                .show()
        } catch (e: Exception) {
            Timber.e(e, "위치 설정 다이얼로그 표시 중 오류 발생")
        }
    }
    
    // 권한 요청 런처
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 위치 권한 결과 처리
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || 
                             permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        
        if (locationGranted) {
            Timber.d("위치 권한 승인됨")
            
            // 안드로이드 10에서 위치 권한 승인 시 위치 서비스 상태 다시 확인
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                val locationManager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
                val isLocationEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) || 
                                       locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
                
                if (!isLocationEnabled) {
                    Timber.d("위치 권한은 승인됐지만 위치 서비스가 꺼져 있음")
                    showLocationSettingsDialog()
                }
            }
        } else {
            Timber.d("위치 권한 거부됨")
        }
        
        // 알림 권한 결과 처리 (안드로이드 13 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] == true
            Timber.d("알림 권한 ${if(notificationGranted) "승인" else "거부"}됨")
        }
    }

    /**
     * 사용자 설정에 따라 도움말 안내 팝업을 표시합니다.
     */
    private fun showHelpGuideIfNeeded() {
        lifecycleScope.launch {
            // "다시 보지 않기" 옵션 확인
            val hideHelpGuide = userPreferencesManager.getHideHelpGuide()
            
            if (!hideHelpGuide) {
                // 팝업 다이얼로그 표시
                showHelpGuideDialog()
            }
        }
    }
    
    /**
     * 도움말 안내 팝업 다이얼로그를 표시합니다.
     */
    private fun showHelpGuideDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_help_guide, null)
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
        
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // 다이얼로그 버튼 설정
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel)
        val btnGoToHelp = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_go_to_help)
        val cbDoNotShowAgain = dialogView.findViewById<androidx.appcompat.widget.AppCompatCheckBox>(R.id.cb_do_not_show_again)
        
        // 나중에 보기 버튼: 다이얼로그를 닫습니다.
        btnCancel.setOnClickListener {
            // 체크박스가 선택되어 있으면 설정을 저장합니다.
            if (cbDoNotShowAgain.isChecked) {
                lifecycleScope.launch {
                    userPreferencesManager.setHideHelpGuide(true)
                }
            }
            dialog.dismiss()
        }
        
        // 지금 보기 버튼: 도움말 화면으로 이동합니다.
        btnGoToHelp.setOnClickListener {
            // 체크박스가 선택되어 있으면 설정을 저장합니다.
            if (cbDoNotShowAgain.isChecked) {
                lifecycleScope.launch {
                    userPreferencesManager.setHideHelpGuide(true)
                }
            }
            
            // NavController를 통해 도움말 화면으로 이동
            val navController = (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController
            navController.navigate(R.id.nav_help)
            
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun setStatusBarColor() {
        // 상태 바 색상 설정
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }

    /**
     * MainViewModel 인스턴스를 반환합니다.
     * @deprecated ViewModel 공유를 위해 activityViewModels()를 사용하세요.
     */
    @Deprecated("Use activityViewModels() instead to prevent memory leaks", 
                ReplaceWith("activityViewModels<MainViewModel>()", 
                "androidx.fragment.app.activityViewModels"))
    fun provideMainViewModel(): MainViewModel {
        return mainViewModel
    }

    /**
     * LoadingManager 인스턴스를 제공합니다.
     */
    fun provideLoadingManager(): LoadingManager {
        return loadingManager
    }

    /**
     * 지도 UI만 업데이트하는 메서드
     * 네비게이션 복귀 상태에서 불필요한 API 호출 없이 UI만 갱신할 때 사용
     */
    private suspend fun updateMapUI() {
        try {
            Timber.d("지도 UI만 업데이트 (API 호출 없음)")
            
            // 필요한 경우 MapViewModel의 UI 갱신 메서드 호출
            val mapViewModel = getMapViewModel()
            if (mapViewModel != null) {
                // UI 관련 업데이트만 수행
                withContext(Dispatchers.Main) {
                    mapViewModel.refreshMapUIOnly()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "지도 UI 업데이트 중 오류 발생")
        }
    }

    /**
     * 포그라운드 전환 이벤트 관찰 시작
     */
    private fun observeForegroundTransitions() {
        lifecycleScope.launch {
            appStateManager.foregroundTransitionEvent.collect { event ->
                Timber.d("포그라운드 전환 이벤트 수신: ${event.backgroundDuration}ms")
                
                // 1. 토큰 유효성 체크 - 만료된 경우 로그인 화면으로 이동
                loginViewModel.checkTokenOnForeground()
                
                // 2. 현재 활성화된 프래그먼트가 MapFragment인 경우에만 처리
                val mapFragment = getCurrentFragmentAsMapFragment()
                if (mapFragment != null) {
                    // 새로고침 필요 여부만 전달
                    mapFragment.handleForegroundTransition(event.needsRefresh)
                    
                    // 긴 백그라운드 시간 후에만 로딩 화면 표시
                    if (event.backgroundDuration >= 5 * 60 * 1000L) { // 5분
                        showLoadingAndInitMap()
                    }
                }
            }
        }
    }
}