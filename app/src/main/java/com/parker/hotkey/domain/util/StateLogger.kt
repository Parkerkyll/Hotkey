package com.parker.hotkey.domain.util

import com.parker.hotkey.domain.model.state.BaseState
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 상태 변경을 로깅하는 유틸리티 클래스
 */
class StateLogger(private val tag: String) {
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    /**
     * 상태 변경을 로깅합니다.
     */
    fun <T : BaseState> logStateChange(oldState: T, newState: T, source: String) {
        if (oldState != newState) {
            val timestamp = dateFormat.format(Date())
            val diff = getStateDiff(oldState, newState)
            
            Timber.d("[$tag] [$timestamp] 상태 변경 ($source):\n$diff")
        }
    }
    
    /**
     * 에러를 로깅합니다.
     */
    fun logError(e: Throwable, message: String) {
        val timestamp = dateFormat.format(Date())
        Timber.e(e, "[$tag] [$timestamp] $message")
    }
    
    /**
     * 디버그 메시지를 로깅합니다.
     */
    fun logDebug(message: String) {
        val timestamp = dateFormat.format(Date())
        Timber.d("[$tag] [$timestamp] $message")
    }
    
    /**
     * 두 상태 간의 차이를 문자열로 반환합니다.
     */
    private fun <T : BaseState> getStateDiff(oldState: T, newState: T): String {
        return buildString {
            appendLine("이전 상태:")
            appendLine(oldState.toString())
            appendLine("새로운 상태:")
            appendLine(newState.toString())
            
            // 로딩 상태 변경 확인
            if (oldState.isLoading != newState.isLoading) {
                appendLine("로딩 상태: ${oldState.isLoading} -> ${newState.isLoading}")
            }
            
            // 에러 상태 변경 확인
            if (oldState.error != newState.error) {
                appendLine("에러 상태: ${oldState.error} -> ${newState.error}")
            }
        }
    }
} 