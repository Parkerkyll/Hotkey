/*
package com.parker.hotkey.presentation.main

import app.cash.turbine.test
import com.parker.hotkey.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class MainViewModelTest {
    
    private lateinit var viewModel: MainViewModel
    private val testDispatcher = StandardTestDispatcher()
    
    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = MainViewModel()
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `드로어 열기-닫기 상태 변경 테스트`() = runTest {
        // 초기 상태 확인
        assertFalse(viewModel.isDrawerOpen.value)
        
        // 드로어 열기
        viewModel.openDrawer()
        assertTrue(viewModel.isDrawerOpen.value)
        
        // 드로어 닫기
        viewModel.closeDrawer()
        assertFalse(viewModel.isDrawerOpen.value)
    }
    
    @Test
    fun `뒤로가기 처리 테스트 - 드로어 열려있을 때`() = runTest {
        // 드로어 열기
        viewModel.openDrawer()
        
        // 뒤로가기 처리
        val result = viewModel.onBackPressed()
        
        // 드로어가 닫히고 true 반환 (처리됨)
        assertTrue(result)
        assertFalse(viewModel.isDrawerOpen.value)
    }
    
    @Test
    fun `뒤로가기 처리 테스트 - 드로어 닫혀있을 때`() = runTest {
        // 드로어 닫기
        viewModel.closeDrawer()
        
        // 뒤로가기 처리
        val result = viewModel.onBackPressed()
        
        // false 반환 (처리 안됨)
        assertFalse(result)
        assertFalse(viewModel.isDrawerOpen.value)
    }
    
    @Test
    fun `네비게이션 아이템 선택 - 공지사항`() = runTest {
        viewModel.navigationEvent.test {
            // 공지사항 메뉴 선택
            val result = viewModel.onNavigationItemSelected(R.id.nav_notice)
            
            // 결과 확인
            assertTrue(result)
            assertEquals(MainViewModel.NavigationState.Notice, viewModel.navigationState.value)
            assertEquals(MainViewModel.NavigationEvent.NavigateToNotice, awaitItem())
            
            // 드로어가 닫혔는지 확인
            assertFalse(viewModel.isDrawerOpen.value)
        }
    }
    
    @Test
    fun `네비게이션 아이템 선택 - 사용법`() = runTest {
        viewModel.navigationEvent.test {
            // 사용법 메뉴 선택
            val result = viewModel.onNavigationItemSelected(R.id.nav_help)
            
            // 결과 확인
            assertTrue(result)
            assertEquals(MainViewModel.NavigationState.Help, viewModel.navigationState.value)
            assertEquals(MainViewModel.NavigationEvent.NavigateToHelp, awaitItem())
            
            // 드로어가 닫혔는지 확인
            assertFalse(viewModel.isDrawerOpen.value)
        }
    }
    
    @Test
    fun `네비게이션 아이템 선택 - 내 정보`() = runTest {
        viewModel.navigationEvent.test {
            // 내 정보 메뉴 선택
            val result = viewModel.onNavigationItemSelected(R.id.nav_profile)
            
            // 결과 확인
            assertTrue(result)
            assertEquals(MainViewModel.NavigationState.Profile, viewModel.navigationState.value)
            assertEquals(MainViewModel.NavigationEvent.NavigateToProfile, awaitItem())
            
            // 드로어가 닫혔는지 확인
            assertFalse(viewModel.isDrawerOpen.value)
        }
    }
    
    @Test
    fun `네비게이션 아이템 선택 - 출근부`() = runTest {
        viewModel.navigationEvent.test {
            // 출근부 메뉴 선택
            val result = viewModel.onNavigationItemSelected(R.id.nav_attendance)
            
            // 결과 확인
            assertTrue(result)
            assertEquals(MainViewModel.NavigationState.Attendance, viewModel.navigationState.value)
            assertEquals(MainViewModel.NavigationEvent.NavigateToAttendance, awaitItem())
            
            // 드로어가 닫혔는지 확인
            assertFalse(viewModel.isDrawerOpen.value)
        }
    }
    
    @Test
    fun `네비게이션 아이템 선택 - 내 동선 추적`() = runTest {
        viewModel.navigationEvent.test {
            // 내 동선 추적 메뉴 선택
            val result = viewModel.onNavigationItemSelected(R.id.nav_tracking)
            
            // 결과 확인
            assertTrue(result)
            assertEquals(MainViewModel.NavigationState.Tracking, viewModel.navigationState.value)
            assertEquals(MainViewModel.NavigationEvent.NavigateToTracking, awaitItem())
            
            // 드로어가 닫혔는지 확인
            assertFalse(viewModel.isDrawerOpen.value)
        }
    }
    
    @Test
    fun `네비게이션 아이템 선택 - 지원되지 않는 메뉴`() = runTest {
        // 지원되지 않는 메뉴 ID
        val result = viewModel.onNavigationItemSelected(-1)
        
        // 결과 확인 (false를 반환해야 함)
        assertFalse(result)
    }
}
*/ 