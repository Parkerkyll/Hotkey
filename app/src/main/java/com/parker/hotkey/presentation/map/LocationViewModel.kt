package com.parker.hotkey.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naver.maps.geometry.LatLng
import com.parker.hotkey.presentation.map.processor.LocationStateHolder
import com.parker.hotkey.presentation.map.processor.LocationTrackingManager
import com.parker.hotkey.presentation.map.controllers.LocationPermissionController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject
import com.parker.hotkey.domain.constants.MapConstants

/**
 * 위치 관련 기능을 담당하는 ViewModel
 * 위치 상태와 위치 권한, 추적 관련 기능을 통합적으로 관리
 */
@HiltViewModel
class LocationViewModel @Inject constructor(
    private val locationStateHolder: LocationStateHolder,
    private val locationTrackingManager: LocationTrackingManager,
    private val locationPermissionController: LocationPermissionController
) : ViewModel() {

    companion object {
        // 모든 줌 레벨 상수를 MapConstants에서 가져옴
        private const val MARKER_SELECT_ZOOM = MapConstants.EDIT_MODE_ZOOM  // 마커 선택 시 줌 레벨도 편집 모드와 동일하게 설정
    }

    // 상태 노출
    val locationState: StateFlow<LocationStateHolder.LocationState> = locationStateHolder.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            LocationStateHolder.LocationState()
        )

    /**
     * ViewModel 초기화
     */
    fun initialize() {
        Timber.d("LocationViewModel 초기화")
        checkLocationPermission()
    }

    /**
     * Fragment 연결
     */
    fun attachFragment(fragment: androidx.fragment.app.Fragment) {
        if (!locationPermissionController.isInitialized()) {
            locationPermissionController.init(fragment)
        }
    }

    /**
     * 위치 권한 체크
     */
    fun checkLocationPermission() {
        Timber.d("위치 권한 체크")
        val hasPermission = locationPermissionController.hasLocationPermission()
        
        if (hasPermission) {
            Timber.d("위치 권한 있음, 위치 추적 초기화")
            initializeLocationTracking()
        } else {
            Timber.d("위치 권한 없음")
            locationPermissionController.checkLocationPermission()
        }
    }

    /**
     * 위치 권한 요청
     */
    fun requestLocationPermission() {
        Timber.d("위치 권한 요청")
        locationPermissionController.requestLocationPermission()
    }

    /**
     * 앱 설정 열기
     */
    fun openAppSettings() {
        locationPermissionController.openAppSettings()
    }

    /**
     * 위치 추적 초기화
     */
    private fun initializeLocationTracking() {
        Timber.d("위치 추적 초기화")
        locationTrackingManager.initialize()
        
        viewModelScope.launch {
            try {
                locationTrackingManager.startTracking()
            } catch (e: Exception) {
                Timber.e(e, "위치 추적 시작 중 오류 발생")
            }
        }
    }

    /**
     * 위치 업데이트
     */
    fun updateLocation(location: LatLng, zoom: Double) {
        locationStateHolder.updateLocationAndZoom(location, zoom)
    }

    /**
     * 사용자 위치 업데이트 (Location 객체를 통해)
     */
    fun updateUserLocation(location: com.parker.hotkey.domain.model.Location) {
        val latLng = LatLng(location.latitude, location.longitude)
        locationStateHolder.updateLocationAndZoom(latLng, locationState.value.currentZoom)
    }

    /**
     * 카메라 위치 변경 처리
     */
    fun onCameraPositionChanged(center: LatLng, zoom: Double) {
        locationStateHolder.updateLocationAndZoom(center, zoom)
    }

    /**
     * 기본 줌 레벨로 재설정
     */
    fun resetToDefaultZoom() {
        locationState.value.currentLocation.let { _ ->
            locationStateHolder.updateZoom(MapConstants.DEFAULT_ZOOM)
        }
    }

    /**
     * 내 위치로 재설정
     */
    fun resetToMyLocation(myLocation: LatLng) {
        locationStateHolder.updateLocationAndZoom(myLocation, MapConstants.DEFAULT_ZOOM)
    }

    /**
     * 권한 요청 결과 처리
     * 
     * @param requestCode 요청 코드
     * @param permissions 권한 배열 (호환성을 위해 유지되지만 내부적으로 사용되지 않음)
     * @param grantResults 권한 부여 결과 배열
     */
    @Suppress("UNUSED_PARAMETER")
    fun handlePermissionResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        // permissions 파라미터는 호환성을 위해 유지하지만 내부적으로 사용하지 않음
        // LocationPermissionController는 별도의 처리가 필요하지 않음 (자체적으로 권한 처리)
        Timber.d("권한 요청 결과 처리 - requestCode: $requestCode")
    }

    /**
     * 위치 추적 중지
     */
    fun stopLocationTracking() {
        locationTrackingManager.stopTracking()
    }

    /**
     * 리소스 정리
     */
    override fun onCleared() {
        try {
            Timber.d("LocationViewModel 정리 시작")
            
            // 모든 작업 취소
            viewModelScope.launch {}.cancel() // 단순히 viewModelScope에 접근만 하기 위한 방법
            
            // 추적 중지
            locationTrackingManager.stopTracking()
            
            // 위치 관련 리소스 정리
            locationTrackingManager.cleanup()
            
            // 권한 관련 리소스 정리 - LocationPermissionController 사용
            locationPermissionController.cleanup()
            
            Timber.d("LocationViewModel 정리 완료")
        } catch (e: Exception) {
            Timber.e(e, "LocationViewModel 정리 중 오류 발생")
        } finally {
            super.onCleared()
        }
    }

    /**
     * 마지막 알려진 위치를 반환합니다.
     */
    fun getLastKnownLocation(): LatLng? {
        return locationState.value.currentLocation.takeIf { 
            it.latitude != 0.0 && it.longitude != 0.0 
        }
    }

    /**
     * 위치 추적 강제 초기화
     * 앱 시작 시 위치 추적이 활성화되지 않는 문제를 해결하기 위한 메서드
     */
    fun forceInitializeLocationTracking() {
        Timber.d("위치 추적 강제 초기화 시도")
        viewModelScope.launch {
            try {
                // 위치 권한 체크 
                val hasPermission = locationPermissionController.hasLocationPermission()
                if (hasPermission) {
                    Timber.d("위치 권한 확인됨, 위치 추적 강제 초기화 시작")
                    
                    // 위치 추적 초기화 강제 실행
                    locationTrackingManager.initialize()
                    
                    // 위치 추적 시작
                    try {
                        Timber.d("위치 추적 강제 시작 시도")
                        val result = locationTrackingManager.startTracking()
                        result.onSuccess { success ->
                            if (success) {
                                Timber.d("위치 추적 강제 시작 성공")
                            } else {
                                Timber.w("위치 추적 강제 시작 실패")
                                // 3초 후 재시도
                                delay(3000)
                                locationTrackingManager.startTracking()
                            }
                        }.onFailure { error ->
                            Timber.e(error, "위치 추적 강제 시작 중 오류")
                            // 3초 후 재시도
                            delay(3000)
                            locationTrackingManager.startTracking()
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "위치 추적 강제 시작 중 예외 발생")
                    }
                } else {
                    Timber.d("위치 권한 없음, 권한 요청")
                    locationPermissionController.requestLocationPermission()
                }
            } catch (e: Exception) {
                Timber.e(e, "위치 추적 강제 초기화 중 오류 발생")
            }
        }
    }
} 