package com.parker.hotkey.data.remote.network

/**
 * 동기화 API 서비스 관련 설정 정보
 */
object SyncConfig {
    const val BASE_URL = "https://5yhn5y2qm1.execute-api.ap-northeast-2.amazonaws.com/prod/"
    
    // API 엔드포인트
    const val SYNC_ENDPOINT = "sync"
    const val GEOHASH_ENDPOINT = "geohash/{geohash}"
    const val MARKERS_ENDPOINT = "markers"
    const val MEMOS_ENDPOINT = "memos"
    
    // 재시도 설정
    const val MAX_RETRY_COUNT = 3
    const val RETRY_DELAY_MS = 1000L
    
    // 타임아웃 설정
    const val CONNECTION_TIMEOUT_MS = 15000L
    const val READ_TIMEOUT_MS = 10000L
    const val WRITE_TIMEOUT_MS = 10000L
} 