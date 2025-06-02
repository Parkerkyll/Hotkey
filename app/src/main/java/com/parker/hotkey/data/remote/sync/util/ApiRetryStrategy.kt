package com.parker.hotkey.data.remote.sync.util

import com.parker.hotkey.data.remote.network.SyncConfig
import kotlinx.coroutines.delay
import retrofit2.Response
import timber.log.Timber
import java.io.IOException

/**
 * 재시도 전략을 구현한 유틸리티 클래스
 */
object RetryUtil {
    /**
     * API 호출을 재시도하는 함수
     * 
     * @param maxRetries 최대 재시도 횟수
     * @param initialDelayMillis 초기 지연 시간 (밀리초)
     * @param maxDelayMillis 최대 지연 시간 (밀리초)
     * @param factor 지연 시간 증가 비율
     * @param block API 호출 함수
     */
    suspend fun <T> retryApiCall(
        maxRetries: Int = SyncConfig.MAX_RETRY_COUNT,
        initialDelayMillis: Long = SyncConfig.RETRY_DELAY_MS,
        maxDelayMillis: Long = 5000L,
        factor: Double = 2.0,
        block: suspend () -> Response<T>
    ): Response<T> {
        var currentDelay = initialDelayMillis
        repeat(maxRetries) { attempt ->
            try {
                // API 호출 시도
                val response = block()
                
                // 성공하거나 클라이언트 오류인 경우 즉시 반환
                if (response.isSuccessful || response.code() in 400..499) {
                    return response
                }
                
                // 서버 오류(5xx)인 경우 재시도
                Timber.w("API 호출 실패 (${response.code()}), 재시도 중... (${attempt + 1}/$maxRetries)")
            } catch (e: IOException) {
                // 네트워크 오류는 재시도
                Timber.w("네트워크 오류 발생, 재시도 중... (${attempt + 1}/$maxRetries): ${e.message}")
            }
            
            // 마지막 시도였으면 더이상 재시도하지 않음
            if (attempt == maxRetries - 1) {
                return@repeat
            }
            
            // 지연 후 재시도
            delay(currentDelay)
            
            // 지연 시간을 증가시키되 최대값을 넘지 않도록 함
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMillis)
        }
        
        // 모든 재시도 실패 시 마지막으로 한 번 더 시도
        return block()
    }
    
    /**
     * 일반적인 작업을 재시도하는 함수
     * 
     * @param maxRetries 최대 재시도 횟수
     * @param initialDelayMillis 초기 지연 시간 (밀리초)
     * @param maxDelayMillis 최대 지연 시간 (밀리초)
     * @param factor 지연 시간 증가 비율
     * @param block 재시도할 작업, 성공 시 결과 반환, 실패 시 null 반환
     */
    suspend fun <T> retry(
        maxRetries: Int = 3,
        initialDelayMillis: Long = 50L,
        maxDelayMillis: Long = 1000L,
        factor: Double = 2.0,
        block: suspend (attempt: Int) -> T?
    ): T? {
        var currentDelay = initialDelayMillis
        
        repeat(maxRetries) { attempt ->
            val result = block(attempt)
            if (result != null) {
                return result
            }
            
            // 마지막 시도였으면 더이상 재시도하지 않음
            if (attempt == maxRetries - 1) {
                return null
            }
            
            // 지연 후 재시도
            delay(currentDelay)
            
            // 지연 시간을 증가시키되 최대값을 넘지 않도록 함
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMillis)
        }
        
        // 마지막 시도
        return block(maxRetries)
    }
    
    /**
     * 이전 API 호출 재시도 함수 (하위 호환성 유지)
     */
    suspend fun <T> retry(
        maxRetries: Int = SyncConfig.MAX_RETRY_COUNT,
        initialDelayMillis: Long = SyncConfig.RETRY_DELAY_MS,
        maxDelayMillis: Long = 5000L,
        factor: Double = 2.0,
        block: suspend () -> Response<T>
    ): Response<T> {
        return retryApiCall(maxRetries, initialDelayMillis, maxDelayMillis, factor, block)
    }
} 