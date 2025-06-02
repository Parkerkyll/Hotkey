package com.parker.hotkey.domain.constants

/**
 * 지도 관련 상수들을 관리하는 객체
 *
 * 주요 기능:
 * - 지도 줌 레벨 관련 상수 관리
 * - 맵 UI 관련 상수 관리
 * - 위치 권한 관련 상수 관리
 */
object MapConstants {
    // 지도 줌 레벨 관련 상수
    /** 기본 지도 줌 레벨 */
    const val DEFAULT_ZOOM = 17.3
    
    /** 최소 줌 레벨 */
    const val MIN_ZOOM = 15.0
    
    /** 최대 줌 레벨 */
    const val MAX_ZOOM = 19.0
    
    /** 오류 상황에서 사용할 기본 줌 레벨 */
    const val DEFAULT_ERROR_ZOOM = 17.3
    
    /** 편집 모드 최소 줌 레벨 */
    const val EDIT_MODE_MIN_ZOOM = 18.0
    
    /** 편집 모드 줌 레벨 */
    const val EDIT_MODE_ZOOM = 18.0
    
    // 맵 UI 관련 상수
    /** 맵 패딩 (dp) */
    const val MAP_PADDING_DP = 16
    
    // 위치 권한 관련 상수
    /** 위치 권한 요청 코드 */
    const val LOCATION_PERMISSION_REQUEST_CODE = 1000
} 