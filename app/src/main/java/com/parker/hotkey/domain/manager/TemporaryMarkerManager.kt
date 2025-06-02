package com.parker.hotkey.domain.manager

import com.naver.maps.geometry.LatLng
import com.parker.hotkey.domain.model.Marker
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 임시 마커 관련 이벤트를 정의하는 sealed 클래스
 */
sealed class TemporaryMarkerEvent {
    data class MarkerCreated(val marker: Marker) : TemporaryMarkerEvent()
    data class MarkerMadePermanent(val markerId: String) : TemporaryMarkerEvent()
    data class MarkerDeleted(val markerId: String) : TemporaryMarkerEvent()
    data class Error(val message: String, val throwable: Throwable? = null) : TemporaryMarkerEvent()
}

/**
 * 임시 마커 관리를 담당하는 인터페이스
 * 임시 마커 생성, 삭제, 영구 저장 기능을 제공합니다.
 */
interface TemporaryMarkerManager {
    /**
     * 임시 마커 ID 목록
     */
    val temporaryMarkers: StateFlow<Set<String>>
    
    /**
     * 임시 마커 이벤트
     */
    val events: SharedFlow<TemporaryMarkerEvent>
    
    /**
     * 임시 마커 생성
     * 
     * @param userId 사용자 ID
     * @param latLng 위치 좌표
     * @return 생성된 마커
     */
    suspend fun createTemporaryMarker(userId: String, latLng: LatLng): Marker
    
    /**
     * 임시 마커를 영구 마커로 변환
     * 
     * @param markerId 마커 ID
     */
    fun makeMarkerPermanent(markerId: String)
    
    /**
     * 임시 마커 삭제
     * 
     * @param markerId 마커 ID
     * @return 삭제 결과
     */
    suspend fun deleteTemporaryMarker(markerId: String): Result<Unit>
    
    /**
     * 임시 마커 목록에서 제거 (상태 관리용)
     * 
     * @param markerId 마커 ID
     */
    fun removeTemporaryMarker(markerId: String)
    
    /**
     * 마커가 임시인지 확인
     * 
     * @param markerId 마커 ID
     * @return 임시 마커 여부
     */
    fun isTemporaryMarker(markerId: String): Boolean
    
    /**
     * 초기화
     */
    fun initialize()
} 