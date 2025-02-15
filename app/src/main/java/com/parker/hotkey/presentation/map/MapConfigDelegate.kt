package com.parker.hotkey.presentation.map

import android.content.Context
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.NaverMap
import com.naver.maps.map.LocationTrackingMode
import com.naver.maps.map.util.FusedLocationSource
import timber.log.Timber
import javax.inject.Inject
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext

class MapConfigDelegate @Inject constructor(
    private var naverMap: NaverMap?,
    @ApplicationContext private val context: Context,
    private val locationSource: FusedLocationSource
) {
    private var onCameraPositionChanged: ((LatLng, Double) -> Unit)? = null

    companion object {
        const val MIN_ZOOM = 14.0
        const val MAX_ZOOM = 19.0
        const val DEFAULT_ZOOM = 17.0
        const val MARKER_CREATION_ZOOM = 18.0
    }

    fun setNaverMap(map: NaverMap) {
        naverMap = map
        setupMapCallbacks()
    }

    fun setOnCameraPositionChangedListener(listener: (LatLng, Double) -> Unit) {
        this.onCameraPositionChanged = listener
    }

    fun setupMap() {
        try {
            naverMap?.let { map ->
                // 줌 레벨 제한 설정
                map.minZoom = MIN_ZOOM
                map.maxZoom = MAX_ZOOM
                
                // 위치 소스 설정
                map.locationSource = locationSource
                
                // UI 설정
                map.uiSettings.apply {
                    isZoomControlEnabled = false     // 줌 컨트롤 버튼 비활성화
                    isCompassEnabled = true         // 나침반 활성화
                    isLocationButtonEnabled = true   // 위치 버튼 활성화
                    isLogoClickEnabled = true       // 로고 클릭 활성화
                    isRotateGesturesEnabled = true  // 지도 회전 제스처 활성화
                }
                
                // 위치 오버레이 활성화 및 설정
                map.locationOverlay.apply {
                    isVisible = true
                    circleRadius = 10             // 위치 정확도 표시 원의 반경 (미터)
                    circleOutlineWidth = 1         // 원의 외곽선 두께
                    circleOutlineColor = ContextCompat.getColor(context, android.R.color.holo_blue_light)
                    circleColor = ContextCompat.getColor(context, android.R.color.holo_blue_bright)
                }
                
                // 위치 추적 모드 설정
                map.locationTrackingMode = LocationTrackingMode.Follow
            }
            
            // 카메라 이동 이벤트 리스너
            naverMap?.addOnCameraChangeListener { _, _ ->
                naverMap?.let { map ->
                    onCameraPositionChanged?.invoke(
                        map.cameraPosition.target,
                        map.cameraPosition.zoom
                    )
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "지도 설정 중 오류 발생")
        }
    }

    private fun setupMapCallbacks() {
        naverMap?.addOnCameraChangeListener { _, _ ->
            naverMap?.let { map ->
                onCameraPositionChanged?.invoke(map.cameraPosition.target, map.cameraPosition.zoom)
            }
        }
    }

    fun moveToLocation(latLng: LatLng, zoom: Double = DEFAULT_ZOOM) {
        naverMap?.let { map ->
            val cameraUpdate = CameraUpdate.scrollAndZoomTo(latLng, zoom)
            map.moveCamera(cameraUpdate)
        }
    }

    fun enableLocationTracking() {
        try {
            naverMap?.locationTrackingMode = LocationTrackingMode.Follow
        } catch (e: Exception) {
            Timber.e(e, "위치 추적 모드 설정 중 오류 발생")
        }
    }

    fun disableLocationTracking() {
        try {
            naverMap?.locationTrackingMode = LocationTrackingMode.None
        } catch (e: Exception) {
            Timber.e(e, "위치 추적 모드 해제 중 오류 발생")
        }
    }
} 