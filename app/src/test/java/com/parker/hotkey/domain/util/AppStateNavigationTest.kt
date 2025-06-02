package com.parker.hotkey.domain.util

import android.content.Context
import android.content.SharedPreferences
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@RunWith(MockitoJUnitRunner::class)
@Config(manifest = Config.NONE)
class AppStateNavigationTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences

    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    private lateinit var appStateManager: AppStateManager
    
    // 테스트용 프래그먼트 ID
    private val MAP_FRAGMENT_ID = 1001
    private val OTHER_FRAGMENT_ID = 1002

    @Before
    fun setup() {
        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockSharedPreferences)
        `when`(mockSharedPreferences.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putBoolean(anyString(), anyBoolean())).thenReturn(mockEditor)
        `when`(mockEditor.putInt(anyString(), anyInt())).thenReturn(mockEditor)
        
        // 기본값으로 최초 실행 완료된 상태
        `when`(mockSharedPreferences.getBoolean(eq("is_first_launch_completed"), anyBoolean())).thenReturn(true)
        
        appStateManager = AppStateManager(mockContext)
    }

    @Test
    fun `다른 화면에서 지도로 복귀 시 NAVIGATION_RETURN 상태로 설정된다`() {
        // given: 이전 프래그먼트 ID를 OTHER_FRAGMENT_ID로 설정
        `when`(mockSharedPreferences.getInt(eq("last_navigation_fragment"), anyInt())).thenReturn(OTHER_FRAGMENT_ID)
        
        // when: 지도 프래그먼트로 복귀
        appStateManager.checkNavigationReturn(MAP_FRAGMENT_ID, MAP_FRAGMENT_ID)
        
        // then: NAVIGATION_RETURN 상태로 변경되었는지 확인
        assertEquals(AppStatus.NAVIGATION_RETURN, appStateManager.currentAppStatus.value)
        assertTrue(appStateManager.isNavigationReturn())
        
        // 현재 프래그먼트 ID가 저장되었는지 확인
        verify(mockEditor).putInt(eq("last_navigation_fragment"), eq(MAP_FRAGMENT_ID))
    }
    
    @Test
    fun `지도 화면에서 다른 화면으로 이동 시 NAVIGATION_RETURN 상태가 아니다`() {
        // given: 이전 프래그먼트 ID를 MAP_FRAGMENT_ID로 설정
        `when`(mockSharedPreferences.getInt(eq("last_navigation_fragment"), anyInt())).thenReturn(MAP_FRAGMENT_ID)
        
        // when: 다른 프래그먼트로 이동
        appStateManager.checkNavigationReturn(OTHER_FRAGMENT_ID, MAP_FRAGMENT_ID)
        
        // then: NAVIGATION_RETURN 상태가 아닌지 확인
        assertFalse(appStateManager.currentAppStatus.value == AppStatus.NAVIGATION_RETURN)
        assertFalse(appStateManager.isNavigationReturn())
        
        // 현재 프래그먼트 ID가 저장되었는지 확인
        verify(mockEditor).putInt(eq("last_navigation_fragment"), eq(OTHER_FRAGMENT_ID))
    }
    
    @Test
    fun `앱 시작 시 바로 지도 화면으로 이동하면 NAVIGATION_RETURN 상태가 아니다`() {
        // given: 이전 프래그먼트 ID가 없는 상태 (기본값 0)
        `when`(mockSharedPreferences.getInt(eq("last_navigation_fragment"), anyInt())).thenReturn(0)
        
        // when: 지도 프래그먼트로 처음 이동
        appStateManager.checkNavigationReturn(MAP_FRAGMENT_ID, MAP_FRAGMENT_ID)
        
        // then: NAVIGATION_RETURN 상태가 아닌지 확인 (이전 화면이 없으므로)
        assertFalse(appStateManager.currentAppStatus.value == AppStatus.NAVIGATION_RETURN)
        assertFalse(appStateManager.isNavigationReturn())
    }
    
    @Test
    fun `상태 재설정 후에는 NAVIGATION_RETURN 상태가 해제된다`() {
        // given: NAVIGATION_RETURN 상태 설정
        `when`(mockSharedPreferences.getInt(eq("last_navigation_fragment"), anyInt())).thenReturn(OTHER_FRAGMENT_ID)
        appStateManager.checkNavigationReturn(MAP_FRAGMENT_ID, MAP_FRAGMENT_ID)
        assertTrue(appStateManager.isNavigationReturn())
        
        // when: 상태 재설정
        appStateManager.resetNavigationReturnStatus()
        
        // then: NAVIGATION_RETURN 상태가 해제되었는지 확인
        assertFalse(appStateManager.isNavigationReturn())
    }
} 