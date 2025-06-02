package com.parker.hotkey.domain.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class EventHandlerTest {

    private lateinit var eventHandler: EventHandler<TestEvent>
    private lateinit var testScope: TestScope
    private lateinit var eventsFlow: MutableSharedFlow<TestEvent>

    @Before
    fun setup() {
        val testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        eventHandler = EventHandler(testScope)
        eventsFlow = MutableSharedFlow(extraBufferCapacity = 10)
    }

    @Test
    fun `on should register event handler`() = runTest {
        val handledA = AtomicBoolean(false)
        val handledB = AtomicBoolean(false)

        // A 이벤트 핸들러 등록
        eventHandler.on<TestEventA> {
            handledA.set(true)
        }

        // B 이벤트 핸들러 등록
        eventHandler.on<TestEventB> {
            handledB.set(true)
        }

        // 이벤트 스트림 구독
        eventHandler.subscribe(this, { eventsFlow })

        // A 이벤트 발행
        eventsFlow.emit(TestEventA)
        advanceUntilIdle()

        // A 이벤트만 처리되어야 함
        assertTrue(handledA.get())
        assertTrue(!handledB.get())

        // B 이벤트 발행
        eventsFlow.emit(TestEventB)
        advanceUntilIdle()

        // B 이벤트도 처리되어야 함
        assertTrue(handledB.get())
    }

    @Test
    fun `subscribe should process events`() = runTest {
        val counter = AtomicInteger(0)

        // 핸들러 등록
        eventHandler.on<TestEventA> {
            counter.incrementAndGet()
        }

        // 이벤트 스트림 구독
        eventHandler.subscribe(this, { eventsFlow })

        // 여러 이벤트 발행
        repeat(3) {
            eventsFlow.emit(TestEventA)
        }
        advanceUntilIdle()

        // 모든 이벤트가 처리되어야 함
        assertEquals(3, counter.get())
    }

    @Test
    fun `subscribeWithHandler should use custom handler`() = runTest {
        val counter = AtomicInteger(0)

        // 커스텀 핸들러로 구독
        eventHandler.subscribeWithHandler(
            owner = this,
            flowProvider = { eventsFlow },
            handler = { _: TestEvent ->
                counter.incrementAndGet()
            }
        )

        // 다양한 이벤트 발행
        eventsFlow.emit(TestEventA)
        eventsFlow.emit(TestEventB)
        advanceUntilIdle()

        // 모든 이벤트가 커스텀 핸들러로 처리되어야 함
        assertEquals(2, counter.get())
    }

    @Test
    fun `unsubscribe should stop event processing`() = runTest {
        val counter = AtomicInteger(0)

        // 핸들러 등록
        eventHandler.on<TestEventA> {
            counter.incrementAndGet()
        }

        // 이벤트 스트림 구독
        eventHandler.subscribe(this, { eventsFlow })

        // 첫 번째 이벤트 발행
        eventsFlow.emit(TestEventA)
        advanceUntilIdle()
        assertEquals(1, counter.get())

        // 구독 취소
        eventHandler.unsubscribe(this)

        // 추가 이벤트 발행
        eventsFlow.emit(TestEventA)
        advanceUntilIdle()

        // 카운터가 증가하지 않아야 함
        assertEquals(1, counter.get())
    }

    @Test
    fun `unsubscribeAll should stop all event processing`() = runTest {
        val counterA = AtomicInteger(0)
        val counterB = AtomicInteger(0)
        val owner1 = "owner1"
        val owner2 = "owner2"

        // 핸들러 등록
        eventHandler.on<TestEventA> {
            counterA.incrementAndGet()
        }

        // 다른 소유자로 구독
        eventHandler.subscribe(owner1, { eventsFlow })
        eventHandler.subscribeWithHandler(
            owner = owner2,
            flowProvider = { eventsFlow },
            handler = { _: TestEvent ->
                counterB.incrementAndGet()
            }
        )

        // 첫 번째 이벤트 발행
        eventsFlow.emit(TestEventA)
        advanceUntilIdle()
        assertEquals(1, counterA.get())
        assertEquals(1, counterB.get())

        // 모든 구독 취소
        eventHandler.unsubscribeAll()

        // 추가 이벤트 발행
        eventsFlow.emit(TestEventA)
        advanceUntilIdle()

        // 카운터가 증가하지 않아야 함
        assertEquals(1, counterA.get())
        assertEquals(1, counterB.get())
    }
}

// 테스트용 이벤트 클래스
sealed class TestEvent
object TestEventA : TestEvent()
object TestEventB : TestEvent() 