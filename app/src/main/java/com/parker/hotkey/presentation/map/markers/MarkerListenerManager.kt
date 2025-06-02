package com.parker.hotkey.presentation.map.markers

import android.content.Context
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.Overlay
import com.parker.hotkey.domain.model.Marker as DomainMarker
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 마커 리스너 관리를 위한 전용 클래스
 */
@Singleton
class MarkerListenerManager @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    // 리스너가 설정된 마커 목록 추적
    private val markersWithListeners = ConcurrentHashMap<String, Boolean>()
    
    // 마커 데이터 맵 - 마커 ID와 해당 도메인 마커 간의 매핑
    private val markerDataMap = ConcurrentHashMap<String, DomainMarker>()
    
    // 마커에 리스너 설정 시 기록
    fun setClickListener(markerId: String, marker: Marker, onMarkerClick: ((DomainMarker) -> Boolean)?, 
                         markerData: DomainMarker) {
        try {
            // 기존 리스너 제거
            marker.onClickListener = null
            
            // 클릭 리스너가 없는 경우 처리
            if (onMarkerClick == null) {
                Timber.d("마커 ${markerId}에 설정할 클릭 리스너가 null임")
                markersWithListeners.remove(markerId)
                markerDataMap.remove(markerId)
                return
            }
            
            // 마커 데이터 맵에 최신 도메인 마커 저장
            markerDataMap[markerId] = markerData
            
            // 약한 참조 사용
            val weakListener = WeakReference(onMarkerClick)
            
            // 새 리스너 설정
            marker.onClickListener = object : Overlay.OnClickListener {
                override fun onClick(overlay: Overlay): Boolean {
                    Timber.d("마커 클릭됨: ID=$markerId")
                    
                    // 약한 참조로부터 콜백 획득
                    val listener = weakListener.get() ?: return false
                    
                    // 마커 데이터 맵에서 최신 데이터 가져오기
                    val latestMarkerData = markerDataMap[markerId]
                    
                    if (latestMarkerData == null) {
                        Timber.w("마커 ${markerId}의 데이터를 찾을 수 없음")
                        return false
                    }
                    
                    // 콜백 호출 (tag 속성 대신 마커 데이터 맵 사용)
                    return listener.invoke(latestMarkerData)
                }
            }
            
            // 리스너 설정 상태 기록
            markersWithListeners[markerId] = true
            Timber.d("마커 ${markerId}에 약한 참조 클릭 리스너 설정 완료")
        } catch (e: Exception) {
            Timber.e(e, "마커 ${markerId}의 클릭 리스너 설정 중 오류 발생")
        }
    }
    
    // 리스너 설정 여부 확인
    fun hasListener(markerId: String): Boolean {
        return markersWithListeners[markerId] == true
    }
    
    // 리스너 제거
    fun removeListener(markerId: String, marker: Marker) {
        try {
            marker.onClickListener = null
            markersWithListeners.remove(markerId)
            markerDataMap.remove(markerId)
            Timber.d("마커 ${markerId}의 리스너 제거됨")
        } catch (e: Exception) {
            Timber.e(e, "마커 ${markerId}의 리스너 제거 중 오류 발생")
        }
    }
    
    // 마커 제거 시 정리
    fun cleanupMarker(markerId: String) {
        markersWithListeners.remove(markerId)
        markerDataMap.remove(markerId)
    }
    
    // 모든 리스너 정리
    fun cleanupAllListeners() {
        markersWithListeners.clear()
        markerDataMap.clear()
        Timber.d("모든 마커 리스너 정리 완료")
    }
} 