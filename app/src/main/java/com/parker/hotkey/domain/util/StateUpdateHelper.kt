package com.parker.hotkey.domain.util

import com.parker.hotkey.domain.constants.CoroutineConstants
import com.parker.hotkey.domain.model.state.BaseState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 상태 업데이트를 안전하게 처리하는 헬퍼 클래스
 * StateEventManager를 상속하여 공통 기능을 재사용합니다.
 * 
 * @param T 관리할 상태 타입 (BaseState 상속 필요)
 * @param stateFlow 관리할 상태 흐름
 * @param errorHandler 에러 처리 로직
 * @param coroutineScope 코루틴 스코프 (디바운싱에 사용)
 */
class StateUpdateHelper<T : BaseState>(
    private val stateFlow: MutableStateFlow<T>,
    private val errorHandler: (T, String?, Boolean) -> T,
    coroutineScope: CoroutineScope
) : StateEventManager(coroutineScope) {
    // 이전 상태 캐싱 (불필요한 업데이트 방지용)
    private var lastEmittedState: T? = null
    
    // 배치 업데이트를 위한 대기 중인 업데이트 목록
    private val pendingUpdates = LinkedList<Pair<String, (T) -> T>>()
    
    /**
     * 상태를 원자적으로 안전하게 업데이트합니다.
     * 상위 클래스의 updateStateAtomic 함수에 위임
     * 
     * @param tag 로깅을 위한 태그
     * @param update 상태 업데이트 람다
     */
    suspend fun updateStateAtomic(tag: String, update: (T) -> T) {
        super.updateStateAtomic(stateFlow, tag) { state ->
            val newState = update(state)
            lastEmittedState = newState
            newState
        }
    }
    
    /**
     * 상태를 안전하게 업데이트합니다. (비동기, 뮤텍스 없음)
     * 상위 클래스의 updateState 함수에 위임
     * 
     * @param tag 로깅을 위한 태그
     * @param update 상태 업데이트 람다
     */
    fun updateState(tag: String, update: (T) -> T) {
        // 배치 업데이트 모드이면 업데이트 큐에 추가
        if (batchUpdateMode.get()) {
            synchronized(pendingUpdates) {
                pendingUpdates.add(tag to update)
                Timber.v("[$tag] 배치 모드: 업데이트 큐에 추가됨 (총 ${pendingUpdates.size}개)")
            }
            return
        }
        
        super.updateState(stateFlow, tag) { state ->
            val newState = update(state)
            lastEmittedState = newState
            newState
        }
    }
    
    /**
     * 디바운스 적용된 상태 업데이트를 수행합니다.
     * 상위 클래스의 updateStateDebounced 함수에 위임
     * 
     * @param tag 로깅 및 작업 식별을 위한 태그
     * @param debounceTime 디바운스 시간 (밀리초)
     * @param update 상태 업데이트 람다
     */
    fun updateStateDebounced(tag: String, debounceTime: Long = CoroutineConstants.DEFAULT_DEBOUNCE_TIME, update: (T) -> T) {
        super.updateStateDebounced(stateFlow, tag, debounceTime) { state ->
            val newState = update(state)
            lastEmittedState = newState
            newState
        }
    }
    
    /**
     * 로딩 상태를 설정합니다.
     */
    fun setLoading(tag: String, isLoading: Boolean) {
        updateState(tag) { errorHandler(it, null, isLoading) }
    }
    
    /**
     * 에러 상태를 설정합니다.
     */
    fun setError(tag: String, error: String?) {
        updateState(tag) { errorHandler(it, error, false) }
    }
    
    /**
     * 상태를 초기화합니다.
     */
    fun reset(tag: String, initialState: T) {
        updateState(tag) { initialState }
    }
    
    /**
     * 조건부 상태 업데이트를 수행합니다.
     * 조건이 충족되지 않으면 업데이트를 수행하지 않습니다.
     * 
     * @param tag 로깅을 위한 태그
     * @param condition 업데이트 조건
     * @param update 상태 업데이트 람다
     */
    fun updateStateIf(tag: String, condition: (T) -> Boolean, update: (T) -> T) {
        try {
            val currentState = stateFlow.value
            if (condition(currentState)) {
                updateState(tag, update)
            } else {
                Timber.v("[$tag] 상태 업데이트 조건 불충족, 업데이트 건너뜀")
            }
        } catch (e: Exception) {
            Timber.e(e, "[$tag] 조건부 상태 업데이트 중 오류 발생")
        }
    }
    
    /**
     * 배치 업데이트 모드를 시작합니다.
     * 이 모드에서는 모든 업데이트가 큐에 추가되고, 일괄 처리됩니다.
     */
    fun startBatchUpdate() {
        if (batchUpdateMode.compareAndSet(false, true)) {
            Timber.d("배치 업데이트 모드 시작")
        }
    }
    
    /**
     * 배치 업데이트 모드를 종료하고 모든 대기 중인 업데이트를 적용합니다.
     * 
     * @param tag 로깅을 위한 태그
     */
    suspend fun finishBatchUpdate(tag: String) {
        if (!batchUpdateMode.compareAndSet(true, false)) {
            Timber.w("[$tag] 배치 모드가 활성화되지 않았습니다.")
            return
        }
        
        val updates: List<Pair<String, (T) -> T>>
        synchronized(pendingUpdates) {
            if (pendingUpdates.isEmpty()) {
                Timber.d("[$tag] 배치 업데이트 모드 종료: 대기 업데이트 없음")
                return
            }
            
            updates = ArrayList(pendingUpdates)
            pendingUpdates.clear()
        }
        
        Timber.d("[$tag] 배치 업데이트 적용 시작 (총 ${updates.size}개)")
        
        // 모든 업데이트를 하나의 원자적 업데이트로 병합
        mutex.withLock {
            try {
                var currentState = stateFlow.value
                val originalState = currentState
                
                updates.forEach { (updateTag, update) ->
                    try {
                        currentState = update(currentState)
                    } catch (e: Exception) {
                        Timber.e(e, "[$updateTag] 배치 내 업데이트 적용 중 오류")
                    }
                }
                
                // 상태가 변경된 경우에만 업데이트
                if (originalState !== currentState && originalState != currentState) {
                    Timber.d("[$tag] 배치 업데이트 적용 완료: $originalState -> $currentState")
                    stateFlow.value = currentState
                    lastEmittedState = currentState
                } else {
                    Timber.d("[$tag] 배치 업데이트 후 상태 변경 없음")
                }
            } catch (e: Exception) {
                Timber.e(e, "[$tag] 배치 업데이트 적용 중 오류 발생")
            }
        }
    }
    
    /**
     * 상태 업데이트 오류 처리 구현
     */
    override fun <S> handleUpdateError(stateFlow: MutableStateFlow<S>, tag: String, error: Exception) {
        if (stateFlow.value is BaseState) {
            try {
                @Suppress("UNCHECKED_CAST")
                val state = stateFlow.value as? T ?: run {
                    Timber.e("[$tag] 상태 타입 변환 실패")
                    return
                }
                val errorState = errorHandler(state, error.message, false)
                @Suppress("UNCHECKED_CAST")
                stateFlow.value = errorState as? S ?: run {
                    Timber.e("[$tag] 에러 상태 타입 변환 실패")
                    return
                }
                Timber.d("[$tag] 오류 상태로 업데이트됨")
            } catch (e: Exception) {
                Timber.e(e, "[$tag] 에러 상태 업데이트 중 추가 오류 발생")
            }
        }
    }
    
    /**
     * 리소스를 정리합니다.
     */
    fun cleanup() {
        super.cancelAll()
    }
} 