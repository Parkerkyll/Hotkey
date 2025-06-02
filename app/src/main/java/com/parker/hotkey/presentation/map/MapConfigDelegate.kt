package com.parker.hotkey.presentation.map

import android.content.Context
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.NaverMap
import com.naver.maps.map.util.FusedLocationSource
import com.parker.hotkey.presentation.map.controllers.LocationPermissionController
import com.parker.hotkey.presentation.map.controllers.MapUIController
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import com.parker.hotkey.domain.constants.MapConstants

class MapConfigDelegate @Inject constructor(
    private var naverMap: NaverMap?,
    @ApplicationContext private val context: Context,
    private val locationSource: FusedLocationSource,
    private val mapUIController: MapUIController,
    private val locationPermissionController: LocationPermissionController
) {
    private var onCameraPositionChanged: ((LatLng, Double) -> Unit)? = null

    fun setNaverMap(map: NaverMap) {
        naverMap = map
        setupMapCallbacks()
        
        // 위치 소스 설정
        try {
            map.locationSource = locationSource
        } catch (e: Exception) {
            Timber.e(e, "지도 위치 소스 설정 중 오류 발생")
        }
        
        // LocationPermissionController에 지도 설정
        locationPermissionController.setNaverMap(map)
    }

    fun setOnCameraPositionChangedListener(listener: (LatLng, Double) -> Unit) {
        this.onCameraPositionChanged = listener
    }

    fun setupMap(map: NaverMap) {
        this.naverMap = map
        setupMapCallbacks()
        
        // 지도 UI 설정은 MapUIController에 위임
        mapUIController.setupMap(map)
        
        // 위치 소스 설정
        try {
            map.locationSource = locationSource
        } catch (e: Exception) {
            Timber.e(e, "지도 위치 소스 설정 중 오류 발생")
        }
        
        // LocationPermissionController에 지도 설정
        locationPermissionController.setNaverMap(map)
        
        // 지도 옵션 설정 - 용량 최적화를 위한 불필요한 기능 비활성화
        map.apply {
            // 3D 건물 모델링 비활성화 (약 12MB 용량 절감)
            setLayerGroupEnabled(NaverMap.LAYER_GROUP_BUILDING, true)  // 건물 레이어 활성화
            setBuildingHeight(0.0f)  // 3D 높이 효과 제거 (건물을 2D로 표시)
            
            // 건물 내부 뷰 비활성화 (약 8MB 용량 절감)
            isIndoorEnabled = false  // 건물 내부 뷰 비활성화
            
            // 기타 불필요한 고급 효과 비활성화
            setNightModeEnabled(false)  // 야간 모드 비활성화
            
            Timber.d("지도 옵션 최적화 설정 완료 - 3D 건물 높이 효과 및 건물 내부 뷰 비활성화")
        }
    }
    
    /**
     * 지도 설정을 적용합니다. (초기화/업데이트 시 호출)
     */
    fun applyMapSettings(map: NaverMap) {
        // 기본 설정 적용
        mapUIController.setupMap(map)
        
        // 위치 소스 설정
        try {
            map.locationSource = locationSource
        } catch (e: Exception) {
            Timber.e(e, "지도 위치 소스 설정 중 오류 발생")
        }
        
        // 지도 옵션 설정
        map.apply {
            // 줌 레벨 제한
            minZoom = MapConstants.MIN_ZOOM
            maxZoom = MapConstants.MAX_ZOOM
            
            // 기본 UI 컨트롤러 설정
            uiSettings.apply {
                isCompassEnabled = true
                isZoomControlEnabled = false // 줌 컨트롤 비활성화
                isScaleBarEnabled = true
                isLocationButtonEnabled = true
            }
            
            // 용량 최적화를 위한 불필요한 기능 비활성화 (추가)
            setLayerGroupEnabled(NaverMap.LAYER_GROUP_BUILDING, true)  // 건물 레이어 활성화
            setBuildingHeight(0.0f)  // 3D 높이 효과 제거
            isIndoorEnabled = false  // 건물 내부 뷰 비활성화
            setNightModeEnabled(false)  // 야간 모드 비활성화
        }
        
        Timber.d("지도 설정 적용 완료 (최적화 설정 포함)")
    }

    private fun setupMapCallbacks() {
        naverMap?.addOnCameraChangeListener { _, _ ->
            naverMap?.let { map ->
                onCameraPositionChanged?.invoke(map.cameraPosition.target, map.cameraPosition.zoom)
            }
        }
    }

    fun moveToLocation(latLng: LatLng, zoom: Double = MapConstants.DEFAULT_ZOOM) {
        naverMap?.let { map ->
            val cameraUpdate = CameraUpdate.scrollAndZoomTo(latLng, zoom)
            map.moveCamera(cameraUpdate)
        }
    }

    fun enableLocationTracking() {
        try {
            // LocationPermissionController에 위임
            locationPermissionController.enableLocationTracking()
        } catch (e: Exception) {
            Timber.e(e, "위치 추적 모드 설정 중 오류 발생")
        }
    }

    fun disableLocationTracking() {
        try {
            // LocationPermissionController에 위임
            locationPermissionController.disableLocationTracking()
        } catch (e: Exception) {
            Timber.e(e, "위치 추적 모드 해제 중 오류 발생")
        }
    }
} 