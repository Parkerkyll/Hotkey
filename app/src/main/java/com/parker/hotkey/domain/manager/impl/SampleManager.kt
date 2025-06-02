package com.parker.hotkey.domain.manager.impl

import com.parker.hotkey.domain.model.state.BaseState
import com.parker.hotkey.domain.util.StateLogger
import com.parker.hotkey.domain.util.StateUpdateHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

/**
 * 상태 관리 패턴을 보여주는 샘플 매니저 클래스
 * Phase 2에서 MemoManager 리팩토링의 가이드라인으로 사용됨
 */
class SampleManager @Inject constructor(
    private val mainDispatcher: CoroutineDispatcher,
    private val ioDispatcher: CoroutineDispatcher
) : CoroutineScope {
    
    private val TAG = "SampleManager"
    
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = job + mainDispatcher
    
    // 상태 정의
    data class SampleState(
        val items: List<SampleItem> = emptyList(),
        val selectedItemId: String? = null,
        override val isLoading: Boolean = false,
        override val error: String? = null
    ) : BaseState
    
    data class SampleItem(
        val id: String,
        val name: String,
        val value: Int
    )
    
    // 상태 관리
    private val _state = MutableStateFlow(SampleState())
    val state: StateFlow<SampleState> = _state.asStateFlow()
    
    // 진행 중인 작업 추적
    private var loadItemsJob: Job? = null
    
    // 유틸리티 클래스
    private val stateLogger = StateLogger(TAG)
    private val stateUpdateHelper = StateUpdateHelper(
        stateFlow = _state,
        errorHandler = { state, error, isLoading ->
            state.copy(error = error, isLoading = isLoading)
        },
        coroutineScope = this
    )
    
    /**
     * 아이템 로드 (비동기 작업 예제)
     */
    fun loadItems() {
        // 이미 진행 중인 작업이 있으면 취소
        loadItemsJob?.cancel()
        
        loadItemsJob = launch {
            try {
                // 로딩 상태 설정
                stateUpdateHelper.setLoading(TAG, true)
                
                // 백그라운드에서 데이터 로드
                val items = withContext(ioDispatcher) {
                    fetchItemsFromSource()
                }
                
                // 상태 업데이트
                stateUpdateHelper.updateState(TAG) { currentState ->
                    currentState.copy(
                        items = items,
                        selectedItemId = if (items.isNotEmpty()) items.first().id else null
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "아이템 로드 실패")
                stateUpdateHelper.setError(TAG, e.message ?: "알 수 없는 오류")
            } finally {
                stateUpdateHelper.setLoading(TAG, false)
                loadItemsJob = null
            }
        }
    }
    
    /**
     * 아이템 선택
     */
    fun selectItem(itemId: String) {
        stateUpdateHelper.updateState(TAG) { currentState ->
            // 선택한 아이템이 존재하는지 확인
            val itemExists = currentState.items.any { it.id == itemId }
            if (!itemExists) {
                stateLogger.logDebug("존재하지 않는 아이템 ID: $itemId")
                return@updateState currentState
            }
            
            currentState.copy(selectedItemId = itemId)
        }
    }
    
    /**
     * 아이템 추가
     */
    fun addItem(name: String, value: Int) {
        stateUpdateHelper.updateState(TAG) { currentState ->
            // 새 아이템 생성
            val newItem = SampleItem(
                id = generateId(),
                name = name,
                value = value
            )
            
            // 목록에 추가
            val updatedItems = currentState.items + newItem
            
            stateLogger.logDebug("아이템 추가됨: $newItem")
            
            currentState.copy(items = updatedItems)
        }
    }
    
    /**
     * 아이템 제거
     */
    fun removeItem(itemId: String) {
        stateUpdateHelper.updateState(TAG) { currentState ->
            val updatedItems = currentState.items.filter { it.id != itemId }
            
            // 선택된 아이템이 제거되었는지 확인
            val updatedSelectedId = if (currentState.selectedItemId == itemId) {
                updatedItems.firstOrNull()?.id
            } else {
                currentState.selectedItemId
            }
            
            stateLogger.logDebug("아이템 제거됨: $itemId")
            
            currentState.copy(
                items = updatedItems,
                selectedItemId = updatedSelectedId
            )
        }
    }
    
    /**
     * 복잡한 상태 변경 (동시성 제어 예제)
     */
    fun performComplexOperation() {
        launch {
            try {
                // 로딩 상태 설정
                stateUpdateHelper.setLoading(TAG, true)
                
                // 첫 번째 비동기 작업
                val tempItems = withContext(ioDispatcher) {
                    fetchItemsFromSource()
                }
                
                // 중간 상태 업데이트
                stateUpdateHelper.updateState(TAG) { it.copy(items = tempItems) }
                
                // 두 번째 비동기 작업
                delay(500) // 시뮬레이션된 지연
                
                // 아이템 가공
                val processedItems = tempItems.map { 
                    it.copy(value = it.value * 2)
                }
                
                // 최종 상태 업데이트
                stateUpdateHelper.updateState(TAG) { it.copy(items = processedItems) }
                
            } catch (e: Exception) {
                stateUpdateHelper.setError(TAG, e.message)
            } finally {
                stateUpdateHelper.setLoading(TAG, false)
            }
        }
    }
    
    /**
     * 리소스 정리
     */
    fun cleanup() {
        job.cancel()
    }
    
    /**
     * 상태 초기화
     */
    fun resetState() {
        stateUpdateHelper.reset(TAG, SampleState())
    }
    
    // 가상의 데이터 소스에서 아이템 가져오기 (시뮬레이션)
    private suspend fun fetchItemsFromSource(): List<SampleItem> {
        // 네트워크 지연 시뮬레이션
        // 주의: ioDispatcher가 TestDispatcher일 경우 이 지연이 즉시 진행되지 않을 수 있음
        delay(1000)
        
        return List(5) { index ->
            SampleItem(
                id = "item_$index",
                name = "아이템 $index",
                value = index * 10
            )
        }
    }
    
    // 아이디 생성 (시뮬레이션)
    private fun generateId(): String {
        return "item_${System.currentTimeMillis()}"
    }
} 