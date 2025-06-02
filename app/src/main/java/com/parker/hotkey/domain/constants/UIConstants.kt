package com.parker.hotkey.domain.constants

/**
 * UI 관련 상수들을 관리하는 객체
 *
 * 주요 기능:
 * - 다이얼로그 관련 상수 관리
 * - Snackbar 관련 상수 관리
 */
object UIConstants {
    // 다이얼로그 관련 상수
    /** 다이얼로그 너비 비율 (화면 대비) */
    const val DIALOG_WIDTH_RATIO = 0.95
    
    /** 다이얼로그 높이 비율 (화면 대비) */
    const val DIALOG_HEIGHT_RATIO = 0.9
    
    // Snackbar 관련 상수
    /** Snackbar 닫힘 이벤트 (ACTION 이벤트) */
    const val SNACKBAR_DISMISS_EVENT_ACTION = 1
    
    /** Snackbar 표시 길이 (SHORT) - 밀리초 */
    const val SNACKBAR_LENGTH_SHORT = 1500
    
    /** Snackbar 표시 길이 (LONG) - 밀리초 */
    const val SNACKBAR_LENGTH_LONG = 2750
    
    /** Snackbar 위치 조정값 (DP) */
    const val SNACKBAR_BOTTOM_MARGIN_DP = 160f
} 