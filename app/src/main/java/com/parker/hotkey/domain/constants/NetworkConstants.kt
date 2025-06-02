package com.parker.hotkey.domain.constants

/**
 * 네트워크 관련 상수들을 관리하는 객체
 *
 * 주요 기능:
 * - API 기본 URL 및 엔드포인트 관리
 * - 네트워크 재시도 설정 관리
 * - 타임아웃 설정 관리
 */
object NetworkConstants {
    // API 기본 URL
    /** API 서버 기본 URL */
    const val BASE_URL = "https://5yhn5y2qm1.execute-api.ap-northeast-2.amazonaws.com/prod/"
    
    // API 엔드포인트
    /** 동기화 엔드포인트 */
    const val SYNC_ENDPOINT = "sync"
    
    /** Geohash 기반 조회 엔드포인트 */
    const val GEOHASH_ENDPOINT = "geohash/{geohash}"
    
    /** 마커 엔드포인트 */
    const val MARKERS_ENDPOINT = "markers"
    
    /** 메모 엔드포인트 */
    const val MEMOS_ENDPOINT = "memos"
    
    // 재시도 설정
    /** 최대 재시도 횟수 */
    const val MAX_RETRY_COUNT = 3
    
    /** 재시도 간격 (밀리초) */
    const val RETRY_DELAY_MS = 1000L
    
    // 타임아웃 설정
    /** 연결 타임아웃 (밀리초) */
    const val CONNECTION_TIMEOUT_MS = 15000L
    
    /** 읽기 타임아웃 (밀리초) */
    const val READ_TIMEOUT_MS = 10000L
    
    /** 쓰기 타임아웃 (밀리초) */
    const val WRITE_TIMEOUT_MS = 10000L
} 