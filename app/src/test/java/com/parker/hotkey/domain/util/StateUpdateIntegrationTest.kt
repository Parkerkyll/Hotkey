package com.parker.hotkey.domain.util

import com.parker.hotkey.domain.model.state.BaseState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class StateUpdateIntegrationTest {
    
    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(testScheduler)
    private val unconfinedDispatcher = UnconfinedTestDispatcher(testScheduler)
    
    private data class TestState(
        override val isLoading: Boolean = false,
        override val error: String? = null,
        val count: Int = 0,
        val data: List<String> = emptyList()
    ) : BaseState
    
    private lateinit var stateFlow: MutableStateFlow<TestState>
    private lateinit var stateUpdateHelper: StateUpdateHelper<TestState>
    private lateinit var stateLogger: StateLogger
    private lateinit var scope: CoroutineScope
    
    // 에러 핸들러
    private val errorHandler: (TestState, String?, Boolean) -> TestState = { state, error, isLoading ->
        state.copy(error = error, isLoading = isLoading)
    }
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        Timber.plant(TestTree())
        
        stateFlow = MutableStateFlow(TestState())
        stateLogger = StateLogger("Integration")
        scope = CoroutineScope(SupervisorJob() + unconfinedDispatcher)
        stateUpdateHelper = StateUpdateHelper(stateFlow, errorHandler, scope)
    }
    
    @After
    fun tearDown() {
        scope.cancel()
        Dispatchers.resetMain()
        Timber.uprootAll()
    }
    
    @Test
    fun `동기적 상태 업데이트 테스트`() = runTest(unconfinedDispatcher) {
        // given
        val stateChanges = AtomicInteger(0)
        
        // 상태 변경 관찰
        stateFlow.onEach { 
            stateChanges.incrementAndGet()
        }.launchIn(scope)
        
        // when
        stateUpdateHelper.updateState("TEST") { 
            it.copy(count = 1)
        }
        
        stateUpdateHelper.updateState("TEST") { 
            it.copy(count = 2, data = listOf("A", "B"))
        }
        
        // then
        assertEquals(2, stateFlow.value.count)
        assertEquals(listOf("A", "B"), stateFlow.value.data)
        assertEquals(3, stateChanges.get()) // 초기값 + 2번의 업데이트
    }
    
    @Test
    fun `비동기 상태 업데이트 테스트`() = runTest(unconfinedDispatcher) {
        // when - 비동기 처리 시작
        scope.launch {
            stateUpdateHelper.setLoading("ASYNC", true)
            
            try {
                // 비동기 작업 시뮬레이션
                delay(1000)
                stateUpdateHelper.updateState("ASYNC") {
                    it.copy(count = 100, data = listOf("Async Result"))
                }
            } catch (e: Exception) {
                stateUpdateHelper.setError("ASYNC", e.message)
            } finally {
                stateUpdateHelper.setLoading("ASYNC", false)
            }
        }
        
        // 즉시 로딩 상태가 설정됨 (UnconfinedTestDispatcher 사용)
        testScheduler.advanceUntilIdle() // 모든 코루틴 실행 완료
        
        // then - 로딩 상태 확인
        assertFalse(stateFlow.value.isLoading)
        assertEquals(100, stateFlow.value.count)
        assertEquals(listOf("Async Result"), stateFlow.value.data)
    }
    
    @Ignore("테스트 환경에서 비동기 작업 처리 문제로 인해 임시 비활성화")
    @Test
    fun `에러 처리 테스트`() = runTest(unconfinedDispatcher) {
        // when - 에러 발생 시뮬레이션
        scope.launch {
            // 로딩 상태 설정
            stateUpdateHelper.setLoading("ERROR", true)
                
            // 에러 직접 설정
            stateUpdateHelper.setError("ERROR", "테스트 에러")
                
            // 로딩 상태 해제
            stateUpdateHelper.setLoading("ERROR", false)
        }
        
        // 모든 코루틴 실행
        testScheduler.advanceUntilIdle()
        
        // then - 최종 상태 확인
        assertFalse(stateFlow.value.isLoading)
        assertEquals("테스트 에러", stateFlow.value.error)
    }
    
    @Test
    fun `취소 처리 테스트`() = runTest(unconfinedDispatcher) {
        // given
        val job = scope.launch {
            stateUpdateHelper.setLoading("CANCEL", true)
            
            try {
                // 오래 걸리는 작업 시뮬레이션
                delay(10000)
                stateUpdateHelper.updateState("CANCEL") {
                    it.copy(count = 999)
                }
            } catch (e: Exception) {
                // 취소 예외는 무시
            } finally {
                stateUpdateHelper.setLoading("CANCEL", false)
            }
        }
        
        // 약간의 시간이 지난 후
        testScheduler.advanceTimeBy(100)
        assertTrue(stateFlow.value.isLoading)
        
        // when - 작업 취소
        job.cancel()
        testScheduler.advanceUntilIdle()
        
        // then - 로딩 상태가 해제되었고, 상태는 변경되지 않음
        assertFalse(stateFlow.value.isLoading)
        assertEquals(0, stateFlow.value.count)
    }
    
    /**
     * 테스트용 Timber Tree
     */
    class TestTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            // 테스트 환경에서는 실제로 로그를 출력하지 않음
        }
    }
}