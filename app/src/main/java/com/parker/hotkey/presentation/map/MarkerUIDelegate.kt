package com.parker.hotkey.presentation.map

import com.naver.maps.geometry.LatLng
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.Marker
import com.parker.hotkey.domain.model.Marker as DomainMarker
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarkerUIDelegate @Inject constructor() {
    private var naverMap: NaverMap? = null
    private val markers = mutableMapOf<String, Marker>()
    private var onMarkerClickListener: ((DomainMarker) -> Unit)? = null

    fun setNaverMap(map: NaverMap) {
        naverMap = map
    }

    fun setOnMarkerClickListener(listener: (DomainMarker) -> Unit) {
        this.onMarkerClickListener = listener
    }

    fun updateMarkers(domainMarkers: List<DomainMarker>) {
        Timber.d("마커 업데이트 시작: ${domainMarkers.size}개의 마커")
        
        // 현재 표시된 마커 중 업데이트할 목록에 없는 마커 제거
        val currentMarkerIds = domainMarkers.map { it.id }.toSet()
        val markersToRemove = markers.keys.filter { it !in currentMarkerIds }
        
        markersToRemove.forEach { id ->
            markers[id]?.let { marker ->
                marker.map = null
                markers.remove(id)
                Timber.d("마커 제거됨: $id")
            }
        }

        // 마커 업데이트 또는 새로 생성
        domainMarkers.forEach { domainMarker ->
            val marker = markers.getOrPut(domainMarker.id) {
                Marker().apply {
                    alpha = 0.4f
                    setOnClickListener {
                        Timber.d("마커 클릭됨: ${domainMarker.id}")
                        onMarkerClickListener?.invoke(domainMarker)
                        true
                    }
                }
            }
            
            marker.position = domainMarker.position
            if (marker.map == null) {
                marker.map = naverMap
                Timber.d("마커 추가됨: ${domainMarker.id}, 위치: (${domainMarker.position.latitude}, ${domainMarker.position.longitude})")
            }
        }
        
        Timber.d("마커 업데이트 완료: 현재 ${markers.size}개의 마커 표시 중")
    }

    fun removeMarker(markerId: String) {
        markers[markerId]?.let { marker ->
            marker.map = null
            markers.remove(markerId)
            Timber.d("마커 UI에서 제거됨: $markerId")
        } ?: Timber.w("제거할 마커를 찾을 수 없음: $markerId")
    }

    fun clearMarkers() {
        markers.values.forEach { marker ->
            marker.map = null
        }
        markers.clear()
    }

    fun getMarkers(): Map<String, Marker> {
        return markers.toMap()
    }
} 