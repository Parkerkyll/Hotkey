package com.parker.hotkey.domain.manager

import com.parker.hotkey.domain.constants.CoroutineConstants
import com.parker.hotkey.domain.util.EventHandler
import com.parker.hotkey.domain.util.JobManager
import com.parker.hotkey.domain.util.StateEventManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.BufferOverflow
import timber.log.Timber
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Manager 클래스들의 공통 기능을 제공하는 추상 클래스
 * @param E 이벤트 타입
 */
abstract class BaseManager<E : Any>(
    protected val coroutineScope: CoroutineScope
) : StateEventManager(coroutineScope) {
    // 초기화 상태 추적
    private val _initialized = MutableStateFlow(false)
    val initialized: StateFlow<Boolean> = _initialized.asStateFlow()

    // 이벤트 처리를 위한 SharedFlow
    protected val _events = MutableSharedFlow<E>(
        replay = 10,
        extraBufferCapacity = 50,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<E> = _events.asSharedFlow()
    
    // 구독자 상태 추적
    private val _hasSubscribers = MutableStateFlow(false)
    val hasSubscribers: StateFlow<Boolean> = _hasSubscribers.asStateFlow()
    
    // 이벤트 핸들러
    protected val eventHandler = EventHandler<E>(coroutineScope)

    /**
     * 초기화 상태를 확인합니다.
     * @return 초기화 완료 여부
     */
    protected fun isInitialized(): Boolean {
        return initialized.value
    }

    protected fun setInitialized() {
        _initialized.value = true
        Timber.d("${this::class.simpleName} 초기화 완료")
    }

    protected fun setError(error: Throwable) {
        Timber.e(error, "${this::class.simpleName} 초기화 실패")
    }
    
    /**
     * 공통 초기화 로직을 표준화한 메서드
     * Manager의 초기화 로직을 표준화하여 중복 코드를 줄입니다.
     * 
     * @param managerName 매니저 이름 (로깅용)
     * @param initBlock 실제 초기화 로직을 담은 중단 함수 블록
     */
    protected fun initializeCommon(managerName: String, initBlock: suspend () -> Unit) {
        if (isInitialized()) {
            Timber.d("${managerName}가 이미 초기화되어 있습니다.")
            return
        }
        
        Timber.d("${managerName} 초기화 시작 - 현재 구독자 여부: ${hasSubscribers.value}")
        
        coroutineScope.launch {
            try {
                Timber.d("${managerName} 초기화 로직 실행 중")
                initBlock()
                setInitialized()
                Timber.d("${managerName} 초기화 완료")
            } catch (e: Exception) {
                setError(e)
                Timber.e(e, "${managerName} 초기화 실패")
                handleError(e, "${managerName} 초기화 중 오류가 발생했습니다.", true)
            }
        }
    }
    
    /**
     * 이벤트 구독 설정
     * 
     * @param scope 이벤트를 수집할 코루틴 스코프
     * @param handler 이벤트 처리 핸들러
     * @return 구독 작업 Job
     */
    open fun subscribeToEvents(scope: CoroutineScope, handler: suspend (E) -> Unit): Job {
        _hasSubscribers.value = true
        Timber.d("${javaClass.simpleName} 이벤트 구독 시작")
        
        // EventHandler를 활용하여 구독 처리
        val job = eventHandler.subscribeWithHandler(
            owner = this,
            flowProvider = { events },
            handler = { event ->
                try {
                    handler(event)
                } catch (e: Exception) {
                    Timber.e(e, "${javaClass.simpleName} 이벤트 처리 중 오류 발생")
                } finally {
                    if (!_hasSubscribers.value) {
                        _hasSubscribers.value = true
                    }
                }
            },
            key = javaClass.simpleName
        )
        
        return job
    }
    
    /**
     * 이벤트 발행
     * 
     * @param event 발행할 이벤트
     * @param highPriority 높은 우선순위 플래그 (중요 이벤트인 경우 true)
     */
    protected suspend fun bufferOrEmitEvent(event: E, highPriority: Boolean = false) {
        try {
            Timber.d("${javaClass.simpleName} 이벤트 발행: $event${if(highPriority) " (고우선순위)" else ""}")
            _events.emit(event)
            
            // 고우선순위 이벤트는 안정적인 전달을 위해 추가 로깅
            if (highPriority) {
                Timber.i("${javaClass.simpleName} 중요 이벤트 발행 완료: $event")
            }
        } catch (e: Exception) {
            Timber.e(e, "${javaClass.simpleName} 이벤트 발행 실패: $event")
        }
    }
    
    /**
     * 디바운스 적용하여 작업을 실행합니다.
     * StateEventManager의 executeDebouncedWithCancel를 활용합니다.
     * 
     * @param key 작업 식별 키
     * @param debounceTime 디바운스 시간 (밀리초)
     * @param onCancel 작업 취소 시 호출될 콜백 (선택사항)
     * @param block 실행할 코루틴 블록
     * @return 생성된 Job 객체
     */
    protected fun launchDebounced(
        key: String,
        debounceTime: Long = CoroutineConstants.DEFAULT_DEBOUNCE_TIME,
        onCancel: (suspend () -> Unit)? = null,
        block: suspend () -> Unit
    ): Job {
        return executeDebouncedWithCancel(
            tag = "${javaClass.simpleName}_$key",
            debounceTime = debounceTime,
            onCancel = onCancel,
            block = block
        )
    }
    
    /**
     * 취소 가능한 작업을 실행합니다. (디바운스 없음)
     * StateEventManager의 executeDebouncedWithCancel를 디바운스 없이 활용합니다.
     * 
     * @param key 작업 식별 키
     * @param block 실행할 코루틴 블록
     * @return 생성된 Job 객체
     */
    protected fun launchCancellable(
        key: String,
        block: suspend () -> Unit
    ): Job {
        return executeDebouncedWithCancel(
            tag = "${javaClass.simpleName}_$key",
            debounceTime = 0L,
            block = block
        )
    }

    /**
     * 리소스 정리를 위한 메서드
     * 하위 클래스에서 오버라이드하여 추가적인 정리 작업을 수행할 수 있습니다.
     */
    open suspend fun cleanup() {
        try {
            _initialized.value = false
            _hasSubscribers.value = false
            
            // 이벤트 핸들러 구독 해제
            eventHandler.unsubscribe(this)
            
            // 모든 작업 취소 (상위 클래스의 cancelAll 호출)
            super.cancelAll()
            
            Timber.d("${this::class.simpleName} 리소스 정리 완료")
        } catch (e: Exception) {
            Timber.e(e, "${this::class.simpleName} 리소스 정리 중 오류 발생")
        }
    }

    /**
     * 표준화된 오류 처리 메서드
     * 오류 로깅 및 이벤트 발행을 처리합니다.
     * 
     * @param throwable 발생한 예외
     * @param message 추가 메시지 (기본값: null)
     * @param emitErrorEvent 오류 이벤트 발행 여부 (기본값: false)
     * @param reportToAnalytics 분석 서비스에 보고 여부 (기본값: true)
     */
    protected open fun handleError(
        throwable: Throwable,
        message: String? = null,
        emitErrorEvent: Boolean = false,
        reportToAnalytics: Boolean = true
    ) {
        val errorMessage = message ?: throwable.message ?: "알 수 없는 오류가 발생했습니다"
        val tag = javaClass.simpleName
        
        // 오류 로깅
        Timber.e(throwable, "$tag: $errorMessage")
        
        // 원인 예외 로깅 (디버깅 목적)
        if (throwable.cause != null) {
            Timber.e("원인 예외: ${throwable.cause?.message}")
        }
        
        // 오류 이벤트 발행 (필요한 경우)
        if (emitErrorEvent) {
            coroutineScope.launch {
                try {
                    // 자식 클래스에서 적절한 이벤트 변환 구현 필요 (createErrorEvent)
                    val errorEvent = createErrorEvent(throwable, errorMessage)
                    errorEvent?.let { bufferOrEmitEvent(it, true) }
                } catch (e: Exception) {
                    Timber.e(e, "$tag: 오류 이벤트 발행 실패")
                }
            }
        }
        
        // TODO: 분석 서비스 보고 로직 추가 (필요시)
    }
    
    /**
     * 오류를 이벤트로 변환하는 메서드
     * 하위 클래스에서 구현해야 합니다.
     * 
     * @param throwable 발생한 예외
     * @param message 오류 메시지
     * @return 생성된 오류 이벤트 (없으면 null)
     */
    protected override fun createErrorEvent(throwable: Throwable, message: String): E? = null
} 