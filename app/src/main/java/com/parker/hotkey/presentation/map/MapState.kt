package com.parker.hotkey.presentation.map

import com.naver.maps.geometry.LatLng
import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.model.Memo

sealed class MapState {
    data object Initial : MapState()
    data object Loading : MapState()
    data class Success(
        val markers: List<Marker> = emptyList(),
        val selectedMarkerMemos: List<Memo> = emptyList(),
        val currentLocation: LatLng = LatLng(37.5666102, 126.9783881),
        val currentZoom: Double = 17.0
    ) : MapState()
    data class Error(val error: MapError) : MapState()
} 