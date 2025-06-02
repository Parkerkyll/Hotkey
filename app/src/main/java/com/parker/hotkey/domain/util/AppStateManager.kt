package com.parker.hotkey.domain.util

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 앱 상태를 관리하는 클래스
 * 앱의 생명주기 이벤트를 모니터링하고 현재 앱 상태를 추적합니다.
 * 이를 통해 각 상황에 맞는 데이터 로드 전략을 적용할 수 있습니다.
 */
@Singleton
class AppStateManager @Inject constructor(
    @ApplicationContext private val context: Context
) : Application.ActivityLifecycleCallbacks {

    companion object {
        private const val PREF_NAME = "app_state_prefs"
        private const val KEY_FIRST_LAUNCH_COMPLETED = "is_first_launch_completed"
        private const val KEY_LAST_NAVIGATION_FRAGMENT = "last_navigation_fragment"
        private const val KEY_LAST_UPDATE_VERSION = "last_update_version"
        private const val TAG = "AppStateManager"
        
        // 백그라운드 시간에 따른 데이터 새로고침 전략 임계값
        private const val SHORT_BACKGROUND_THRESHOLD_MS = 30 * 1000L // 30초
        private const val LONG_BACKGROUND_THRESHOLD_MS = 5 * 60 * 1000L // 5분
        
        // 네비게이션 복귀 상태 자동 리셋 시간
        const val NAVIGATION_RETURN_RESET_DELAY_MS = 5000L // 5초 (이전: 2초)
    }
    
    // 코루틴 스코프 정의
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    // 포그라운드 전환 이벤트를 위한 SharedFlow
    private val _foregroundTransitionEvent = MutableSharedFlow<ForegroundTransitionEvent>()
    val foregroundTransitionEvent: SharedFlow<ForegroundTransitionEvent> = _foregroundTransitionEvent.asSharedFlow()
    
    // 포그라운드 전환 이벤트를 표현하는 데이터 클래스
    data class ForegroundTransitionEvent(
        val backgroundDuration: Long,
        val needsRefresh: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private val _currentAppStatus = MutableStateFlow<AppStatus>(
        if (isFirstLaunchCompleted()) AppStatus.NORMAL_LAUNCH else AppStatus.FRESH_INSTALL
    )
    
    /**
     * 앱의 현재 상태를 외부에 제공하는 StateFlow
     */
    val currentAppStatus: StateFlow<AppStatus> = _currentAppStatus.asStateFlow()

    private var activityCount = 0
    private var wasInBackground = false
    private var backgroundStartTime = 0L
    
    // 마지막 백그라운드 시간을 저장 (외부에서 조회 가능)
    private val _lastBackgroundDuration = MutableStateFlow(0L)
    val lastBackgroundDuration: StateFlow<Long> = _lastBackgroundDuration.asStateFlow()
    
    // 네비게이션 복귀 상태 리셋 러너블
    private val navigationReturnResetRunnable = Runnable { 
        if (_currentAppStatus.value == AppStatus.NAVIGATION_RETURN) {
            updateAppStatus(AppStatus.NORMAL_LAUNCH)
            Timber.tag(TAG).d("네비게이션 복귀 상태 자동 리셋됨 (${NAVIGATION_RETURN_RESET_DELAY_MS}ms 경과)")
        }
    }
    
    // 메인 핸들러 (UI 스레드)
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        Timber.tag(TAG).d("초기화: 앱 상태 = ${currentAppStatus.value}")
    }

    /**
     * 앱 최초 실행 완료 여부를 SharedPreferences에서 확인
     */
    private fun isFirstLaunchCompleted(): Boolean {
        return preferences.getBoolean(KEY_FIRST_LAUNCH_COMPLETED, false)
    }

    /**
     * 앱 최초 실행 완료를 SharedPreferences에 저장
     */
    fun completeFirstLaunch() {
        preferences.edit()
            .putBoolean(KEY_FIRST_LAUNCH_COMPLETED, true)
            .apply()
        updateAppStatus(AppStatus.NORMAL_LAUNCH)
        Timber.tag(TAG).d("앱 최초 실행 완료 처리됨")
    }
    
    /**
     * 현재 앱 버전을 저장합니다 (앱 업데이트 감지를 위해 사용)
     */
    fun saveCurrentAppVersion(versionCode: Int) {
        preferences.edit()
            .putInt(KEY_LAST_UPDATE_VERSION, versionCode)
            .apply()
        Timber.tag(TAG).d("현재 앱 버전 저장: $versionCode")
    }
    
    /**
     * 앱 업데이트 여부를 확인합니다
     * @param currentVersionCode 현재 앱 버전
     * @return 앱이 업데이트되었으면 true, 아니면 false
     */
    fun checkIfAppUpdated(currentVersionCode: Int): Boolean {
        val lastVersion = preferences.getInt(KEY_LAST_UPDATE_VERSION, -1)
        val isUpdated = lastVersion != -1 && lastVersion < currentVersionCode
        
        if (isUpdated) {
            Timber.tag(TAG).d("앱 업데이트 감지: $lastVersion -> $currentVersionCode")
            updateAppStatus(AppStatus.AFTER_UPDATE)
            saveCurrentAppVersion(currentVersionCode)
        }
        
        return isUpdated
    }
    
    /**
     * 마지막 네비게이션 프래그먼트 ID를 저장합니다
     * @param fragmentId 프래그먼트 리소스 ID
     */
    fun saveLastNavigationFragment(fragmentId: Int) {
        preferences.edit()
            .putInt(KEY_LAST_NAVIGATION_FRAGMENT, fragmentId)
            .apply()
    }
    
    /**
     * 맵 프래그먼트로 네비게이션 복귀 시 상태를 업데이트합니다
     * @param currentFragmentId 현재 프래그먼트 리소스 ID
     * @param mapFragmentId 맵 프래그먼트 리소스 ID
     */
    fun checkNavigationReturn(currentFragmentId: Int, mapFragmentId: Int) {
        val lastFragmentId = preferences.getInt(KEY_LAST_NAVIGATION_FRAGMENT, -1)
        
        if (currentFragmentId == mapFragmentId && lastFragmentId != -1 && lastFragmentId != mapFragmentId) {
            // 이전 리셋 타이머가 있으면 취소
            mainHandler.removeCallbacks(navigationReturnResetRunnable)
            
            // 네비게이션 복귀 상태로 업데이트
            updateAppStatus(AppStatus.NAVIGATION_RETURN)
            Timber.tag(TAG).d("네비게이션 복귀 감지: $lastFragmentId -> $mapFragmentId")
            
            // 자동 리셋 타이머 시작
            mainHandler.postDelayed(navigationReturnResetRunnable, NAVIGATION_RETURN_RESET_DELAY_MS)
            Timber.tag(TAG).d("네비게이션 복귀 상태 자동 리셋 타이머 시작: ${NAVIGATION_RETURN_RESET_DELAY_MS}ms")
        }
        
        // 현재 프래그먼트 ID 저장
        saveLastNavigationFragment(currentFragmentId)
    }
    
    /**
     * 네비게이션 복귀 상태를 수동으로 리셋합니다.
     * UI 이벤트 등으로 상태를 즉시 초기화해야 할 때 호출합니다.
     */
    fun resetNavigationReturnStatus() {
        if (_currentAppStatus.value == AppStatus.NAVIGATION_RETURN) {
            mainHandler.removeCallbacks(navigationReturnResetRunnable)
            updateAppStatus(AppStatus.NORMAL_LAUNCH)
            Timber.tag(TAG).d("네비게이션 복귀 상태 수동 리셋됨")
        }
    }

    /**
     * 앱 상태를 업데이트하고 로그 기록
     */
    private fun updateAppStatus(newStatus: AppStatus) {
        if (_currentAppStatus.value != newStatus) {
            val previousStatus = _currentAppStatus.value
            _currentAppStatus.value = newStatus
            Timber.tag(TAG).d("앱 상태 변경: $previousStatus -> $newStatus")
        }
    }

    // ActivityLifecycleCallbacks 구현

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        Timber.tag(TAG).v("Activity 생성: ${activity.javaClass.simpleName}")
    }

    override fun onActivityStarted(activity: Activity) {
        if (activityCount == 0 && wasInBackground) {
            // 백그라운드 시간 계산
            val currentTime = SystemClock.elapsedRealtime()
            val backgroundDuration = currentTime - backgroundStartTime
            _lastBackgroundDuration.value = backgroundDuration
            
            // 앱이 백그라운드에서 포그라운드로 복귀
            updateAppStatus(AppStatus.FOREGROUND_RESUME)
            wasInBackground = false
            
            // 새로고침 필요 여부 결정
            val needsRefresh = backgroundDuration >= SHORT_BACKGROUND_THRESHOLD_MS
            
            // 포그라운드 전환 이벤트 발행
            viewModelScope.launch {
                _foregroundTransitionEvent.emit(
                    ForegroundTransitionEvent(
                        backgroundDuration = backgroundDuration,
                        needsRefresh = needsRefresh
                    )
                )
                Timber.tag(TAG).d("포그라운드 전환 이벤트 발행: 백그라운드 시간=${backgroundDuration}ms, 새로고침 필요=$needsRefresh")
            }
            
            // 백그라운드 시간에 따른 로그
            when {
                backgroundDuration < SHORT_BACKGROUND_THRESHOLD_MS -> {
                    Timber.tag(TAG).d("짧은 백그라운드 후 복귀: ${backgroundDuration}ms (데이터 새로고침 불필요)")
                }
                backgroundDuration < LONG_BACKGROUND_THRESHOLD_MS -> {
                    Timber.tag(TAG).d("중간 백그라운드 후 복귀: ${backgroundDuration}ms (경량 새로고침 필요)")
                }
                else -> {
                    Timber.tag(TAG).d("긴 백그라운드 후 복귀: ${backgroundDuration}ms (전체 새로고침 필요)")
                }
            }
        }
        activityCount++
        Timber.tag(TAG).v("Activity 시작: ${activity.javaClass.simpleName}, 현재 활성 Activity 수: $activityCount")
    }

    override fun onActivityResumed(activity: Activity) {
        Timber.tag(TAG).v("Activity 재개: ${activity.javaClass.simpleName}")
    }

    override fun onActivityPaused(activity: Activity) {
        Timber.tag(TAG).v("Activity 일시정지: ${activity.javaClass.simpleName}")
    }

    override fun onActivityStopped(activity: Activity) {
        activityCount--
        Timber.tag(TAG).v("Activity 중지: ${activity.javaClass.simpleName}, 현재 활성 Activity 수: $activityCount")
        
        if (activityCount == 0) {
            // 앱이 백그라운드로 이동
            wasInBackground = true
            backgroundStartTime = SystemClock.elapsedRealtime()
            Timber.tag(TAG).d("앱이 백그라운드로 이동: ${activity.javaClass.simpleName} 중지됨")
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        Timber.tag(TAG).v("Activity 상태 저장: ${activity.javaClass.simpleName}")
    }

    override fun onActivityDestroyed(activity: Activity) {
        Timber.tag(TAG).v("Activity 소멸: ${activity.javaClass.simpleName}")
    }

    /**
     * 네비게이션 복귀 상태인지 확인합니다
     * @return 현재 앱 상태가 네비게이션 복귀인 경우 true, 아니면 false
     */
    fun isNavigationReturn(): Boolean {
        return _currentAppStatus.value == AppStatus.NAVIGATION_RETURN
    }
    
    /**
     * 네비게이션 복귀 상태를 강제로 설정합니다.
     * 네비게이션 메뉴에서 지도로 돌아갈 때 API 호출을 방지하기 위해 사용합니다.
     * @return 상태 설정 성공 여부
     */
    fun forceNavigationReturnState(): Boolean {
        try {
            // 이전 리셋 타이머가 있으면 취소
            mainHandler.removeCallbacks(navigationReturnResetRunnable)
            
            // 네비게이션 복귀 상태로 업데이트
            updateAppStatus(AppStatus.NAVIGATION_RETURN)
            Timber.tag(TAG).d("네비게이션 복귀 상태 강제 설정됨")
            
            // 자동 리셋 타이머 시작
            mainHandler.postDelayed(navigationReturnResetRunnable, NAVIGATION_RETURN_RESET_DELAY_MS)
            Timber.tag(TAG).d("네비게이션 복귀 상태 자동 리셋 타이머 시작: ${NAVIGATION_RETURN_RESET_DELAY_MS}ms")
            
            return true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "네비게이션 복귀 상태 강제 설정 중 오류 발생")
            return false
        }
    }
} 