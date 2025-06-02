package com.parker.hotkey.domain.manager

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@ExperimentalCoroutinesApi
class BaseManagerTest {

    private lateinit var testScope: TestScope
    private lateinit var manager: TestBaseManager

    @Before
    fun setup() {
        testScope = TestScope()
        manager = TestBaseManager(testScope)
    }

    @Test
    fun `launchDebounced 함수는 executeDebouncedWithCancel를 활용함`() = testScope.runTest {
        // 준비
        val executed = AtomicBoolean(false)
        val debounceTime = 100L

        // 실행
        manager.testLaunchDebounced("test", debounceTime) {
            executed.set(true)
        }

        // 디바운스 시간 전에는 실행되지 않음
        advanceTimeBy(50L)
        assertFalse(executed.get())

        // 디바운스 시간 후에는 실행됨
        advanceTimeBy(100L)
        assertTrue(executed.get())
    }

    @Test
    fun `launchDebounced 함수는 클래스 이름을 키에 포함함`() = testScope.runTest {
        // 준비
        val key = "test_key"
        val expectedTagSubstring = "TestBaseManager_$key"
        val actualTag = manager.captureTag { manager.testLaunchDebounced(key, 0) {} }
        
        // 실행 및 검증
        assertTrue("태그에 클래스 이름이 포함되어야 함", actualTag.contains(expectedTagSubstring))
    }

    @Test
    fun `launchCancellable 함수는 디바운스 없이 즉시 실행됨`() = testScope.runTest {
        // 준비
        val executed = AtomicBoolean(false)

        // 실행
        manager.testLaunchCancellable("test") {
            executed.set(true)
        }

        // 즉시 실행됨 (디바운스 없음)
        assertTrue(executed.get())
    }

    @Test
    fun `초기화 상태는 setInitialized로 설정 가능함`() = testScope.runTest {
        // 초기 상태 확인
        assertFalse(manager.initialized.value)
        
        // 초기화 상태 설정
        manager.testSetInitialized()
        
        // 상태 변경 확인
        assertTrue(manager.initialized.value)
    }

    @Test
    fun `cleanup 메서드는 상태를 초기화하고 상위 클래스의 cancelAll을 호출함`() = testScope.runTest {
        // 준비
        val counter = AtomicInteger(0)
        
        // 작업 실행 및 상태 초기화
        manager.testSetInitialized()
        manager.testLaunchDebounced("test", 100L) {
            counter.incrementAndGet()
        }
        
        // 정리 실행
        manager.cleanup()
        
        // 정리 후 상태 확인
        assertFalse(manager.initialized.value)
        
        // 작업이 취소되어 실행되지 않음
        advanceTimeBy(200L)
        assertEquals(0, counter.get())
    }
    
    /**
     * 테스트용 BaseManager 구현
     */
    private class TestBaseManager(scope: TestScope) : BaseManager<String>(scope) {
        private var capturedTag: String? = null
        
        fun testLaunchDebounced(key: String, debounceTime: Long, block: suspend () -> Unit): Job {
            return launchDebounced(key, debounceTime, null, block)
        }
        
        fun testLaunchCancellable(key: String, block: suspend () -> Unit): Job {
            return launchCancellable(key, block)
        }
        
        fun testSetInitialized() {
            setInitialized()
        }
        
        fun captureTag(block: () -> Unit): String {
            capturedTag = null
            
            // 원본 메서드 오버라이드
            val originalMethod = this::executeDebouncedWithCancel
            
            // 메서드를 임시로 덮어씌움
            val testManager = this
            object : BaseManager<String>(scope) {
                override fun executeDebouncedWithCancel(
                    tag: String,
                    debounceTime: Long,
                    onCancel: (suspend () -> Unit)?,
                    block: suspend () -> Unit
                ): Job {
                    testManager.capturedTag = tag
                    return super.executeDebouncedWithCancel(tag, debounceTime, onCancel, block)
                }
            }.also {
                // 테스트 블록 실행
                block()
            }
            
            return capturedTag ?: throw IllegalStateException("태그가 캡처되지 않음")
        }
        
        // 이벤트 발행 테스트를 위한 메서드
        suspend fun testEmitEvent(event: String) {
            bufferOrEmitEvent(event)
        }
        
        // 에러 이벤트 생성 테스트를 위한 오버라이드
        override fun createErrorEvent(throwable: Throwable, message: String): String {
            return "Error: ${throwable.javaClass.simpleName} - $message"
        }
    }
} 