package com.parker.hotkey.util

import android.content.Context
import android.content.SharedPreferences
import com.parker.hotkey.domain.util.AppStatus
import timber.log.Timber

/**
 * 개발 및 테스트 단계에서 앱 상태를 시뮬레이션하기 위한 유틸리티 클래스
 * 실제 프로덕션 코드에서는 사용되지 않고, 5.2 단계의 테스트 과정에서만 활용됩니다.
 */
object AppStateTest {
    private const val PREF_NAME = "app_state_prefs"
    private const val KEY_FIRST_LAUNCH_COMPLETED = "is_first_launch_completed"
    private const val TAG = "AppStateTest"

    /**
     * 앱이 최초 설치된 상태를 시뮬레이션합니다.
     * SharedPreferences에서 최초 실행 여부를 false로 설정합니다.
     */
    fun simulateFreshInstall(context: Context) {
        try {
            val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            preferences.edit()
                .putBoolean(KEY_FIRST_LAUNCH_COMPLETED, false)
                .apply()
            val message = "최초 설치 상태(FRESH_INSTALL)로 시뮬레이션되었습니다."
            println("$TAG: $message")
            try {
                Timber.tag(TAG).d(message)
            } catch (e: Exception) {
                println("$TAG: Timber 로깅 실패 - ${e.message}")
            }
        } catch (e: Exception) {
            println("$TAG: simulateFreshInstall 실패 - ${e.message}")
            try {
                Timber.tag(TAG).e(e, "simulateFreshInstall 실패")
            } catch (ignored: Exception) {
                // Timber 초기화 문제일 경우 무시
            }
            throw e
        }
    }

    /**
     * 앱이 정상적으로 실행되는 상태를 시뮬레이션합니다.
     * SharedPreferences에서 최초 실행 여부를 true로 설정합니다.
     */
    fun simulateNormalLaunch(context: Context) {
        try {
            val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            preferences.edit()
                .putBoolean(KEY_FIRST_LAUNCH_COMPLETED, true)
                .apply()
            val message = "일반 실행 상태(NORMAL_LAUNCH)로 시뮬레이션되었습니다."
            println("$TAG: $message")
            try {
                Timber.tag(TAG).d(message)
            } catch (e: Exception) {
                println("$TAG: Timber 로깅 실패 - ${e.message}")
            }
        } catch (e: Exception) {
            println("$TAG: simulateNormalLaunch 실패 - ${e.message}")
            try {
                Timber.tag(TAG).e(e, "simulateNormalLaunch 실패")
            } catch (ignored: Exception) {
                // Timber 초기화 문제일 경우 무시
            }
            throw e
        }
    }

    /**
     * 현재 AppStatus 값을 확인합니다.
     * 이 메서드는 디버깅 및 테스트 용도로만 사용합니다.
     */
    fun checkCurrentStatus(context: Context): String {
        try {
            val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val isFirstLaunchCompleted = preferences.getBoolean(KEY_FIRST_LAUNCH_COMPLETED, false)
            val statusName = if (isFirstLaunchCompleted) "NORMAL_LAUNCH" else "FRESH_INSTALL"
            
            val message = "현재 상태: $statusName (isFirstLaunchCompleted=$isFirstLaunchCompleted)"
            println("$TAG: $message")
            try {
                Timber.tag(TAG).d(message)
            } catch (e: Exception) {
                println("$TAG: Timber 로깅 실패 - ${e.message}")
            }
            return statusName
        } catch (e: Exception) {
            println("$TAG: checkCurrentStatus 실패 - ${e.message}")
            try {
                Timber.tag(TAG).e(e, "checkCurrentStatus 실패")
            } catch (ignored: Exception) {
                // Timber 초기화 문제일 경우 무시
            }
            throw e
        }
    }
} 