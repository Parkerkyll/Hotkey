package com.parker.hotkey.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 연속된 이벤트를 디바운싱하는 유틸리티 클래스
 * 마지막 이벤트 발생 후 지정된 시간이 지나면 액션을 실행합니다.
 * @param delayMillis 디바운싱 시간 (밀리초)
 */
class Debouncer<T>(private val delayMillis: Long) {
    private var coroutineScope: CoroutineScope? = null
    private var latestJob: Job? = null
    
    /**
     * 입력에 대한 액션을 디바운싱하여 실행합니다.
     * @param input 처리할 입력값
     * @param action 디바운싱 후 실행할 액션
     */
    fun debounce(input: T, action: suspend (T) -> Unit) {
        // 코루틴 스코프가 없거나 활성 상태가 아니면 새로 생성
        if (coroutineScope == null) {
            coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        }
        
        // 이전 작업 취소
        latestJob?.cancel()
        
        // 새 작업 등록
        latestJob = coroutineScope?.launch {
            Timber.d("디바운싱 시작: $input (${delayMillis}ms)")
            delay(delayMillis)
            Timber.d("디바운싱 후 액션 실행: $input")
            action(input)
        }
    }
    
    /**
     * 모든 작업을 취소하고 리소스를 정리합니다.
     */
    fun cancel() {
        Timber.d("디바운서 취소")
        latestJob?.cancel()
        coroutineScope = null
    }
} 