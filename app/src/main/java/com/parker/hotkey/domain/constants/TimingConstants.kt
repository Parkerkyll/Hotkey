package com.parker.hotkey.domain.constants

/**
 * 시간 관련 상수들을 관리하는 객체
 *
 * 주요 기능:
 * - 타이머 및 지연 시간 관련 상수 관리
 * - 애니메이션 타이밍 관련 상수 관리
 * - 위치 업데이트 간격 관련 상수 관리
 */
object TimingConstants {
    // 타이머 및 지연 시간
    /** 편집 모드 타임아웃 시간 (밀리초) - 1분 후 자동으로 읽기 모드로 전환 */
    const val EDIT_MODE_TIMEOUT_MS = 60000L         // 1분
    
    /** 타이머 화면 업데이트 주기 (밀리초) */
    const val TIMER_UPDATE_INTERVAL_MS = 1000L      // 1초
    
    /** 메시지 표시 시간 (밀리초) */
    const val MESSAGE_DISPLAY_DELAY_MS = 2000L      // 2초
    
    /** 메시지 표시 간격 제한 (밀리초) - 동일 메시지 표시 방지 */
    const val MESSAGE_THROTTLE_MS = 1000L           // 1초
    
    // 애니메이션 타이밍
    /** 마커 애니메이션 지속 시간 (밀리초) */
    const val MARKER_ANIMATION_DURATION_MS = 300L   // 0.3초
    
    /** 카메라 이동 애니메이션 지속 시간 (밀리초) */
    const val CAMERA_ANIMATION_DURATION_MS = 200L   // 0.2초
    
    /** 카메라 업데이트 지연 시간 (밀리초) */
    const val CAMERA_UPDATE_DELAY_MS = 300L         // 0.3초
    
    /** 마커 생성 지연 시간 (밀리초) */
    const val MARKER_CREATION_DELAY_MS = 2000L      // 2초
    
    // 위치 업데이트 간격
    /** 최소 위치 업데이트 간격 (밀리초) */
    const val MIN_LOCATION_INTERVAL_MS = 5000L      // 5초
    
    /** 가장 빠른 위치 업데이트 간격 (밀리초) */
    const val FASTEST_LOCATION_INTERVAL_MS = 2000L  // 2초
} 