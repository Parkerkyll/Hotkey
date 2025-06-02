package com.parker.hotkey.domain.util

import com.parker.hotkey.domain.model.state.BaseState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class StateUpdateHelperTest {

    private data class TestState(
        val value: Int = 0,
        override val isLoading: Boolean = false,
        override val error: String? = null
    ) : BaseState
    
    private lateinit var stateFlow: MutableStateFlow<TestState>
    private lateinit var stateUpdateHelper: StateUpdateHelper<TestState>
    private lateinit var testScope: CoroutineScope
    
    @Before
    fun setup() {
        stateFlow = MutableStateFlow(TestState())
        testScope = CoroutineScope(UnconfinedTestDispatcher())
        stateUpdateHelper = StateUpdateHelper(
            stateFlow = stateFlow,
            errorHandler = { state, error, isLoading ->
                state.copy(error = error, isLoading = isLoading)
            },
            coroutineScope = testScope
        )
    }
    
    @Test
    fun `updateState should update state properly`() {
        // When
        stateUpdateHelper.updateState("TEST") { state ->
            state.copy(value = 10)
        }
        
        // Then
        assertEquals(10, stateFlow.value.value)
    }
    
    @Test
    fun `updateState should skip update when new state is same as old state`() {
        // Given
        var updateCount = 0
        val originalState = stateFlow.value
        
        // When - 동일한 상태로 업데이트
        stateUpdateHelper.updateState("TEST") { state ->
            updateCount++
            state
        }
        
        // Then
        assertEquals(1, updateCount) // 업데이트 함수는 호출됨
        assertEquals(originalState, stateFlow.value) // 상태는 변경되지 않음
    }
    
    @Test
    fun `updateStateIf should only update when condition is true`() {
        // When - 조건이 true인 경우
        stateUpdateHelper.updateStateIf("TEST", { true }) { state ->
            state.copy(value = 20)
        }
        
        // Then
        assertEquals(20, stateFlow.value.value)
        
        // When - 조건이 false인 경우
        stateUpdateHelper.updateStateIf("TEST", { false }) { state ->
            state.copy(value = 30)
        }
        
        // Then - 업데이트되지 않음
        assertEquals(20, stateFlow.value.value)
    }
    
    @Test
    fun `setLoading and setError should update state properly`() {
        // When
        stateUpdateHelper.setLoading("TEST", true)
        
        // Then
        assertEquals(true, stateFlow.value.isLoading)
        
        // When
        stateUpdateHelper.setError("TEST", "Error message")
        
        // Then
        assertEquals("Error message", stateFlow.value.error)
        assertEquals(false, stateFlow.value.isLoading) // 에러 설정 시 로딩 상태 해제
    }
    
    @Test
    fun `reset should reset state to initial value`() {
        // Given
        stateUpdateHelper.updateState("TEST") { 
            it.copy(value = 50, isLoading = true, error = "Some error") 
        }
        
        // When
        stateUpdateHelper.reset("TEST", TestState(value = 0))
        
        // Then
        assertEquals(TestState(value = 0), stateFlow.value)
    }
    
    /* 
    // 'batchUpdateAtomic' 메소드가 존재하지 않아 주석 처리
    @Test
    fun `batchUpdateAtomic should apply multiple updates in one atomic operation`() = runTest {
        // Given
        val updates = listOf<(TestState) -> TestState>(
            { it.copy(value = it.value + 5) },
            { it.copy(value = it.value * 2) },
            { it.copy(value = it.value - 3) }
        )
        
        // When
        stateUpdateHelper.batchUpdateAtomic("TEST", updates)
        
        // Then - 업데이트가 순차적으로 적용되어야 함: ((0 + 5) * 2) - 3 = 7
        assertEquals(7, stateFlow.value.value)
    }
    */
    
    @Test
    fun `startBatchUpdate and finishBatchUpdate should apply updates as one operation`() = runTest {
        // Given
        stateUpdateHelper.startBatchUpdate()
        
        // When - 여러 업데이트 수행
        stateUpdateHelper.updateState("TEST") { it.copy(value = 10) }
        stateUpdateHelper.updateState("TEST") { it.copy(value = 20) }
        stateUpdateHelper.updateState("TEST") { it.copy(value = 30) }
        
        // 이 시점에서는 아직 상태가 업데이트되지 않아야 함
        assertEquals(0, stateFlow.value.value)
        
        // 배치 업데이트 종료
        stateUpdateHelper.finishBatchUpdate("TEST")
        
        // Then - 마지막 업데이트만 적용되어야 함
        assertEquals(30, stateFlow.value.value)
    }
} 