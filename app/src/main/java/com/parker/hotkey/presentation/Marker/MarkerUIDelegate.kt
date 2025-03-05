package com.parker.hotkey.presentation.map

import com.naver.maps.geometry.LatLng
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.Marker
import com.parker.hotkey.domain.model.Marker as DomainMarker
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import com.parker.hotkey.presentation.map.MapConstants.MARKER_ALPHA
import com.parker.hotkey.presentation.map.MapConstants.MARKER_WIDTH
import com.parker.hotkey.presentation.map.MapConstants.MARKER_HEIGHT

@Singleton
class MarkerUIDelegate @Inject constructor() {
    private var naverMap: NaverMap? = null
    private val markers = ConcurrentHashMap<String, Marker>()
    private var onMarkerClickListener: ((DomainMarker) -> Unit)? = null
    private var pendingMarkers = mutableListOf<DomainMarker>()
    private var isInitialized = false

    fun setNaverMap(map: NaverMap) {
        Timber.d("NaverMap 설정 시작")
        naverMap = map
        isInitialized = true
        Timber.d("NaverMap 설정됨 - 초기화 완료")
        
        // NaverMap이 설정되면 대기 중인 마커들을 처리
        if (pendingMarkers.isNotEmpty()) {
            Timber.d("대기 중인 마커 처리 시작: ${pendingMarkers.size}개")
            updateMarkers(pendingMarkers)
            pendingMarkers.clear()
        }
    }

    fun setOnMarkerClickListener(listener: (DomainMarker) -> Unit) {
        this.onMarkerClickListener = listener
    }

    fun updateMarkers(domainMarkers: List<DomainMarker>) {
        if (!isInitialized) {
            Timber.d("NaverMap이 아직 초기화되지 않음 - 마커들을 대기 목록에 추가")
            pendingMarkers.clear()
            pendingMarkers.addAll(domainMarkers)
            return
        }
        
        val map = naverMap ?: run {
            Timber.d("NaverMap이 null임 - 마커 업데이트 불가")
            pendingMarkers.clear()
            pendingMarkers.addAll(domainMarkers)
            return
        }
        
        try {
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
                        width = MARKER_WIDTH
                        height = MARKER_HEIGHT
                        alpha = MARKER_ALPHA
                        setOnClickListener { overlay ->
                            Timber.d("마커 클릭됨: ${domainMarker.id}")
                            onMarkerClickListener?.invoke(domainMarker)
                            true
                        }
                        Timber.d("새 마커 생성됨: ${domainMarker.id}")
                    }
                }
                
                marker.position = LatLng(domainMarker.position.latitude, domainMarker.position.longitude)
                marker.map = map
                Timber.d("마커 업데이트/추가됨: ${domainMarker.id}, 위치: (${domainMarker.position.latitude}, ${domainMarker.position.longitude})")
            }
            
            Timber.d("마커 업데이트 완료: 현재 ${markers.size}개의 마커 표시 중")
        } catch (e: Exception) {
            Timber.e(e, "마커 업데이트 중 오류 발생: ${e.message}")
        }
    }

    fun removeMarker(markerId: String) {
        try {
            markers[markerId]?.let { marker ->
                marker.map = null
                markers.remove(markerId)
                Timber.d("마커 UI에서 제거됨: $markerId")
            } ?: Timber.w("제거할 마커를 찾을 수 없음: $markerId")
        } catch (e: Exception) {
            Timber.e(e, "마커 제거 중 오류 발생: $markerId")
        }
    }

    fun clearMarkers() {
        try {
            markers.values.forEach { marker ->
                marker.map = null
            }
            markers.clear()
            pendingMarkers.clear()
            Timber.d("모든 마커 제거됨")
        } catch (e: Exception) {
            Timber.e(e, "마커 초기화 중 오류 발생")
        }
    }

    fun getMarkers(): Map<String, Marker> = markers.toMap()
    
    fun isMapInitialized(): Boolean = isInitialized

    fun updateMarkersAlpha(alpha: Float) {
        if (!isInitialized) {
            Timber.d("NaverMap이 초기화되지 않아 투명도 업데이트 불가")
            return
        }
        
        markers.values.forEach { marker ->
            marker.alpha = alpha
        }
        Timber.d("마커 투명도 업데이트 완료: alpha=$alpha, 마커 수=${markers.size}")
    }

    fun setupMarkerClickListener(map: NaverMap, onMarkerClick: (com.parker.hotkey.domain.model.Marker) -> Unit) {
        Timber.d("마커 클릭 리스너 설정 시작")
        naverMap = map
        this.onMarkerClickListener = onMarkerClick
        isInitialized = true
        Timber.d("마커 클릭 리스너 설정 완료")
        
        // 리스너 설정 후 대기 중인 마커들 처리
        if (pendingMarkers.isNotEmpty()) {
            Timber.d("리스너 설정 후 대기 중인 마커 처리: ${pendingMarkers.size}개")
            updateMarkers(pendingMarkers)
            pendingMarkers.clear()
        }
    }
} 