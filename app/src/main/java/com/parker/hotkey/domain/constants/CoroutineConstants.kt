package com.parker.hotkey.domain.constants

/**
 * 코루틴 관련 상수
 * 코루틴 관리 중복 코드 제거 작업에서 집중화된 상수들
 */
object CoroutineConstants {
    /** 기본 디바운스 시간 (밀리초) */
    const val DEFAULT_DEBOUNCE_TIME = 300L
    
    /** 짧은 디바운스 시간 (밀리초) */
    const val SHORT_DEBOUNCE_TIME = 150L
    
    /** UI 업데이트용 디바운스 시간 (밀리초) */
    const val UI_DEBOUNCE_TIME = 200L
    
    /** 지도 위치 업데이트 디바운스 시간 (밀리초) */
    const val MAP_LOCATION_DEBOUNCE_TIME = 250L
} 