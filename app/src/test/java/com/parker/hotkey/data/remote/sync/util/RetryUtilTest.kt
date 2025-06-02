package com.parker.hotkey.data.remote.sync.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.io.IOException

@ExperimentalCoroutinesApi
class RetryUtilTest {

    @Test
    fun `retryApiCall should return successful response immediately`() = runTest {
        // given
        val response = Response.success("Success")

        // when
        val result = RetryUtil.retryApiCall {
            response
        }

        // then
        assertTrue(result.isSuccessful)
        assertEquals("Success", result.body())
    }

    @Test
    fun `retryApiCall should retry on server error and eventually succeed`() = runTest {
        // given
        var attempts = 0
        
        // when
        val result = RetryUtil.retryApiCall(
            maxRetries = 3,
            initialDelayMillis = 10L, // 테스트에서는 짧게 설정
            maxDelayMillis = 50L
        ) {
            attempts++
            if (attempts < 3) {
                // 처음 두 번은 서버 오류 발생
                Response.error(500, ResponseBody.create(null, "Error"))
            } else {
                // 세 번째 시도에서 성공
                Response.success("Success after retry")
            }
        }

        // then
        assertTrue(result.isSuccessful)
        assertEquals("Success after retry", result.body())
        assertEquals(3, attempts)
    }

    @Test
    fun `retryApiCall should retry on network error and eventually succeed`() = runTest {
        // given
        var attempts = 0
        
        // when
        val result = RetryUtil.retryApiCall(
            maxRetries = 3,
            initialDelayMillis = 10L,
            maxDelayMillis = 50L
        ) {
            attempts++
            if (attempts < 3) {
                // 처음 두 번은 네트워크 오류 발생
                throw IOException("Network error")
            } else {
                // 세 번째 시도에서 성공
                Response.success("Success after network retry")
            }
        }

        // then
        assertTrue(result.isSuccessful)
        assertEquals("Success after network retry", result.body())
        assertEquals(3, attempts)
    }

    @Test
    fun `retry should return result when successful`() = runTest {
        // given
        var attempts = 0
        
        // when
        val result = RetryUtil.retry(
            maxRetries = 3,
            initialDelayMillis = 10L,
            maxDelayMillis = 50L
        ) { attempt ->
            attempts++
            "Success on attempt $attempt"
        }

        // then
        assertEquals("Success on attempt 0", result)
        assertEquals(1, attempts)
    }

    @Test
    fun `retry should retry on null result and eventually succeed`() = runTest {
        // given
        var attempts = 0
        
        // when
        val result = RetryUtil.retry(
            maxRetries = 3,
            initialDelayMillis = 10L,
            maxDelayMillis = 50L
        ) { attempt ->
            attempts++
            if (attempts < 3) {
                null // 처음 두 번은 실패
            } else {
                "Success after $attempts attempts"
            }
        }

        // then
        assertEquals("Success after 3 attempts", result)
        assertEquals(3, attempts)
    }

    @Test
    fun `retry should return null if all attempts fail`() = runTest {
        // when
        val result = RetryUtil.retry(
            maxRetries = 2,
            initialDelayMillis = 10L,
            maxDelayMillis = 50L
        ) { _ ->
            null // 모든 시도 실패
        }

        // then
        assertNull(result)
    }
} 