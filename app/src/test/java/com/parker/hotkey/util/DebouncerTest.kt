package com.parker.hotkey.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DebouncerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var debouncer: Debouncer<String>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        debouncer = Debouncer(500L)  // 기본 500ms 디바운스 타임
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `debounce 함수는 타임아웃 내에 여러 번 호출 시 마지막 호출만 실행한다`() = testScope.runTest {
        var executionCount = 0
        var lastValue = ""

        // 500ms 디바운스 타임으로 설정
        val debounceTime = 500L

        // 첫 번째 호출
        debouncer.debounce("first") {
            executionCount++
            lastValue = it
        }

        // 시간 진행
        advanceTimeBy(100L)

        // 두 번째 호출 (디바운스 타임 내에 발생)
        debouncer.debounce("second") {
            executionCount++
            lastValue = it
        }

        // 시간 진행
        advanceTimeBy(100L)

        // 세 번째 호출 (디바운스 타임 내에 발생)
        debouncer.debounce("third") {
            executionCount++
            lastValue = it
        }

        // 디바운스 타임보다 조금 더 기다림
        advanceTimeBy(debounceTime + 100L)

        // 마지막 호출만 실행되었는지 확인
        assertEquals(1, executionCount)
        assertEquals("third", lastValue)
    }

    @Test
    fun `debounce 함수는 서로 다른 키로 호출 시 각각 독립적으로 실행된다`() = testScope.runTest {
        var key1ExecutionCount = 0
        var key2ExecutionCount = 0

        // 첫 번째 debouncer
        val debouncer1 = Debouncer<String>(500L)
        
        // 두 번째 debouncer
        val debouncer2 = Debouncer<String>(500L)

        // key1으로 호출
        debouncer1.debounce("value1") {
            key1ExecutionCount++
        }

        // key2로 호출
        debouncer2.debounce("value2") {
            key2ExecutionCount++
        }

        // 디바운스 타임보다 충분히 기다림
        advanceTimeBy(600L)

        // 각 키에 대한 콜백이 한 번씩 실행되었는지 확인
        assertEquals(1, key1ExecutionCount)
        assertEquals(1, key2ExecutionCount)
    }

    @Test
    fun `debounce 함수는 디바운스 타임 이후에 호출 시 새로운 콜백을 실행한다`() = testScope.runTest {
        var executionCount = 0

        // 300ms 디바운스 타임으로 설정
        val customDebouncer = Debouncer<String>(300L)

        // 첫 번째 호출
        customDebouncer.debounce("value") {
            executionCount++
        }

        // 디바운스 타임보다 충분히 기다림
        advanceTimeBy(400L)

        // 실행 횟수 확인
        assertEquals(1, executionCount)

        // 두 번째 호출 (디바운스 타임 이후)
        customDebouncer.debounce("value") {
            executionCount++
        }

        // 디바운스 타임보다 충분히 기다림
        advanceTimeBy(400L)

        // 실행 횟수 확인 (2번 실행되어야 함)
        assertEquals(2, executionCount)
    }
} 