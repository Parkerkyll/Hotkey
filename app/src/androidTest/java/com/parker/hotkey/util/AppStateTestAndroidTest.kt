package com.parker.hotkey.util

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber

/**
 * AppStateTest 유틸리티 클래스 테스트
 * 이 테스트는 AndroidTest(인스트루먼테이션 테스트)로 실행되어야 합니다.
 * Context 및 SharedPreferences에 실제 접근이 필요하기 때문입니다.
 */
@RunWith(AndroidJUnit4::class)
class AppStateTestAndroidTest {

    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences
    private val PREF_NAME = "app_state_prefs"
    private val KEY_FIRST_LAUNCH_COMPLETED = "is_first_launch_completed"

    @Before
    fun setUp() {
        // 시스템 로그로 먼저 출력
        android.util.Log.d("AppStateTestAndroidTest", "테스트 준비 시작")
        
        // Timber 초기화 - 테스트 환경에서는 로그 출력이 필요할 수 있음
        if (Timber.forest().isEmpty()) {
            Timber.plant(object : Timber.DebugTree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    android.util.Log.println(priority, "Timber[$tag]", message)
                }
            })
        }
        
        // 테스트 시작 로깅
        println("AppStateTestAndroidTest: 테스트 준비 시작")
        Timber.d("AppStateTestAndroidTest: 테스트 준비 시작")
        
        try {
            // 테스트용 Context 가져오기
            context = ApplicationProvider.getApplicationContext()
            android.util.Log.d("AppStateTestAndroidTest", "Context 획득 성공: $context")
            
            // SharedPreferences 초기화 (테스트 간 격리를 위해 항상 깨끗한 상태로 시작)
            preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            preferences.edit().clear().commit()
            
            println("AppStateTestAndroidTest: 테스트 준비 완료, SharedPreferences 초기화됨")
            Timber.d("AppStateTestAndroidTest: 테스트 준비 완료, SharedPreferences 초기화됨")
        } catch (e: Exception) {
            android.util.Log.e("AppStateTestAndroidTest", "초기화 오류", e)
            println("AppStateTestAndroidTest 초기화 오류: ${e.message}")
            Timber.e(e, "AppStateTestAndroidTest 초기화 오류")
            throw e
        }
    }

    @Test
    fun testSimulateFreshInstall() {
        try {
            println("AppStateTestAndroidTest: testSimulateFreshInstall 테스트 시작")
            Timber.d("AppStateTestAndroidTest: testSimulateFreshInstall 테스트 시작")
            
            // Given - 최초 설치 상태 시뮬레이션
            AppStateTest.simulateFreshInstall(context)
            
            // When - 현재 상태 확인
            val status = AppStateTest.checkCurrentStatus(context)
            
            // Then - 상태가 FRESH_INSTALL인지 확인
            println("AppStateTestAndroidTest: 기대값='FRESH_INSTALL', 실제값='$status'")
            Timber.d("AppStateTestAndroidTest: 기대값='FRESH_INSTALL', 실제값='$status'")
            assertEquals("FRESH_INSTALL", status)
            
            // SharedPreferences에 올바른 값이 설정되었는지 직접 확인
            val isFirstLaunchCompleted = preferences.getBoolean(KEY_FIRST_LAUNCH_COMPLETED, true)
            assertEquals(false, isFirstLaunchCompleted)
            
            println("AppStateTestAndroidTest: testSimulateFreshInstall 테스트 완료")
            Timber.d("AppStateTestAndroidTest: testSimulateFreshInstall 테스트 완료")
        } catch (e: Exception) {
            println("testSimulateFreshInstall 오류: ${e.message}")
            Timber.e(e, "testSimulateFreshInstall 오류")
            throw e
        }
    }

    @Test
    fun testSimulateNormalLaunch() {
        try {
            println("AppStateTestAndroidTest: testSimulateNormalLaunch 테스트 시작")
            Timber.d("AppStateTestAndroidTest: testSimulateNormalLaunch 테스트 시작")
            
            // Given - 일반 실행 상태 시뮬레이션
            AppStateTest.simulateNormalLaunch(context)
            
            // When - 현재 상태 확인
            val status = AppStateTest.checkCurrentStatus(context)
            
            // Then - 상태가 NORMAL_LAUNCH인지 확인
            println("AppStateTestAndroidTest: 기대값='NORMAL_LAUNCH', 실제값='$status'")
            Timber.d("AppStateTestAndroidTest: 기대값='NORMAL_LAUNCH', 실제값='$status'")
            assertEquals("NORMAL_LAUNCH", status)
            
            // SharedPreferences에 올바른 값이 설정되었는지 직접 확인
            val isFirstLaunchCompleted = preferences.getBoolean(KEY_FIRST_LAUNCH_COMPLETED, false)
            assertEquals(true, isFirstLaunchCompleted)
            
            println("AppStateTestAndroidTest: testSimulateNormalLaunch 테스트 완료")
            Timber.d("AppStateTestAndroidTest: testSimulateNormalLaunch 테스트 완료")
        } catch (e: Exception) {
            println("testSimulateNormalLaunch 오류: ${e.message}")
            Timber.e(e, "testSimulateNormalLaunch 오류")
            throw e
        }
    }

    @Test
    fun testStateTransition() {
        try {
            println("AppStateTestAndroidTest: testStateTransition 테스트 시작")
            Timber.d("AppStateTestAndroidTest: testStateTransition 테스트 시작")
            
            // Given - 처음에 최초 설치 상태로 시작
            AppStateTest.simulateFreshInstall(context)
            assertEquals("FRESH_INSTALL", AppStateTest.checkCurrentStatus(context))
            
            // When - 일반 실행 상태로 전환
            AppStateTest.simulateNormalLaunch(context)
            
            // Then - 상태가 NORMAL_LAUNCH로 변경되었는지 확인
            val status = AppStateTest.checkCurrentStatus(context)
            println("AppStateTestAndroidTest: 기대값='NORMAL_LAUNCH', 실제값='$status'")
            Timber.d("AppStateTestAndroidTest: 기대값='NORMAL_LAUNCH', 실제값='$status'")
            assertEquals("NORMAL_LAUNCH", status)
            
            println("AppStateTestAndroidTest: testStateTransition 테스트 완료")
            Timber.d("AppStateTestAndroidTest: testStateTransition 테스트 완료")
        } catch (e: Exception) {
            println("testStateTransition 오류: ${e.message}")
            Timber.e(e, "testStateTransition 오류")
            throw e
        }
    }

    @After
    fun tearDown() {
        try {
            // 테스트 후 SharedPreferences 정리 (선택 사항)
            preferences.edit().clear().commit()
            println("AppStateTestAndroidTest: 테스트 종료, SharedPreferences 정리됨")
            Timber.d("AppStateTestAndroidTest: 테스트 종료, SharedPreferences 정리됨")
        } catch (e: Exception) {
            println("tearDown 오류: ${e.message}")
            Timber.e(e, "tearDown 오류")
        }
    }
} 