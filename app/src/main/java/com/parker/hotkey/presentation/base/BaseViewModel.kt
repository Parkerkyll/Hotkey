package com.parker.hotkey.presentation.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 모든 ViewModel의 기본 클래스
 * 상태와 이벤트 처리에 대한 공통 로직을 제공합니다.
 *
 * @param S ViewModel의 상태 타입
 * @param E ViewModel에서 발행하는 이벤트 타입
 */
abstract class BaseViewModel<S : Any, E : Any> : ViewModel() {
    // 상태 관리
    protected val _state = MutableStateFlow<S>(createInitialState())
    val state: StateFlow<S> = _state.asStateFlow()
    
    // 이벤트 관리
    private val _events = MutableSharedFlow<E>(extraBufferCapacity = 1)
    val events: SharedFlow<E> = _events.asSharedFlow()
    
    /**
     * ViewModel의 초기 상태를 생성합니다.
     * 각 ViewModel에서 이 메서드를 구현해야 합니다.
     */
    protected abstract fun createInitialState(): S
    
    /**
     * 현재 상태 값을 반환합니다.
     */
    protected fun currentState(): S = state.value
    
    /**
     * 상태를 업데이트합니다.
     * @param reduce 현재 상태를 받아 새 상태를 반환하는 함수
     */
    protected fun setState(reduce: S.() -> S) {
        _state.update { it.reduce() }
    }
    
    /**
     * 이벤트를 발행합니다.
     * @param event 발행할 이벤트
     */
    protected suspend fun emitEvent(event: E) {
        _events.emit(event)
    }
    
    /**
     * 이벤트를 발행합니다. 비동기 컨텍스트에서 사용할 수 있도록 코루틴을 시작합니다.
     * @param event 발행할 이벤트
     */
    protected fun launchEvent(event: E) {
        viewModelScope.launch {
            emitEvent(event)
        }
    }
    
    /**
     * 오류 처리를 포함한 코루틴을 시작합니다.
     * @param block 실행할 비동기 블록
     * @param onError 오류 발생 시 호출할 함수
     */
    protected fun launchWithErrorHandling(
        block: suspend () -> Unit,
        onError: (Throwable) -> Unit = { logError(it) }
    ) = viewModelScope.launch {
        try {
            block()
        } catch (e: Exception) {
            onError(e)
        }
    }
    
    /**
     * 오류를 로깅합니다.
     * @param throwable 발생한 오류
     */
    protected fun logError(throwable: Throwable) {
        Timber.e(throwable, "ViewModel 오류 발생")
    }
} 