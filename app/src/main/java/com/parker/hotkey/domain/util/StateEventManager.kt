package com.parker.hotkey.domain.util

import com.parker.hotkey.domain.constants.CoroutineConstants
import com.parker.hotkey.domain.model.state.BaseState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 상태 및 이벤트 관리를 위한 통합 베이스 클래스
 * StateUpdateHelper와 EventHandler 간의 중복 기능을 하나로 통합
 *
 * @param scope 코루틴 작업을 실행할 스코프
 */
abstract class StateEventManager(
    protected val scope: CoroutineScope
) {
    // 공통으로 사용할 JobManager
    protected val jobManager = JobManager<String>()
    
    // 원자적 업데이트를 위한 뮤텍스
    protected val mutex = Mutex()
    
    // 배치 업데이트 모드 제어
    protected val batchUpdateMode = AtomicBoolean(false)
    
    /**
     * 디바운스 적용된 작업을 실행합니다.
     *
     * @param tag 작업 식별 태그
     * @param debounceTime 디바운스 시간 (밀리초)
     * @param block 실행할 코루틴 블록
     * @return 생성된 Job 객체
     */
    fun executeDebounced(
        tag: String,
        debounceTime: Long = CoroutineConstants.DEFAULT_DEBOUNCE_TIME,
        block: suspend () -> Unit
    ): Job {
        return jobManager.launch(
            scope = scope,
            key = tag,
            debounceTime = debounceTime,
            block = block
        )
    }
    
    /**
     * 디바운스 적용된 작업을 실행하고 취소 콜백을 제공합니다.
     * BaseManager의 launchDebounced와 통합된 기능
     *
     * @param tag 작업 식별 태그
     * @param debounceTime 디바운스 시간 (밀리초)
     * @param onCancel 작업 취소 시 호출될 콜백 (선택사항)
     * @param block 실행할 코루틴 블록
     * @return 생성된 Job 객체
     */
    open fun executeDebouncedWithCancel(
        tag: String,
        debounceTime: Long = CoroutineConstants.DEFAULT_DEBOUNCE_TIME,
        onCancel: (suspend () -> Unit)? = null,
        block: suspend () -> Unit
    ): Job {
        return jobManager.launch(
            scope = scope,
            key = tag,
            debounceTime = debounceTime,
            onCancel = onCancel,
            block = block
        )
    }
    
    /**
     * 일반 Flow를 구독하고 관리합니다.
     *
     * @param flowProvider Flow 공급자
     * @param tag 구독 식별 태그
     * @param onEach Flow 항목 처리 함수
     * @param onError 에러 처리 함수 (선택사항)
     * @return 생성된 구독 작업
     */
    fun <T> subscribeFlow(
        flowProvider: () -> Flow<T>,
        tag: String,
        onEach: suspend (T) -> Unit,
        onError: suspend (Throwable) -> Unit = { e ->
            Timber.e(e, "[$tag] 구독 중 오류 발생")
        }
    ): Job {
        // 이전 구독이 있다면 먼저 취소 (중복 구독 방지)
        subscriberManager.unsubscribe(tag)
        
        return subscriberManager.register<T>(
            scope = scope,
            flowProvider = { 
                try {
                    flowProvider().catch { e -> 
                        onError(e)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[$tag] Flow 초기화 중 오류 발생")
                    // 오류 발생 시 빈 Flow 반환
                    kotlinx.coroutines.flow.emptyFlow()
                }
            },
            tag = tag,
            onEach = onEach,
            onError = onError
        )
    }
    
    /**
     * 디바운스 적용된 Flow 구독을 등록합니다.
     *
     * @param flowProvider Flow 공급자
     * @param tag 구독 식별 태그
     * @param debounceTime 디바운스 시간 (밀리초)
     * @param onEach Flow 항목 처리 함수
     * @return 생성된 구독 작업
     */
    fun <T> subscribeDebouncedFlow(
        flowProvider: () -> Flow<T>,
        tag: String,
        debounceTime: Long = CoroutineConstants.DEFAULT_DEBOUNCE_TIME,
        onEach: suspend (T) -> Unit
    ): Job {
        return subscriberManager.registerDebounced<T>(
            scope = scope,
            flowProvider = flowProvider,
            tag = tag,
            debounceTime = debounceTime,
            onEach = onEach
        )
    }
    
    /**
     * 모든 실행 중인 작업을 취소합니다.
     */
    fun cancelAll() {
        jobManager.cancelAll()
        subscriberManager.unsubscribeAll()
        Timber.d("모든 상태/이벤트 작업이 취소되었습니다")
    }
    
    /**
     * StateUpdateHelper 기능 통합: 상태를 안전하게 업데이트합니다.
     *
     * @param stateFlow 업데이트할 상태 흐름
     * @param tag 로깅을 위한 태그
     * @param update 상태 업데이트 람다
     */
    protected fun <S> updateState(
        stateFlow: MutableStateFlow<S>,
        tag: String,
        update: (S) -> S
    ) {
        try {
            val oldState = stateFlow.value
            val newState = update(oldState)
            
            // 상태 변경이 없으면 업데이트 생략
            if (oldState === newState || oldState == newState) {
                Timber.v("[$tag] 상태 변경 없음, 업데이트 생략")
                return
            }
            
            // 상태 변경 로깅
            Timber.d("[$tag] 상태 업데이트: $oldState -> $newState")
            
            stateFlow.value = newState
        } catch (e: Exception) {
            Timber.e(e, "[$tag] 상태 업데이트 중 오류 발생")
            handleUpdateError(stateFlow, tag, e)
        }
    }
    
    /**
     * StateUpdateHelper 기능 통합: 상태를 원자적으로 안전하게 업데이트합니다.
     *
     * @param stateFlow 업데이트할 상태 흐름
     * @param tag 로깅을 위한 태그
     * @param update 상태 업데이트 람다
     */
    protected suspend fun <S> updateStateAtomic(
        stateFlow: MutableStateFlow<S>,
        tag: String,
        update: (S) -> S
    ) {
        mutex.withLock {
            try {
                val oldState = stateFlow.value
                val newState = update(oldState)
                
                // 상태 변경이 없으면 업데이트 생략
                if (oldState === newState || oldState == newState) {
                    Timber.v("[$tag] 상태 변경 없음, 업데이트 생략")
                    return
                }
                
                // 상태 변경 로깅
                Timber.d("[$tag] 상태 원자적 업데이트: $oldState -> $newState")
                
                stateFlow.value = newState
            } catch (e: Exception) {
                Timber.e(e, "[$tag] 원자적 상태 업데이트 중 오류 발생")
                handleUpdateError(stateFlow, tag, e)
            }
        }
    }
    
    /**
     * StateUpdateHelper 기능 통합: 디바운스 적용된 상태 업데이트를 수행합니다.
     *
     * @param stateFlow 업데이트할 상태 흐름
     * @param tag 로깅 및 작업 식별을 위한 태그
     * @param debounceTime 디바운스 시간 (밀리초)
     * @param update 상태 업데이트 람다
     */
    protected fun <S> updateStateDebounced(
        stateFlow: MutableStateFlow<S>,
        tag: String,
        debounceTime: Long = CoroutineConstants.DEFAULT_DEBOUNCE_TIME,
        update: (S) -> S
    ) {
        executeDebounced(
            tag = "update_$tag",
            debounceTime = debounceTime
        ) {
            updateStateAtomic(stateFlow, tag, update)
        }
    }
    
    /**
     * StateUpdateHelper 기능 통합: 상태 업데이트 오류 처리.
     * 이 메서드는 기본적으로 로깅만 수행하며, 필요에 따라 하위 클래스에서 오버라이드할 수 있습니다.
     */
    protected open fun <S> handleUpdateError(stateFlow: MutableStateFlow<S>, tag: String, error: Exception) {
        // BaseState 인터페이스를 구현한 경우 에러 상태 설정
        if (stateFlow.value is BaseState) {
            try {
                Timber.d("[$tag] BaseState 에러 핸들링 - 하위 클래스에서 구현 필요")
            } catch (e: Exception) {
                Timber.e(e, "[$tag] 에러 상태 업데이트 중 추가 오류 발생")
            }
        }
    }
    
    /**
     * EventHandler 기능 통합: 특정 타입의 이벤트를 처리합니다.
     *
     * @param event 처리할 이벤트
     * @param tag 로깅을 위한 태그
     * @param handlers 이벤트 타입별 핸들러 맵
     */
    protected suspend fun <E : Any> processEvent(
        event: E,
        tag: String,
        handlers: Map<Class<*>, suspend (E) -> Unit>
    ) {
        val eventType = event.javaClass
        val eventName = eventType.simpleName
        
        val handlerKeys = handlers.keys.filter { it.isAssignableFrom(eventType) }
        if (handlerKeys.isEmpty()) {
            Timber.w("[$tag] $eventName 이벤트에 대한 핸들러가 없습니다")
        } else {
            handlerKeys.forEach { handlerKey ->
                try {
                    handlers[handlerKey]?.invoke(event)
                } catch (e: Exception) {
                    Timber.e(e, "[$tag] $eventName 이벤트 처리 중 오류 발생")
                }
            }
        }
    }
    
    /**
     * EventHandler 기능 통합: 에러 이벤트를 생성합니다.
     * 하위 클래스에서 이 메서드를 구현하여 필요한 에러 이벤트를 생성할 수 있습니다.
     */
    protected open fun createErrorEvent(throwable: Throwable, message: String): Any? {
        // 기본 구현은 널 반환, 하위 클래스에서 필요에 따라 구현
        return null
    }
    
    // 객체 클래스 이름을 안전하게 가져오는 도우미 함수
    private fun getClassNameSafely(item: Any?): String {
        return when {
            item == null -> "null"
            else -> try {
                item.javaClass.simpleName
            } catch (e: Exception) {
                "UnknownClass"
            }
        }
    }
    
    // Flow 구독을 관리하는 내부 매니저
    private val subscriberManager = object {
        private val subscriptions = mutableMapOf<String, Job>()
        
        fun <T> register(
            scope: CoroutineScope,
            flowProvider: () -> Flow<T>,
            tag: String,
            onEach: suspend (T) -> Unit,
            onError: suspend (Throwable) -> Unit
        ): Job {
            // 기존 구독 취소
            unsubscribe(tag)
            
            val flow = flowProvider()
            val job = scope.launch {
                try {
                    flow.onEach { item ->
                        try {
                            val itemClassName = getClassNameSafely(item)
                            Timber.d("[$tag] 항목 수신됨: $itemClassName")
                            onEach(item)
                        } catch (e: Exception) {
                            // 코루틴 취소 예외는 정상적인 취소 과정이므로 경고나 디버그 로그로 처리
                            if (e is kotlinx.coroutines.CancellationException) {
                                Timber.d("[$tag] 항목 처리 중 코루틴 취소됨")
                            } else {
                                Timber.e(e, "[$tag] 항목 처리 중 오류 발생")
                            }
                        }
                    }
                    .catch { error -> 
                        // 코루틴 취소 예외는 정상적인 취소 과정이므로 경고나 디버그 로그로 처리
                        if (error is kotlinx.coroutines.CancellationException) {
                            Timber.d("[$tag] 구독 취소됨")
                        } else {
                            onError(error)
                        }
                    }
                    .collect()
                } catch (e: Exception) {
                    // 코루틴 취소 예외는 정상적인 취소 과정이므로 경고나 디버그 로그로 처리
                    if (e is kotlinx.coroutines.CancellationException) {
                        Timber.d("[$tag] 구독 취소됨")
                    } else {
                        onError(e)
                    }
                }
            }
            
            subscriptions[tag] = job
            Timber.d("[$tag] 구독 등록됨")
            return job
        }
        
        fun <T> registerDebounced(
            scope: CoroutineScope,
            flowProvider: () -> Flow<T>,
            tag: String,
            debounceTime: Long,
            onEach: suspend (T) -> Unit
        ): Job {
            // 기존 구독 취소
            unsubscribe(tag)
            
            val flow = flowProvider()
            val job = scope.launch {
                try {
                    flow.onEach { item ->
                        val itemType = getClassNameSafely(item)
                        Timber.d("[$tag] 디바운스 항목 수신됨: $itemType")
                        
                        jobManager.launch(
                            scope = scope,
                            key = "$tag:$itemType",
                            debounceTime = debounceTime
                        ) {
                            try {
                                onEach(item)
                            } catch (e: Exception) {
                                // 코루틴 취소 예외는 정상적인 취소 과정이므로 경고나 디버그 로그로 처리
                                if (e is kotlinx.coroutines.CancellationException) {
                                    Timber.d("[$tag] 디바운스 항목 처리 중 코루틴 취소됨")
                                } else {
                                    Timber.e(e, "[$tag] 디바운스 항목 처리 중 오류 발생")
                                }
                            }
                        }
                    }
                    .catch { e -> 
                        // 코루틴 취소 예외는 정상적인 취소 과정이므로 경고나 디버그 로그로 처리
                        if (e is kotlinx.coroutines.CancellationException) {
                            Timber.d("[$tag] 디바운스 구독 취소됨")
                        } else {
                            Timber.e(e, "[$tag] 디바운스 구독 중 오류 발생") 
                        }
                    }
                    .collect()
                } catch (e: Exception) {
                    // 코루틴 취소 예외는 정상적인 취소 과정이므로 경고나 디버그 로그로 처리
                    if (e is kotlinx.coroutines.CancellationException) {
                        Timber.d("[$tag] 디바운스 구독 취소됨")
                    } else {
                        Timber.e(e, "[$tag] 디바운스 구독 중 오류 발생")
                    }
                }
            }
            
            subscriptions[tag] = job
            Timber.d("[$tag] 디바운스 구독 등록됨")
            return job
        }
        
        fun unsubscribe(tag: String) {
            subscriptions[tag]?.let { job ->
                if (job.isActive) {
                    job.cancel()
                    Timber.d("[$tag] 구독 취소됨")
                }
                subscriptions.remove(tag)
            }
        }
        
        fun unsubscribeAll() {
            subscriptions.forEach { (tag, job) ->
                if (job.isActive) {
                    job.cancel()
                    Timber.d("[$tag] 구독 취소됨")
                }
            }
            subscriptions.clear()
        }
    }
} 