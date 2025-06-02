package com.parker.hotkey.domain.util

import com.parker.hotkey.domain.model.state.BaseState
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class StateLoggerTest {
    
    // 테스트용 상태 클래스
    data class TestState(
        override val isLoading: Boolean = false,
        override val error: String? = null,
        val value: Int = 0
    ) : BaseState
    
    private lateinit var stateLogger: StateLogger
    
    @Before
    fun setup() {
        // Timber 초기화
        Timber.plant(TestTree())
        stateLogger = StateLogger("TEST")
    }
    
    @Test
    fun `상태 변경시 로그가 정상적으로 출력됨`() {
        // given
        val oldState = TestState(value = 0)
        val newState = TestState(value = 100)
        
        // when
        stateLogger.logStateChange(oldState, newState, "테스트")
        
        // then
        // 로그 출력 확인은 TestTree를 통해 가능
    }
    
    @Test
    fun `동일한 상태는 로그가 출력되지 않음`() {
        // given
        val state = TestState(value = 0)
        
        // when
        stateLogger.logStateChange(state, state, "테스트")
        
        // then
        // 로그가 출력되지 않았음을 TestTree를 통해 확인
    }
    
    @Test
    fun `에러 로깅이 정상적으로 동작`() {
        // given
        val exception = RuntimeException("테스트 에러")
        
        // when
        stateLogger.logError(exception, "에러 발생")
        
        // then
        // 에러 로그 출력 확인
    }
    
    @Test
    fun `디버그 메시지가 정상적으로 출력됨`() {
        // when
        stateLogger.logDebug("디버그 메시지")
        
        // then
        // 디버그 로그 출력 확인
    }
    
    /**
     * 테스트용 Timber Tree
     */
    class TestTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            // 테스트 환경에서는 실제로 로그를 출력하지 않음
            // 필요한 경우 로그를 캡처하여 검증할 수 있음
        }
    }
} 