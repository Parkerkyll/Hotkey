package com.parker.hotkey.data.remote.sync.interceptor

import com.parker.hotkey.domain.util.AppStateManager
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import timber.log.Timber
import javax.inject.Inject

/**
 * API 호출 모니터링을 위한 인터셉터
 * API 요청/응답 정보를 자세하게 로깅합니다.
 */
class ApiCallMonitorInterceptor @Inject constructor(
    private val appStateManager: AppStateManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        val method = request.method
        val requestBody = request.body
        val requestHeaders = request.headers
        
        // 네비게이션 복귀 상태 확인
        val isNavReturn = appStateManager.isNavigationReturn()
        
        // 요청 정보 상세 로깅
        Timber.tag("API_MONITOR").d("━━━━━━━━━━ API 요청 시작 ━━━━━━━━━━")
        Timber.tag("API_MONITOR").d("URL: $url")
        Timber.tag("API_MONITOR").d("메서드: $method")
        
        // 네비게이션 복귀 상태 로깅
        if (isNavReturn) {
            Timber.tag("API_MONITOR").w("⚠️ 네비게이션 복귀 상태에서 API 호출 감지됨 ⚠️")
        }
        
        // 호출 스택 로깅 (어디서 API 호출이 발생했는지 추적)
        val stackTrace = Thread.currentThread().stackTrace
        val callStack = stackTrace.slice(3..minOf(8, stackTrace.size - 1))
            .joinToString(" <- ") { "${it.className.substringAfterLast('.')}.${it.methodName}(${it.lineNumber})" }
        Timber.tag("API_MONITOR").d("호출 스택: $callStack")
        
        // 헤더 정보 로깅
        Timber.tag("API_MONITOR").d("요청 헤더:")
        requestHeaders.forEach { 
            Timber.tag("API_MONITOR").d("  ${it.first}: ${it.second}")
        }
        
        // 바디 정보 로깅 (가능한 경우)
        requestBody?.let {
            if (request.header("Content-Type")?.contains("application/json") == true) {
                try {
                    val buffer = Buffer()
                    it.writeTo(buffer)
                    val bodyStr = buffer.readUtf8()
                    Timber.tag("API_MONITOR").d("요청 바디: $bodyStr")
                } catch (e: Exception) {
                    Timber.tag("API_MONITOR").w("요청 바디 로깅 실패: ${e.message}")
                }
            }
        }
        
        val startTime = System.currentTimeMillis()
        
        // 실제 네트워크 요청 실행
        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            Timber.tag("API_MONITOR").e("API 호출 실패: ${e.message}")
            throw e
        }
        
        val duration = System.currentTimeMillis() - startTime
        val responseCode = response.code
        val responseBody = response.body
        val responseSize = responseBody.contentLength()
        
        // 응답 정보 상세 로깅
        Timber.tag("API_MONITOR").d("━━━━━━━━━━ API 응답 수신 ━━━━━━━━━━")
        Timber.tag("API_MONITOR").d("URL: $url")
        Timber.tag("API_MONITOR").d("소요시간: ${duration}ms")
        Timber.tag("API_MONITOR").d("응답코드: $responseCode")
        Timber.tag("API_MONITOR").d("응답크기: ${responseSize}bytes")
        
        // 작은 응답인 경우 바디 내용도 로깅 (큰 응답은 로그가 너무 길어질 수 있어 제한)
        if (responseSize < 10000 && response.header("Content-Type")?.contains("application/json") == true) {
            try {
                // 응답 바디는 한 번만 읽을 수 있으므로 복제해야 함
                val responseBodyCopy = response.peekBody(Long.MAX_VALUE)
                val bodyString = responseBodyCopy.string()
                Timber.tag("API_MONITOR").d("응답 바디: $bodyString")
            } catch (e: Exception) {
                Timber.tag("API_MONITOR").w("응답 바디 로깅 실패: ${e.message}")
            }
        }
        
        Timber.tag("API_MONITOR").d("━━━━━━━━━━ API 호출 완료 ━━━━━━━━━━")
        
        return response
    }
} 