package com.parker.hotkey.domain.manager

import com.naver.maps.geometry.LatLng
import com.parker.hotkey.domain.model.Marker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 마커 관련 이벤트를 정의하는 sealed 클래스
 */
sealed class MarkerEvent {
    data class MarkerDeleted(val markerId: String) : MarkerEvent()
    data class MarkerCreated(val marker: Marker) : MarkerEvent()
    data class MarkerSelected(val markerId: String) : MarkerEvent()
    object MarkerSelectionCleared : MarkerEvent()
    data class MarkerCreationSuccess(val marker: Marker) : MarkerEvent()
    data class Error(val message: String, val throwable: Throwable? = null) : MarkerEvent()
    object RefreshMarkersUI : MarkerEvent()
}

/**
 * 마커 관리를 담당하는 인터페이스
 * 마커 CRUD 작업과 상태 관리를 제공합니다.
 */
interface MarkerManager {
    /**
     * 현재 로드된 마커 목록
     */
    val markers: StateFlow<List<Marker>>
    
    /**
     * 현재 선택된 마커 ID
     */
    val selectedMarkerId: StateFlow<String?>
    
    /**
     * 마커 관련 이벤트 Flow
     */
    val markerEvents: SharedFlow<MarkerEvent>
    
    /**
     * 초기화 상태
     */
    val initialized: StateFlow<Boolean>
    
    /**
     * 이벤트 구독 설정
     * 
     * @param scope 이벤트를 수집할 코루틴 스코프
     * @param handler 이벤트 처리 핸들러
     * @return 구독 작업 Job
     */
    fun subscribeToEvents(scope: CoroutineScope, handler: suspend (MarkerEvent) -> Unit): Job
    
    /**
     * 매니저 초기화
     * 필요한 초기 설정을 수행합니다.
     */
    fun initialize()
    
    /**
     * 마커 선택
     */
    fun selectMarker(markerId: String)
    
    /**
     * 마커 선택 해제
     */
    fun clearSelectedMarker()
    
    /**
     * 마커 생성
     */
    suspend fun createMarker(userId: String, latLng: LatLng): Result<Marker>
    
    /**
     * 마커 생성 후 선택
     */
    suspend fun createMarkerAndSelect(userId: String, latLng: LatLng): Result<Marker>
    
    /**
     * 마커 생성 및 선택 후 메모장 열기
     * @param userId 사용자 ID
     * @param latLng 위치 좌표
     * @return 생성 결과
     */
    suspend fun createMarkerAndOpenMemo(userId: String, latLng: LatLng): Result<Marker>
    
    /**
     * 마커 삭제
     */
    suspend fun deleteMarker(markerId: String): Result<Unit>
    
    /**
     * 상태 기반 마커 삭제
     * 마커의 상태(임시/영구)에 따라 적절한 삭제 처리를 수행합니다.
     */
    suspend fun deleteMarkerByState(markerId: String): Result<Unit>
    
    /**
     * 특정 영역의 마커 로드 - 기본 방식
     * 
     * @param geohash 현재 위치의 geohash
     * @param neighbors 주변 geohash 목록
     */
    fun loadMarkersInArea(geohash: String, neighbors: List<String>)
    
    /**
     * 특정 영역의 마커 로드 - 최적화 방식 (줌 레벨에 따라 정밀도 조절)
     * 
     * @param geohash 현재 위치의 geohash
     * @param neighbors 주변 geohash 목록
     * @param zoomLevel 현재 지도 줌 레벨
     */
    fun loadMarkersInAreaOptimized(geohash: String, neighbors: List<String>, zoomLevel: Double)
    
    /**
     * 특정 영역의 마커 로드 - geohash6 최적화 방식 (항상 정밀도 6 사용, 마커 수만 조절)
     * 
     * @param geohash 현재 위치의 geohash6
     * @param neighbors 주변 geohash6 목록
     * @param zoomLevel 현재 지도 줌 레벨 (마커 수 제한에만 사용)
     */
    @Deprecated("loadMarkersInAreaOptimized로 통합됨", ReplaceWith("loadMarkersInAreaOptimized(geohash, neighbors, zoomLevel)"))
    fun loadMarkersInAreaGeohash6Optimized(geohash: String, neighbors: List<String>, zoomLevel: Double)
    
    /**
     * ID로 마커 조회
     *
     * @param markerId 마커 ID
     * @return 마커 객체 또는 null
     */
    fun getMarkerById(markerId: String): Marker?
    
    /**
     * 마커 목록 업데이트
     *
     * @param markers 새 마커 목록
     */
    fun updateMarkers(markers: List<Marker>)
    
    /**
     * 목록에서 마커 강제 제거 (낙관적 UI 업데이트용)
     */
    fun forceRemoveMarkerFromList(markerId: String)
    
    /**
     * 백그라운드에서 포그라운드로 전환 시 마커 UI를 새로고침합니다.
     * 마커 깜박임 방지를 위해 사용합니다.
     */
    fun refreshMarkersUI()
} 