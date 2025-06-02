package com.parker.hotkey.domain.util

import com.parker.hotkey.domain.model.state.BaseState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StateEventManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    
    private lateinit var stateEventManager: TestStateEventManager
    
    @Before
    fun setup() {
        stateEventManager = TestStateEventManager(testScope)
    }
    
    @Test
    fun `executeDebounced should execute only once after debounce time`() = testScope.runTest {
        // given
        var executionCount = 0
        val job = stateEventManager.executeDebounced(
            tag = "test",
            debounceTime = 100L
        ) {
            executionCount++
        }
        
        // when: 간격을 두고 여러번 호출
        advanceTimeBy(50L)  // 첫 번째 호출 후 50ms
        job.cancel()  // 첫 번째 작업 취소
        
        stateEventManager.executeDebounced(
            tag = "test",
            debounceTime = 100L
        ) {
            executionCount++
        }
        advanceTimeBy(50L)  // 두 번째 호출 후 50ms (아직 실행 안됨)
        
        stateEventManager.executeDebounced(
            tag = "test",
            debounceTime = 100L
        ) {
            executionCount++
        }
        advanceTimeBy(150L)  // 세 번째 호출 후 150ms (실행되어야 함)
        
        // then: 마지막 작업만 실행되어야 함
        assertEquals(1, executionCount)
    }
    
    @Test
    fun `subscribeFlow should receive values from flow`() = testScope.runTest {
        // given
        val testFlow = MutableSharedFlow<String>()
        val receivedValues = mutableListOf<String>()
        
        // when
        stateEventManager.subscribeFlow(
            flowProvider = { testFlow },
            tag = "test_subscription",
            onEach = { receivedValues.add(it) }
        )
        
        // 데이터 발행
        testFlow.emit("value1")
        testFlow.emit("value2")
        
        // then
        assertEquals(2, receivedValues.size)
        assertEquals("value1", receivedValues[0])
        assertEquals("value2", receivedValues[1])
    }
    
    @Test
    fun `updateState should update state value`() = testScope.runTest {
        // given
        val initialState = TestState(counter = 0, message = "Initial")
        stateEventManager.initializeState(initialState)
        
        // when
        stateEventManager.updateState("test") { state ->
            state.copy(counter = state.counter + 1, message = "Updated")
        }
        
        // then
        val currentState = stateEventManager.stateFlow.value
        assertEquals(1, currentState.counter)
        assertEquals("Updated", currentState.message)
    }
    
    @Test
    fun `processEvent should handle events with correct handler`() = testScope.runTest {
        // given
        val eventsFlow = MutableSharedFlow<TestEvent>()
        val receivedEvents = mutableListOf<TestEvent>()
        
        // when: 이벤트 핸들러 등록
        stateEventManager.on<TestEvent.Event1> { event ->
            receivedEvents.add(event)
        }
        
        stateEventManager.on<TestEvent.Event2> { event ->
            receivedEvents.add(event)
        }
        
        // Flow 구독
        stateEventManager.subscribe(this, { eventsFlow })
        
        // 이벤트 발행
        eventsFlow.emit(TestEvent.Event1("test1"))
        eventsFlow.emit(TestEvent.Event2(42))
        
        // then
        assertEquals(2, receivedEvents.size)
        assertEquals("test1", (receivedEvents[0] as TestEvent.Event1).message)
        assertEquals(42, (receivedEvents[1] as TestEvent.Event2).value)
    }
    
    // StateUpdateHelper와 EventHandler를 통합한 테스트 클래스
    private inner class TestStateEventManager(scope: TestScope) : StateEventManager(scope) {
        // StateFlow 관련 기능
        val stateFlow = MutableStateFlow(TestState())
        
        fun initializeState(state: TestState) {
            stateFlow.value = state
        }
        
        fun updateState(tag: String, update: (TestState) -> TestState) {
            val oldState = stateFlow.value
            val newState = update(oldState)
            stateFlow.value = newState
        }
        
        // 이벤트 처리 관련 기능
        private val handlers = mutableMapOf<Class<*>, suspend (TestEvent) -> Unit>()
        
        @Suppress("UNCHECKED_CAST")
        fun <T : TestEvent> on(eventType: Class<T>, handler: suspend (T) -> Unit) {
            handlers[eventType] = { event ->
                if (eventType.isInstance(event)) {
                    handler(event as T)
                }
            }
        }
        
        inline fun <reified T : TestEvent> on(noinline handler: suspend (T) -> Unit) {
            on(T::class.java, handler)
        }
        
        fun subscribe(owner: Any, flowProvider: () -> Flow<TestEvent>) {
            subscribeFlow(
                flowProvider = flowProvider,
                tag = owner.toString(),
                onEach = { event ->
                    val handlerKeys = handlers.keys.filter { it.isAssignableFrom(event.javaClass) }
                    handlerKeys.forEach { handlerKey ->
                        handlers[handlerKey]?.invoke(event)
                    }
                }
            )
        }
    }
    
    // 테스트용 상태 클래스
    data class TestState(
        val counter: Int = 0,
        val message: String = "",
        override val error: String? = null,
        override val isLoading: Boolean = false
    ) : BaseState

    // 테스트용 이벤트 클래스
    sealed class TestEvent {
        data class Event1(val message: String) : TestEvent()
        data class Event2(val value: Int) : TestEvent()
    }
} 