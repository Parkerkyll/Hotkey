package com.parker.hotkey.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naver.maps.geometry.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocationViewModel @Inject constructor() : ViewModel() {

    companion object {
        const val DEFAULT_ZOOM = 15.0
        const val MIN_ZOOM = 14.0      // 최소 줌 레벨 (지역 수준)
        const val MAX_ZOOM = 19.0      // 최대 줌 레벨 (상세 건물)
        const val MARKER_CREATE_ZOOM = 18.0  // 마커 생성 시 줌 레벨
        const val MARKER_SELECT_ZOOM = 18.0  // 마커 선택 시 줌 레벨
    }

    private val _locationState = MutableStateFlow<LocationState>(LocationState())
    val locationState: StateFlow<LocationState> = _locationState

    fun updateLocation(location: LatLng, zoom: Double = DEFAULT_ZOOM) {
        _locationState.value = _locationState.value.copy(
            currentLocation = location,
            currentZoom = zoom
        )
    }

    fun onCameraPositionChanged(center: LatLng, zoom: Double) {
        _locationState.value = _locationState.value.copy(
            currentLocation = center,
            currentZoom = zoom
        )
    }

    fun resetToDefaultZoom() {
        _locationState.value = _locationState.value.copy(
            currentZoom = DEFAULT_ZOOM
        )
    }

    fun resetToMyLocation(myLocation: LatLng) {
        _locationState.value = _locationState.value.copy(
            currentLocation = myLocation,
            currentZoom = DEFAULT_ZOOM
        )
    }

    data class LocationState(
        val currentLocation: LatLng = LatLng(37.5666102, 126.9783881),
        val currentZoom: Double = DEFAULT_ZOOM
    )
} 