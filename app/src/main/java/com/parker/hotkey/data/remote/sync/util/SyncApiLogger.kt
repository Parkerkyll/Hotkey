package com.parker.hotkey.data.remote.sync.util

import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber

/**
 * API 통신 로깅을 위한 유틸리티 클래스
 */
object SyncApiLogger {
    /**
     * HTTP 로깅 인터셉터 생성
     * 
     * @param isDebug 디버그 모드 여부
     * @return HttpLoggingInterceptor 인스턴스
     */
    fun createLoggingInterceptor(isDebug: Boolean): HttpLoggingInterceptor {
        return HttpLoggingInterceptor { message ->
            // 메시지 로깅
            Timber.tag("API_LOG").d(message)
        }.apply {
            // 디버그 모드인 경우에만 자세한 로깅
            level = if (isDebug) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }
}