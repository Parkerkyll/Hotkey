package com.parker.hotkey.domain.constants

/**
 * 마커 관련 상수들을 관리하는 객체
 *
 * 주요 기능:
 * - 마커 UI 관련 상수 관리
 * - 마커 투명도 관련 상수 관리
 * - 마커 크기 및 거리 관련 상수 관리
 */
object MarkerConstants {
    // 마커 UI 관련 상수
    /** 마커 기본 크기 (dp) */
    const val MARKER_SIZE_DP = 64
    
    /** 선택된 마커 크기 (dp) */
    const val MARKER_SIZE_SELECTED_DP = 64
    
    // 마커 투명도 관련 상수
    /** 일반 마커 투명도 */
    const val MARKER_ALPHA_NORMAL = 0.8f
    
    /** 선택된 마커 투명도 */
    const val MARKER_ALPHA_SELECTED = 1.0f
    
    /** 비활성 마커 투명도 */
    const val MARKER_ALPHA_INACTIVE = 0.5f
    
    /** 기본 마커 투명도 */
    const val MARKER_ALPHA_DEFAULT = 0.4f
    
    // 마커 크기 관련 상수
    /** 마커 너비 (픽셀) */
    const val MARKER_WIDTH = 100
    
    /** 마커 높이 (픽셀) */
    const val MARKER_HEIGHT = 130
    
    // 마커 주변 최소 거리 설정
    /** 
     * 마커 생성 최소 거리 (미터)
     * 이 거리 이내에 다른 마커가 있으면 새 마커 생성을 제한합니다.
     */
    const val MARKER_MINIMUM_DISTANCE_M = 20.0
} 