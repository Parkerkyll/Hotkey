package com.parker.hotkey.domain.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class JobManagerTest {

    private lateinit var jobManager: JobManager<String>
    private lateinit var testScope: TestScope
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        jobManager = JobManager()
        testScope = TestScope(testDispatcher)
    }

    @Test
    fun `launch should execute the block`() = runTest {
        val executed = AtomicBoolean(false)
        
        jobManager.launch(
            scope = this,
            key = "test",
            debounceTime = 0L
        ) {
            executed.set(true)
        }
        
        advanceUntilIdle()
        assertTrue(executed.get())
    }

    @Test
    fun `launch with debounce should delay execution`() = runTest {
        val executed = AtomicBoolean(false)
        val debounceTime = 100L
        
        jobManager.launch(
            scope = this,
            key = "test",
            debounceTime = debounceTime
        ) {
            executed.set(true)
        }
        
        advanceUntilIdle()
        // 실행되지 않아야 함
        assertFalse(executed.get())
        
        // 디바운스 시간만큼 진행
        advanceTimeBy(debounceTime)
        advanceUntilIdle()
        
        // 실행되어야 함
        assertTrue(executed.get())
    }

    @Test
    fun `launch should cancel previous job with same key`() = runTest {
        val counter = AtomicInteger(0)
        val debounceTime = 100L
        
        // 첫 번째 작업 시작
        val job1 = jobManager.launch(
            scope = this,
            key = "test",
            debounceTime = debounceTime
        ) {
            delay(50)  // 약간의 지연 추가
            counter.incrementAndGet()
        }
        
        advanceUntilIdle()
        // 디바운스 지연 중
        advanceTimeBy(50)
        advanceUntilIdle()
        
        // 두 번째 작업 시작 (첫 번째 작업은 취소되어야 함)
        val job2 = jobManager.launch(
            scope = this,
            key = "test",
            debounceTime = debounceTime
        ) {
            counter.incrementAndGet()
        }
        
        // 모든 작업 완료를 위해 충분한 시간 진행
        advanceTimeBy(debounceTime + 100)
        advanceUntilIdle()
        
        // 두 번째 작업만 실행되어야 함
        assertEquals(1, counter.get())
        assertFalse(job1.isActive)
    }

    @Test
    fun `cancelJob should cancel active job`() = runTest {
        val executed = AtomicBoolean(false)
        val debounceTime = 100L
        
        jobManager.launch(
            scope = this,
            key = "test",
            debounceTime = debounceTime
        ) {
            executed.set(true)
        }
        
        advanceUntilIdle()
        // 작업 취소
        val cancelled = jobManager.cancelJob("test")
        advanceUntilIdle()
        
        // 취소되었는지 확인
        assertTrue(cancelled)
        
        // 충분한 시간이 경과해도 실행되지 않아야 함
        advanceTimeBy(debounceTime + 100)
        advanceUntilIdle()
        assertFalse(executed.get())
    }

    @Test
    fun `onCancel callback should be invoked when job is cancelled`() = runTest {
        val executed = AtomicBoolean(false)
        val cancelled = AtomicBoolean(false)
        val debounceTime = 100L
        
        jobManager.launch(
            scope = this,
            key = "test",
            debounceTime = debounceTime,
            onCancel = {
                cancelled.set(true)
            }
        ) {
            delay(200)  // 실행에 시간이 걸리는 작업
            executed.set(true)
        }
        
        advanceUntilIdle()
        // 디바운스 시간 경과
        advanceTimeBy(debounceTime)
        advanceUntilIdle()
        
        // 작업 실행 중 취소
        jobManager.cancelJob("test")
        advanceUntilIdle()
        
        // 취소 콜백이 호출되었는지 확인
        assertTrue(cancelled.get())
        assertFalse(executed.get())
    }

    @Test
    fun `isJobActive should return correct status`() = runTest {
        // 작업 시작 전
        assertFalse(jobManager.isJobActive("test"))
        
        // 작업 시작
        val job = jobManager.launch(
            scope = this,
            key = "test",
            debounceTime = 100L
        ) {
            delay(200)
        }
        
        advanceUntilIdle()
        // 디바운스 중에는 active
        assertTrue(jobManager.isJobActive("test"))
        
        // 작업 취소
        jobManager.cancelJob("test")
        advanceUntilIdle()
        
        // 취소 후에는 inactive
        assertFalse(jobManager.isJobActive("test"))
    }

    @Test
    fun `cancelAll should cancel all active jobs`() = runTest {
        val executed1 = AtomicBoolean(false)
        val executed2 = AtomicBoolean(false)
        
        // 첫 번째 작업
        jobManager.launch(
            scope = this,
            key = "test1",
            debounceTime = 100L
        ) {
            executed1.set(true)
        }
        
        // 두 번째 작업
        jobManager.launch(
            scope = this,
            key = "test2",
            debounceTime = 100L
        ) {
            executed2.set(true)
        }
        
        advanceUntilIdle()
        // 모든 작업 취소
        jobManager.cancelAll()
        advanceUntilIdle()
        
        // 충분한 시간이 경과해도 실행되지 않아야 함
        advanceTimeBy(200)
        advanceUntilIdle()
        assertFalse(executed1.get())
        assertFalse(executed2.get())
    }
} 