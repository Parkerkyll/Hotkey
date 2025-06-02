package com.parker.hotkey.domain.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 이벤트 처리를 표준화하고 안정적으로 관리하는 유틸리티 클래스
 * 이벤트 구독 및 처리를 일관된 방식으로 제공합니다.
 * StateEventManager를 상속하여 공통 기능을 재사용합니다.
 * 
 * @param E 이벤트 타입
 * @param scope 이벤트를 수집할 코루틴 스코프
 * @param classifier 이벤트 분류 함수 (선택사항)
 */
class EventHandler<E : Any>(
    scope: CoroutineScope,
    private val classifier: (E) -> String = { it.javaClass.simpleName }
) : StateEventManager(scope) {
    private val handlers = mutableMapOf<Class<*>, suspend (E) -> Unit>()
    private val subscriptions = mutableMapOf<Any, MutableList<Job>>()
    
    /**
     * 특정 이벤트 타입에 대한 핸들러를 등록합니다.
     * 
     * @param eventType 처리할 이벤트 타입
     * @param handler 이벤트 처리 핸들러
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : E> on(eventType: Class<T>, handler: suspend (T) -> Unit) {
        handlers[eventType] = { event ->
            if (eventType.isInstance(event)) {
                handler(event as T)
            }
        }
        
        Timber.d("${eventType.simpleName} 이벤트 핸들러 등록됨")
    }
    
    /**
     * 인라인 방식으로 이벤트 핸들러를 등록합니다.
     * 
     * @param handler 이벤트 처리 핸들러
     */
    inline fun <reified T : E> on(noinline handler: suspend (T) -> Unit) {
        on(T::class.java, handler)
    }
    
    /**
     * 이벤트 스트림을 구독합니다.
     * 
     * @param owner 구독 소유자 (일반적으로 ViewModel이나 Manager)
     * @param flowProvider 이벤트 스트림 공급자
     * @param key 작업 식별 키 (선택사항)
     */
    fun subscribe(
        owner: Any,
        flowProvider: () -> Flow<E>,
        key: String = owner.javaClass.simpleName
    ) {
        val job = subscribeFlow(
            flowProvider = flowProvider,
            tag = key,
            onEach = { event ->
                // processEvent 메서드를 사용하여 이벤트 처리
                processEvent(event, key, handlers)
            }
        )
        
        subscriptions.getOrPut(owner) { mutableListOf() }.add(job)
    }
    
    /**
     * 커스텀 처리 로직으로 이벤트 스트림을 구독합니다.
     * 
     * @param owner 구독 소유자
     * @param flowProvider 이벤트 스트림 공급자
     * @param handler 이벤트 처리 핸들러
     * @param key 작업 식별 키 (선택사항)
     * @return 생성된 구독 작업
     */
    fun <T> subscribeWithHandler(
        owner: Any,
        flowProvider: () -> Flow<T>,
        handler: suspend (T) -> Unit,
        key: String = owner.javaClass.simpleName
    ): Job {
        val job = subscribeFlow(
            flowProvider = flowProvider,
            tag = key,
            onEach = handler
        )
        
        subscriptions.getOrPut(owner) { mutableListOf() }.add(job)
        return job
    }
    
    /**
     * 디바운스 적용된 이벤트 처리를 위한 구독을 등록합니다.
     * 
     * @param owner 구독 소유자
     * @param flowProvider 이벤트 스트림 공급자
     * @param debounceTime 디바운스 시간 (밀리초)
     * @param key 작업 식별 키 (선택사항)
     */
    fun subscribeDebounced(
        owner: Any,
        flowProvider: () -> Flow<E>,
        debounceTime: Long = 300L,
        key: String = owner.javaClass.simpleName
    ) {
        val job = subscribeDebouncedFlow(
            flowProvider = flowProvider,
            tag = key,
            debounceTime = debounceTime,
            onEach = { event ->
                // processEvent 메서드를 사용하여 이벤트 처리
                processEvent(event, key, handlers)
            }
        )
        
        subscriptions.getOrPut(owner) { mutableListOf() }.add(job)
    }
    
    /**
     * 특정 소유자의 모든 구독을 취소합니다.
     * 
     * @param owner 구독 소유자
     */
    fun unsubscribe(owner: Any) {
        subscriptions[owner]?.forEach { job ->
            if (job.isActive) {
                job.cancel()
                Timber.d("[${owner.javaClass.simpleName}] 이벤트 구독 취소됨")
            }
        }
        subscriptions.remove(owner)
    }
    
    /**
     * 모든 구독을 취소합니다.
     */
    fun unsubscribeAll() {
        subscriptions.forEach { (owner, jobs) ->
            jobs.forEach { job ->
                if (job.isActive) {
                    job.cancel()
                    Timber.d("[${owner.javaClass.simpleName}] 이벤트 구독 취소됨")
                }
            }
        }
        subscriptions.clear()
        super.cancelAll()
    }
    
    /**
     * 에러 이벤트 생성 구현
     */
    override fun createErrorEvent(throwable: Throwable, message: String): Any? {
        // 에러 이벤트 타입이 없는 경우 null 반환
        return null
    }
} 