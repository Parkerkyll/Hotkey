package com.parker.hotkey.domain.util

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(MockitoJUnitRunner::class)
@Config(manifest = Config.NONE)
class AppStateManagerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences

    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    @Mock
    private lateinit var mockActivity: Activity

    private lateinit var appStateManager: AppStateManager

    @Before
    fun setup() {
        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockSharedPreferences)
        `when`(mockSharedPreferences.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putBoolean(anyString(), anyBoolean())).thenReturn(mockEditor)
        `when`(mockEditor.putInt(anyString(), anyInt())).thenReturn(mockEditor)
        
        // 기본값으로 최초 실행 완료되지 않은 상태
        `when`(mockSharedPreferences.getBoolean(eq("is_first_launch_completed"), anyBoolean())).thenReturn(false)
        
        appStateManager = AppStateManager(mockContext)
    }

    @Test
    fun `초기 상태는 FRESH_INSTALL이어야 함`() {
        // 최초 실행 완료되지 않았으므로 FRESH_INSTALL 상태여야 함
        assertEquals(AppStatus.FRESH_INSTALL, appStateManager.currentAppStatus.value)
    }
    
    @Test
    fun `최초 실행 완료 후 상태는 NORMAL_LAUNCH여야 함`() {
        // 최초 실행 완료 수행
        appStateManager.completeFirstLaunch()
        
        // NORMAL_LAUNCH 상태로 변경되었는지 확인
        assertEquals(AppStatus.NORMAL_LAUNCH, appStateManager.currentAppStatus.value)
        
        // SharedPreferences에 저장되었는지 확인
        verify(mockEditor).putBoolean(eq("is_first_launch_completed"), eq(true))
        verify(mockEditor).apply()
    }
    
    @Test
    fun `이미 설치된 앱의 초기 상태는 NORMAL_LAUNCH여야 함`() {
        // 최초 실행이 이미 완료된 상태로 설정
        `when`(mockSharedPreferences.getBoolean(eq("is_first_launch_completed"), anyBoolean())).thenReturn(true)
        
        // 새로운 인스턴스 생성
        val appStateManager = AppStateManager(mockContext)
        
        // NORMAL_LAUNCH 상태여야 함
        assertEquals(AppStatus.NORMAL_LAUNCH, appStateManager.currentAppStatus.value)
    }
    
    @Test
    fun `백그라운드로 이동 후 복귀 시 FOREGROUND_RESUME 상태여야 함`() {
        // Activity 시작
        appStateManager.onActivityStarted(mockActivity)
        
        // Activity 중지 (백그라운드로 이동)
        appStateManager.onActivityStopped(mockActivity)
        
        // 다시 Activity 시작 (포그라운드로 복귀)
        appStateManager.onActivityStarted(mockActivity)
        
        // FOREGROUND_RESUME 상태로 변경되었는지 확인
        assertEquals(AppStatus.FOREGROUND_RESUME, appStateManager.currentAppStatus.value)
    }
    
    @Test
    fun `앱 업데이트 감지 시 AFTER_UPDATE 상태여야 함`() {
        // 이전 버전 설정
        `when`(mockSharedPreferences.getInt(eq("last_update_version"), anyInt())).thenReturn(10)
        
        // 현재 버전이 더 높은 경우 업데이트로 감지되어야 함
        val isUpdated = appStateManager.checkIfAppUpdated(11)
        
        assertTrue(isUpdated)
        assertEquals(AppStatus.AFTER_UPDATE, appStateManager.currentAppStatus.value)
        
        // 업데이트 버전이 저장되었는지 확인
        verify(mockEditor).putInt(eq("last_update_version"), eq(11))
    }
    
    @Test
    fun `동일한 앱 버전일 경우 업데이트로 감지되지 않아야 함`() {
        // 이전 버전과 현재 버전이 동일하게 설정
        `when`(mockSharedPreferences.getInt(eq("last_update_version"), anyInt())).thenReturn(10)
        
        // 동일한 버전 체크
        val isUpdated = appStateManager.checkIfAppUpdated(10)
        
        assertFalse(isUpdated)
        
        // 상태가 변경되지 않아야 함 (초기값 유지)
        assertEquals(AppStatus.FRESH_INSTALL, appStateManager.currentAppStatus.value)
    }
    
    @Test
    fun `네비게이션 복귀 시 NAVIGATION_RETURN 상태여야 함`() {
        // 이전 프래그먼트 ID 설정 (101)
        `when`(mockSharedPreferences.getInt(eq("last_navigation_fragment"), anyInt())).thenReturn(101)
        
        // 맵 프래그먼트로 복귀 (102)
        appStateManager.checkNavigationReturn(102, 102)
        
        // NAVIGATION_RETURN 상태로 변경되었는지 확인
        assertEquals(AppStatus.NAVIGATION_RETURN, appStateManager.currentAppStatus.value)
        
        // 현재 프래그먼트 ID가 저장되었는지 확인
        verify(mockEditor).putInt(eq("last_navigation_fragment"), eq(102))
    }
    
    @Test
    fun `여러 Activity가 있는 경우 모든 Activity가 중지되어야 백그라운드로 감지`() {
        // 첫 번째 Activity 시작
        appStateManager.onActivityStarted(mockActivity)
        
        // 두 번째 Activity 시작
        val mockActivity2 = mock(Activity::class.java)
        appStateManager.onActivityStarted(mockActivity2)
        
        // 첫 번째 Activity 중지
        appStateManager.onActivityStopped(mockActivity)
        
        // 아직 두 번째 Activity가 활성화되어 있으므로 백그라운드로 간주되지 않음
        val initialStatus = appStateManager.currentAppStatus.value
        
        // 두 번째 Activity도 중지
        appStateManager.onActivityStopped(mockActivity2)
        
        // 이제 모든 Activity가 중지되었으므로, 다음 시작 시 FOREGROUND_RESUME으로 변경되어야 함
        appStateManager.onActivityStarted(mockActivity)
        
        assertEquals(AppStatus.FOREGROUND_RESUME, appStateManager.currentAppStatus.value)
    }
} 