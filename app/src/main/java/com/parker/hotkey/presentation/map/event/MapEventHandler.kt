package com.parker.hotkey.presentation.map.event

import com.naver.maps.geometry.LatLng
import com.parker.hotkey.domain.model.Memo

/**
 * 지도 관련 이벤트 처리를 담당하는 인터페이스
 * MapViewModel의 이벤트 처리 로직을 분리하여 단일 책임 원칙을 준수합니다.
 */
interface MapEventHandler {
    /**
     * 마커 클릭 이벤트 처리
     * @param markerId 클릭된 마커 ID
     * @return 이벤트 처리 여부
     */
    fun handleMarkerClick(markerId: String): Boolean
    
    /**
     * 지도 클릭 이벤트 처리
     * @param latLng 클릭된 지도 위치
     */
    fun handleMapClick(latLng: LatLng)
    
    /**
     * 메모 다이얼로그 표시 이벤트 처리
     * @param markerId 메모를 표시할 마커 ID
     */
    fun handleMemoDialogOpen(markerId: String)
    
    /**
     * 메모 다이얼로그 표시 완료 이벤트 처리
     */
    fun handleMemoDialogShown()
    
    /**
     * 메모 다이얼로그 닫힘 이벤트 처리
     */
    fun handleMemoDialogDismissed()
    
    /**
     * 편집 모드 토글 이벤트 처리
     */
    fun toggleEditMode()
    
    /**
     * 메모 생성 이벤트 처리
     * @param markerId 메모를 생성할 마커 ID
     * @param content 메모 내용
     */
    fun handleCreateMemo(markerId: String, content: String)
    
    /**
     * 메모 삭제 이벤트 처리
     * @param memoId 삭제할 메모 ID
     */
    fun handleDeleteMemo(memoId: String)
    
    /**
     * 마커 삭제 이벤트 처리
     * @param markerId 삭제할 마커 ID
     */
    fun handleDeleteMarker(markerId: String)

    /**
     * 메모 로드 이벤트 처리
     * @param markerId 메모를 로드할 마커 ID
     */
    fun handleLoadMemos(markerId: String)
} 