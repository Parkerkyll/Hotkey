package com.parker.hotkey.data.remote.interceptor

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * API 호출을 모니터링하는 인터셉터
 * 특정 API 호출의 발생 여부와 호출 스택을 로깅합니다.
 */
@Singleton
class ApiMonitorInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        
        // deleteMarker API 호출 모니터링
        if (url.contains("deleteMarker") || url.contains("markers") && request.method == "DELETE") {
            Log.d("API_MONITOR", "deleteMarker API 호출 - URL: $url")
            Log.d("API_MONITOR", "호출 스택: ${getSimplifiedStackTrace()}")
        }
        
        val response = chain.proceed(request)
        
        // deleteMarker API 응답 모니터링
        if (url.contains("deleteMarker") || url.contains("markers") && request.method == "DELETE") {
            Log.d("API_MONITOR", "━━━━━━━━━━ API 호출 완료 ━━━━━━━━━━")
        }
        
        return response
    }
    
    /**
     * 간소화된 스택 트레이스 반환
     * 불필요한 시스템 스택을 제거하고 핵심 호출 스택만 표시합니다.
     */
    private fun getSimplifiedStackTrace(): String {
        val fullStack = Thread.currentThread().stackTrace
        
        // 필요한 부분만 추출 (3~8번 프레임)
        return fullStack.slice(3..minOf(8, fullStack.size - 1))
            .joinToString(" <- ") { "${it.className.substringAfterLast('.')}.${it.methodName}(${it.lineNumber})" }
    }
} 