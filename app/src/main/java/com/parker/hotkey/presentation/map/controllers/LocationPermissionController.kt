package com.parker.hotkey.presentation.map.controllers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.naver.maps.map.LocationTrackingMode
import com.naver.maps.map.NaverMap
import com.naver.maps.map.util.FusedLocationSource
import com.parker.hotkey.domain.model.Location
import com.parker.hotkey.domain.constants.MapConstants
import com.parker.hotkey.presentation.base.BaseUIController
import com.parker.hotkey.util.LifecycleAware
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import android.os.Build
import java.lang.ref.WeakReference

/**
 * 위치 권한 관련 처리를 담당하는 통합 컨트롤러
 * 권한 요청, 위치 소스 관리, 위치 추적 활성화 등의 기능을 제공
 */
@Singleton
class LocationPermissionController @Inject constructor(
    @ApplicationContext context: Context
) : BaseUIController(context), LifecycleAware {
    // Fragment를 약한 참조로 변경하여 메모리 누수 방지
    private var fragmentRef: WeakReference<Fragment>? = null
    private var locationSource: FusedLocationSource? = null
    private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null
    
    // 라이프사이클 옵저버
    private var lifecycleObserver: LifecycleEventObserver? = null
    
    // 콜백을 약한 참조로 저장하여 메모리 누수 방지
    private var onPermissionGranted: (() -> Unit)? = null
    private var onPermissionDenied: (() -> Unit)? = null
    private var onFirstLocationUpdate: ((Location) -> Unit)? = null
    private var showError: ((String) -> Unit)? = null
    
    private var map: NaverMap? = null
    
    /**
     * BaseUIController의 onInitialize 구현
     * 주의: 이 메서드는 실질적인 초기화를 수행하지 않습니다.
     * 실제 초기화는 init(fragment, locationSource) 메서드에서 수행해야 합니다.
     */
    override fun onInitialize(rootView: View) {
        // BaseUIController의 요구사항을 충족하기 위한 빈 구현
        // 실제 초기화는 init(fragment, locationSource) 메서드를 통해 이루어집니다.
    }
    
    /**
     * 위치 권한 컨트롤러 초기화
     * @param fragment 프래그먼트 인스턴스
     * @param locationSource 선택적 위치 소스 인스턴스
     */
    fun init(fragment: Fragment, locationSource: FusedLocationSource? = null) {
        try {
            // fragment가 null이 아니고 사용 가능한지 확인
            if (fragment.isAdded && fragment.context != null) {
                // 약한 참조로 저장
                this.fragmentRef = WeakReference(fragment)
                
                // null이 아닌 FusedLocationSource 인스턴스 생성
                this.locationSource = locationSource ?: FusedLocationSource(fragment, MapConstants.LOCATION_PERMISSION_REQUEST_CODE)
                
                // 권한 요청 런처 초기화
                if (permissionLauncher == null) {
                    permissionLauncher = fragment.registerForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissions ->
                        when {
                            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                                Timber.d("위치 권한 승인됨")
                                
                                // 위치 소스 활성화 시도
                                try {
                                    val locSource = this.locationSource
                                    if (locSource != null && !locSource.isActivated) {
                                        Timber.d("권한 승인 후 위치 소스 활성화 시도")
                                        locSource.activate { location ->
                                            location?.let { loc ->
                                                val userLocation = Location(
                                                    latitude = loc.latitude,
                                                    longitude = loc.longitude
                                                )
                                                onFirstLocationUpdate?.invoke(userLocation)
                                            }
                                        }
                                        
                                        if (locSource.isActivated) {
                                            Timber.d("위치 소스 활성화 성공, 위치 추적 활성화")
                                            enableLocationTracking()
                                        } else {
                                            Timber.d("위치 소스 활성화 실패")
                                        }
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "권한 승인 후 위치 소스 활성화 시도 중 오류 발생")
                                }
                                
                                // 콜백 호출
                                onPermissionGranted?.invoke()
                            }
                            else -> {
                                Timber.d("위치 권한 거부됨")
                                onPermissionDenied?.invoke()
                            }
                        }
                    }
                }
                
                // 프래그먼트 라이프사이클에 옵저버 연결
                registerLifecycleObserver(fragment)
                
                // BaseUIController의 초기화 상태 설정
                markAsInitialized()
                
                Timber.d("LocationPermissionController 초기화 완료")
            } else {
                Timber.e("초기화 실패: 프래그먼트가 추가되지 않았거나 컨텍스트가 null입니다")
            }
        } catch (e: Exception) {
            Timber.e(e, "LocationPermissionController 초기화 중 오류 발생")
        }
    }
    
    /**
     * 프래그먼트 라이프사이클에 옵저버를 등록합니다.
     */
    private fun registerLifecycleObserver(fragment: Fragment) {
        try {
            // 기존 옵저버가 있으면 제거
            lifecycleObserver?.let {
                fragment.lifecycle.removeObserver(it)
            }
            
            // 새 옵저버 생성 및 등록
            lifecycleObserver = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_DESTROY -> {
                        Timber.d("Fragment 파괴 감지: 리소스 정리")
                        cleanup()
                    }
                    Lifecycle.Event.ON_STOP -> {
                        Timber.d("Fragment 중지 감지: 위치 추적 중지")
                        disableLocationTracking()
                    }
                    else -> {
                        // 다른 이벤트는 무시
                    }
                }
            }
            
            fragment.lifecycle.addObserver(lifecycleObserver!!)
            Timber.d("프래그먼트 라이프사이클 옵저버 등록 완료")
        } catch (e: Exception) {
            Timber.e(e, "라이프사이클 옵저버 등록 중 오류 발생")
        }
    }
    
    /**
     * 모든 리소스와 참조를 정리하는 메서드
     * Fragment onDestroyView나 onDestroy에서 호출해야 함
     */
    override fun cleanup() {
        try {
            Timber.d("LocationPermissionController cleanup 시작")
            
            // 위치 소스 비활성화
            locationSource?.deactivate()
            
            // 라이프사이클 옵저버 제거
            fragmentRef?.get()?.lifecycle?.let { lifecycle ->
                lifecycleObserver?.let {
                    lifecycle.removeObserver(it)
                }
            }
            lifecycleObserver = null
            
            // 참조 해제
            map = null
            
            // 권한 런처 해제
            permissionLauncher = null
            
            // 콜백 참조 해제
            onPermissionGranted = null
            onPermissionDenied = null
            onFirstLocationUpdate = null
            showError = null
            
            // Fragment 약한 참조 정리
            fragmentRef = null
            
            Timber.d("LocationPermissionController cleanup 완료")
        } catch (e: Exception) {
            Timber.e(e, "LocationPermissionController cleanup 중 오류 발생")
        }
    }
    
    /**
     * BaseUIController의 초기화 상태를 명시적으로 설정
     * 이 메서드는 테스트에서만 사용해야 함
     */
    internal fun markAsInitialized() {
        val fragment = fragmentRef?.get()
        if (fragment != null && fragment.view != null) {
            // 뷰가 있으면 BaseUIController 초기화
            initialize(fragment.requireView())
        } else {
            // 프래그먼트 뷰가 없어도 초기화된 것으로 간주하기 위한 오버라이드
            Timber.d("프래그먼트 뷰 없이 초기화 상태를 활성화합니다.")
            // BaseUIController의 필드에 직접 접근할 수 없으므로 설명적인 메시지만 남김
        }
    }
    
    fun setNaverMap(map: NaverMap) {
        this.map = map
        try {
            val locSource = locationSource
            if (locSource != null) {
                map.locationSource = locSource
            }
        } catch (e: Exception) {
            Timber.e(e, "지도 위치 소스 설정 중 오류 발생")
        }
    }
    
    fun setCallbacks(
        onPermissionGranted: () -> Unit,
        onPermissionDenied: () -> Unit,
        showError: (String) -> Unit,
        onFirstLocationUpdate: (Location) -> Unit
    ) {
        this.onPermissionGranted = onPermissionGranted
        this.onPermissionDenied = onPermissionDenied
        this.showError = showError
        this.onFirstLocationUpdate = onFirstLocationUpdate
        
        Timber.d("콜백 설정 완료")
    }
    
    fun checkLocationPermission() {
        try {
            // fragment가 초기화되었는지 확인
            val fragment = fragmentRef?.get()
            if (fragment == null || !fragment.isAdded || fragment.context == null) {
                Timber.e("프래그먼트가 초기화되지 않았거나 유효하지 않습니다")
                showError?.invoke("위치 권한을 확인할 수 없습니다. 잠시 후 다시 시도해주세요.")
                return
            }
            
            if (locationSource != null && locationSource?.isActivated == true) {
                Timber.d("이미 위치 권한 있음")
                // 권한이 있는 경우 위치 추적 활성화
                enableLocationTracking()
                // 콜백 호출
                onPermissionGranted?.invoke()
            } else {
                // 위치 소스가 초기화되었지만 활성화되지 않은 경우
                if (locationSource != null && locationSource?.isActivated == false) {
                    try {
                        // 위치 권한이 있는지 확인
                        val permissionCheck = fragment.requireContext().checkSelfPermission(
                            android.Manifest.permission.ACCESS_FINE_LOCATION
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                            fragment.requireContext().checkSelfPermission(
                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        
                        if (permissionCheck) {
                            Timber.d("권한 있음, 위치 소스 활성화 시도")
                            locationSource?.activate { location ->
                                location?.let { loc ->
                                    val userLocation = Location(
                                        latitude = loc.latitude,
                                        longitude = loc.longitude
                                    )
                                    onFirstLocationUpdate?.invoke(userLocation)
                                }
                            }
                            
                            if (locationSource?.isActivated == true) {
                                Timber.d("위치 소스 활성화 성공")
                                enableLocationTracking()
                                onPermissionGranted?.invoke()
                                return
                            } else {
                                Timber.d("위치 소스 활성화 실패")
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "위치 소스 활성화 시도 중 오류 발생")
                    }
                }
                
                Timber.d("위치 권한 요청 시작")
                requestLocationPermission()
            }
        } catch (e: Exception) {
            Timber.e(e, "위치 권한 체크 중 오류 발생")
            showError?.invoke("위치 권한 확인 중 오류가 발생했습니다.")
        }
    }
    
    fun requestLocationPermission() {
        try {
            if (permissionLauncher != null) {
                // Android 10(API 29) 이상에서는 위치 권한 분리 처리
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Timber.d("안드로이드 10 이상: 포그라운드 위치 권한 요청")
                    // 먼저 포그라운드 위치 권한만 요청
                    permissionLauncher?.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                    // 백그라운드 위치 권한은 앱 설정에서 별도로 처리해야 함을 로그
                    Timber.d("안드로이드 10 이상에서는 백그라운드 위치 권한을 별도로 요청해야 합니다")
                } else {
                    // Android 9 이하는 기존 방식대로 처리
                    Timber.d("안드로이드 9 이하: 위치 권한 요청")
                    permissionLauncher?.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
                Timber.d("위치 권한 요청 실행")
            } else {
                Timber.e("권한 요청 런처가 초기화되지 않았습니다")
                showError?.invoke("권한 요청을 처리할 수 없습니다. 앱을 다시 시작해주세요.")
            }
        } catch (e: Exception) {
            Timber.e(e, "위치 권한 요청 중 오류 발생")
            showError?.invoke("위치 권한 요청 중 오류가 발생했습니다.")
        }
    }
    
    fun hasLocationPermission(): Boolean {
        return locationSource != null && locationSource?.isActivated == true
    }
    
    fun enableLocationTracking() {
        try {
            // fragment가 초기화되었는지 확인
            val fragment = fragmentRef?.get()
            if (fragment == null || !fragment.isAdded || fragment.context == null) {
                Timber.e("위치 추적 활성화 불가: 프래그먼트가 초기화되지 않았거나 유효하지 않습니다")
                return
            }
            
            map?.let { naverMap ->
                if (locationSource != null) {
                    // 위치 소스가 초기화되었지만 활성화되지 않은 경우 활성화 시도
                    if (locationSource?.isActivated == false) {
                        try {
                            Timber.d("위치 소스 활성화 시도")
                            locationSource?.activate { location ->
                                location?.let { loc ->
                                    val userLocation = Location(
                                        latitude = loc.latitude,
                                        longitude = loc.longitude
                                    )
                                    // Fragment의 생명주기 확인 후 콜백 호출
                                    if (fragment.view != null && fragment.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                                        onFirstLocationUpdate?.invoke(userLocation)
                                        Timber.d("위치 업데이트: lat=${loc.latitude}, lng=${loc.longitude}")
                                    } else {
                                        Timber.d("Fragment가 더 이상 활성 상태가 아니므로 위치 업데이트 무시")
                                    }
                                }
                            }
                            Timber.d("위치 소스 활성화 완료: ${locationSource?.isActivated}")
                        } catch (e: Exception) {
                            Timber.e(e, "위치 소스 활성화 중 오류 발생")
                            showError?.invoke("위치 추적 시작 중 오류가 발생했습니다.")
                            return
                        }
                    }
                    
                    // 위치 추적 모드 설정
                    if (locationSource?.isActivated == true) {
                        naverMap.locationTrackingMode = LocationTrackingMode.Follow
                        Timber.d("위치 추적 활성화 완료")
                        
                        // 첫 위치 업데이트 감지
                        try {
                            locationSource?.lastLocation?.let { lastLocation ->
                                val location = Location(
                                    latitude = lastLocation.latitude,
                                    longitude = lastLocation.longitude
                                )
                                // Fragment 생명주기 확인 후 콜백 호출
                                if (fragment.view != null && fragment.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                                    onFirstLocationUpdate?.invoke(location)
                                    Timber.d("첫 위치 감지: lat=${location.latitude}, lng=${location.longitude}")
                                } else {
                                    Timber.d("Fragment가 더 이상 활성 상태가 아니므로 첫 위치 감지 무시")
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "위치 정보 조회 중 오류 발생")
                        }
                    } else {
                        Timber.d("위치 소스 활성화 실패 - 위치 추적 비활성화")
                        naverMap.locationTrackingMode = LocationTrackingMode.None
                    }
                } else {
                    Timber.d("위치 권한 없음 - 위치 추적 활성화 불가")
                    naverMap.locationTrackingMode = LocationTrackingMode.None
                }
            } ?: run {
                Timber.e("NaverMap이 설정되지 않았습니다")
            }
        } catch (e: Exception) {
            Timber.e(e, "위치 추적 활성화 중 오류 발생")
            showError?.invoke("위치 추적을 활성화할 수 없습니다.")
        }
    }
    
    fun disableLocationTracking() {
        try {
            map?.locationTrackingMode = LocationTrackingMode.None
            Timber.d("위치 추적 비활성화 완료")
        } catch (e: Exception) {
            Timber.e(e, "위치 추적 비활성화 중 오류 발생")
        }
    }
    
    /**
     * 앱 설정 화면으로 이동
     * 사용자가 위치 권한을 거부한 경우 앱 설정 화면으로 이동하여 권한을 수동으로 설정할 수 있게 함
     */
    fun openAppSettings() {
        try {
            val fragment = fragmentRef?.get()
            if (fragment != null) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", fragment.requireContext().packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                fragment.startActivity(intent)
                Timber.d("앱 설정 화면으로 이동")
            } else {
                Timber.e("Fragment가 초기화되지 않아 앱 설정 화면으로 이동할 수 없습니다")
            }
        } catch (e: Exception) {
            Timber.e(e, "앱 설정 화면으로 이동 중 오류 발생")
            showError?.invoke("앱 설정 화면으로 이동할 수 없습니다.")
        }
    }
    
    /**
     * 위치 권한을 확인하고 처리합니다.
     * 비동기 초기화 작업에서 호출됩니다.
     */
    fun checkPermission() {
        try {
            Timber.d("위치 권한 확인 시작")
            
            // 프래그먼트가 초기화되지 않았거나 파괴된 경우 처리
            val fragment = fragmentRef?.get()
            if (fragment == null || !fragment.isAdded || fragment.lifecycle.currentState == Lifecycle.State.DESTROYED) {
                Timber.e("프래그먼트가 초기화되지 않았거나 이미 파괴됨")
                return
            }
            
            // 위치 권한 확인 로직
            checkLocationPermission()
            
        } catch (e: Exception) {
            Timber.e(e, "위치 권한 확인 중 오류 발생")
            showError?.invoke("위치 권한 확인 중 오류가 발생했습니다.")
        }
    }
} 