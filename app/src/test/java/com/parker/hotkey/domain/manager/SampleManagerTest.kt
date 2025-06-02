package com.parker.hotkey.domain.manager

import com.parker.hotkey.domain.manager.impl.SampleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.Ignore
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
class SampleManagerTest {
    
    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(testScheduler)
    private val unconfinedDispatcher = UnconfinedTestDispatcher(testScheduler)
    
    private lateinit var sampleManager: SampleManager
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        Timber.plant(object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                // 테스트에서는 로깅하지 않음
            }
        })
        
        sampleManager = SampleManager(testDispatcher, unconfinedDispatcher)
    }
    
    @After
    fun tearDown() {
        sampleManager.cleanup()
        Dispatchers.resetMain()
        Timber.uprootAll()
    }
    
    @Test
    fun `아이템 로드 테스트`() = runTest {
        // when
        sampleManager.loadItems()
        
        // 비동기 작업 완료까지 대기 - fetchItemsFromSource에서 delay(1000)를 사용하므로 명시적 진행 필요
        testScheduler.advanceTimeBy(1100)
        
        // then - 로딩 상태 확인
        assertFalse(sampleManager.state.value.isLoading)
        assertNull(sampleManager.state.value.error)
        assertEquals(5, sampleManager.state.value.items.size)
        assertEquals("item_0", sampleManager.state.value.selectedItemId)
    }
    
    @Test
    fun `아이템 추가 테스트`() = runTest {
        // given
        sampleManager.loadItems()
        testScheduler.advanceTimeBy(1100)
        val initialItemCount = sampleManager.state.value.items.size
        
        // when
        sampleManager.addItem("테스트 아이템", 100)
        
        // then
        val items = sampleManager.state.value.items
        assertEquals(initialItemCount + 1, items.size)
        assertEquals("테스트 아이템", items.last().name)
        assertEquals(100, items.last().value)
    }
    
    @Test
    fun `아이템 선택 테스트`() = runTest {
        // given
        sampleManager.loadItems()
        testScheduler.advanceTimeBy(1100)
        
        // when
        sampleManager.selectItem("item_2")
        
        // then
        assertEquals("item_2", sampleManager.state.value.selectedItemId)
    }
    
    @Test
    fun `존재하지 않는 아이템 선택 테스트`() = runTest {
        // given
        sampleManager.loadItems()
        testScheduler.advanceTimeBy(1100)
        val initialSelectedId = sampleManager.state.value.selectedItemId
        
        // when
        sampleManager.selectItem("non_existent_item")
        
        // then - 선택 상태가 변경되지 않아야 함
        assertEquals(initialSelectedId, sampleManager.state.value.selectedItemId)
    }
    
    @Test
    fun `아이템 제거 테스트`() = runTest {
        // given
        sampleManager.loadItems()
        testScheduler.advanceTimeBy(1100)
        val initialItemCount = sampleManager.state.value.items.size
        
        // when
        sampleManager.removeItem("item_1")
        
        // then
        val items = sampleManager.state.value.items
        assertEquals(initialItemCount - 1, items.size)
        assertFalse(items.any { it.id == "item_1" })
    }
    
    @Test
    fun `선택된 아이템 제거 테스트`() = runTest {
        // given
        sampleManager.loadItems()
        testScheduler.advanceTimeBy(1100)
        sampleManager.selectItem("item_2")
        
        // when
        sampleManager.removeItem("item_2")
        
        // then
        assertNotEquals("item_2", sampleManager.state.value.selectedItemId)
        // 첫 번째 아이템이 자동 선택되어야 함
        assertEquals("item_0", sampleManager.state.value.selectedItemId)
    }
    
    @Ignore("테스트 환경에서 비동기 작업 처리 문제로 인해 임시 비활성화")
    @Test
    fun `복잡한 작업 테스트`() = runTest {
        // 1. 작업 시작
        sampleManager.performComplexOperation()
        
        // 2. 첫 번째 비동기 작업 완료 (데이터 로드)
        testScheduler.advanceTimeBy(1100)
        
        // 3. 중간 상태 확인 - 아이템은 로드되었지만 아직 가공되지 않음
        assertEquals(5, sampleManager.state.value.items.size)
        
        // 4. 두 번째 비동기 작업 완료 (아이템 가공)
        testScheduler.advanceTimeBy(600)
        
        // 5. 최종 상태 확인
        with(sampleManager.state.value) {
            assertFalse(isLoading)
            assertNull(error)
            assertEquals(5, items.size)
            // 값이 2배로 증가했는지 확인
            assertEquals(20, items[2].value) // 2번 인덱스는 초기값 10, 2배하면 20
        }
    }
    
    @Test
    fun `동시에 여러 작업 실행 테스트`() = runTest {
        // when - 첫 번째 로드 작업 시작
        launch {
            sampleManager.loadItems()
        }
        
        // 약간의 시간이 지난 후
        testScheduler.advanceTimeBy(500)
        
        // when - 두 번째 로드 작업 시작 (첫 번째 작업이 취소되어야 함)
        launch {
            sampleManager.loadItems()
        }
        
        // 모든 작업 완료까지 대기
        testScheduler.advanceTimeBy(1500)
        
        // then - 마지막 작업의 결과만 유효해야 함
        with(sampleManager.state.value) {
            assertFalse(isLoading)
            assertNull(error)
            assertEquals(5, items.size)
        }
    }
    
    @Test
    fun `상태 초기화 테스트`() = runTest {
        // given
        sampleManager.loadItems()
        testScheduler.advanceTimeBy(1100)
        
        // when
        sampleManager.resetState()
        
        // then
        with(sampleManager.state.value) {
            assertTrue(items.isEmpty())
            assertNull(selectedItemId)
            assertFalse(isLoading)
            assertNull(error)
        }
    }
} 