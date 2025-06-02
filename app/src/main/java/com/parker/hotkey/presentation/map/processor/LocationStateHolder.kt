package com.parker.hotkey.presentation.map.processor

import com.naver.maps.geometry.LatLng
import com.parker.hotkey.domain.model.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 위치 관련 상태를 관리하는 클래스
 * ViewModel과 LocationTracker 사이의 중간 계층으로 작동
 */
@Singleton
class LocationStateHolder @Inject constructor() {
    
    // 위치 상태
    data class LocationState(
        val currentLocation: LatLng = LatLng(37.5666102, 126.9783881), // 서울시청 기본 위치
        val currentZoom: Double = 15.0,
        val isLocationTrackingEnabled: Boolean = false,
        val isLocationPermissionGranted: Boolean = false,
        val geohash: String? = null,
        val neighborGeohashes: List<String> = emptyList(),
        val lastError: Throwable? = null
    )
    
    private val _state = MutableStateFlow(LocationState())
    val state: StateFlow<LocationState> = _state.asStateFlow()
    
    fun updateLocation(location: LatLng) {
        _state.update { it.copy(currentLocation = location) }
        Timber.d("위치 업데이트: ${location.latitude}, ${location.longitude}")
    }
    
    fun updateZoom(zoom: Double) {
        _state.update { it.copy(currentZoom = zoom) }
    }
    
    fun updateLocationAndZoom(location: LatLng, zoom: Double) {
        _state.update { it.copy(
            currentLocation = location,
            currentZoom = zoom
        )}
    }
    
    fun updateGeohash(geohash: String?, neighbors: List<String>) {
        _state.update { it.copy(
            geohash = geohash,
            neighborGeohashes = neighbors
        )}
    }
    
    fun updateLocationTrackingState(isEnabled: Boolean) {
        _state.update { it.copy(isLocationTrackingEnabled = isEnabled) }
    }
    
    fun updateLocationPermissionState(isGranted: Boolean) {
        _state.update { it.copy(isLocationPermissionGranted = isGranted) }
    }
    
    fun setError(error: Throwable?) {
        _state.update { it.copy(lastError = error) }
    }
    
    fun resetError() {
        _state.update { it.copy(lastError = null) }
    }
    
    /**
     * 도메인 모델을 UI 모델로 변환
     */
    fun mapDomainLocationToUiModel(location: Location): LatLng {
        return LatLng(location.latitude, location.longitude)
    }
} 