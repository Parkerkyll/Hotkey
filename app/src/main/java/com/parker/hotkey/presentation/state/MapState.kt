package com.parker.hotkey.presentation.state

import com.naver.maps.geometry.LatLng
import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.model.Memo
import com.parker.hotkey.presentation.map.MapError

/**
 * 지도 UI의 상태를 나타내는 sealed 클래스
 */
sealed class MapState {
    /**
     * 초기 상태 - 데이터 로딩 전 상태
     */
    object Initial : MapState()

    /**
     * 로딩 상태 - 데이터를 로딩 중인 상태
     */
    object Loading : MapState()

    /**
     * 성공 상태 - 데이터 로딩이 완료된 상태
     *
     * @property markers 표시할 마커 목록
     * @property selectedMarker 현재 선택된 마커 (없을 수 있음)
     * @property selectedMarkerMemos 선택된 마커의 메모 목록
     * @property currentLocation 현재 사용자 위치
     * @property editMode 편집 모드 활성화 여부
     * @property editModeTimeRemaining 편집 모드 남은 시간 (밀리초)
     * @property markerToDeleteId 삭제 중인 마커 ID (UI에서 즉시 반영하기 위함)
     * @property zoomLevel 현재 지도의 줌 레벨
     * @property timestamp 최근 타임스탬프
     * @property loading 로딩 상태 여부
     */
    data class Success(
        val markers: List<Marker> = emptyList(),
        val selectedMarker: Marker? = null,
        val selectedMarkerMemos: List<Memo> = emptyList(),
        val currentLocation: LatLng = LatLng(0.0, 0.0),
        val editMode: Boolean = false,
        val editModeTimeRemaining: Long = 0L,
        val markerToDeleteId: String? = null,
        val zoomLevel: Double? = null,
        val timestamp: Long = System.currentTimeMillis(),
        val loading: Boolean = false
    ) : MapState() {
        /**
         * 현재 선택된 마커가 있는지 여부
         */
        val hasSelectedMarker: Boolean get() = selectedMarker != null
        
        /**
         * 선택된 마커의 ID (선택된 마커가 없으면 null)
         */
        val selectedMarkerId: String? get() = selectedMarker?.id
        
        /**
         * 선택된 마커에 메모가 있는지 여부
         */
        val hasMemosForSelectedMarker: Boolean get() = selectedMarkerMemos.isNotEmpty()
        
        /**
         * 선택된 마커의 메모 개수
         */
        val memoCount: Int get() = selectedMarkerMemos.size
    }

    /**
     * 에러 상태 - 오류가 발생한 상태
     *
     * @property error 발생한 오류
     */
    data class Error(val error: MapError) : MapState()
} 