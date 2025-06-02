package com.parker.hotkey.data.remote.util

/**
 * API 요청의 우선순위를 정의하는 열거형
 */
enum class ApiPriority {
    /**
     * 사용자가 대기 중이며 즉시 처리해야 하는 최우선 요청
     */
    FOREGROUND_CRITICAL,
    
    /**
     * 사용자가 대기 중인 일반적인 우선순위 요청
     */
    FOREGROUND_NORMAL,
    
    /**
     * 백그라운드에서 데이터 동기화를 위한 요청
     */
    BACKGROUND_SYNC,
    
    /**
     * 사용자 경험 개선을 위한 사전 데이터 로딩 요청 (가장 낮은 우선순위)
     */
    BACKGROUND_PREFETCH
} 