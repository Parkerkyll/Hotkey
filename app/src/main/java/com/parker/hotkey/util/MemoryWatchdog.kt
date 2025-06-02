package com.parker.hotkey.util

import android.app.ActivityManager
import android.content.Context
import android.app.Activity
import androidx.fragment.app.Fragment
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 메모리 상태 모니터링 및 관리
 * API 26+ 기기에서 메모리 사용량을 모니터링하고 최적화 작업을 수행합니다.
 */
@Singleton
class MemoryWatchdog @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // 메모리 상태 정의
    enum class MemoryState { GOOD, MODERATE, WARNING, CRITICAL }
    
    // 메모리 최적화 작업 정의
    enum class OptimizationAction { NONE, REDUCE_CACHE, AGGRESSIVE_CLEANUP }
    
    // 메모리 체크 주기 (밀리초)
    private val MEMORY_CHECK_INTERVAL = 30_000L // 30초
    
    // 메모리 상태 임계값 (%)
    private val WARNING_THRESHOLD = 0.75f
    private val CRITICAL_THRESHOLD = 0.85f
    
    // Activity와 Fragment에 대한 약한 참조
    private var weakActivity: WeakReference<Activity>? = null
    private var weakFragment: WeakReference<Fragment>? = null
    
    // 콜백을 약한 참조로 저장
    private var lowMemoryCallback: WeakReference<((Boolean) -> Unit)>? = null
    
    /**
     * Activity 참조 설정
     * @param activity 설정할 Activity 인스턴스
     */
    fun setActivity(activity: Activity) {
        this.weakActivity = WeakReference(activity)
    }
    
    /**
     * Fragment 참조 설정
     * @param fragment 설정할 Fragment 인스턴스
     */
    fun setFragment(fragment: Fragment) {
        this.weakFragment = WeakReference(fragment)
    }
    
    /**
     * 메모리 부족 콜백 설정
     * @param callback 메모리 부족 시 호출될 콜백
     */
    fun setLowMemoryCallback(callback: (Boolean) -> Unit) {
        this.lowMemoryCallback = WeakReference(callback)
    }
    
    /**
     * 현재 메모리 상태 확인
     * @return 현재 메모리 상태
     */
    fun checkMemoryState(): MemoryState {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        val percentUsed = 1 - (memInfo.availMem.toFloat() / memInfo.totalMem)
        
        return when {
            percentUsed > CRITICAL_THRESHOLD -> MemoryState.CRITICAL
            percentUsed > WARNING_THRESHOLD -> MemoryState.WARNING
            percentUsed > 0.65f -> MemoryState.MODERATE
            else -> MemoryState.GOOD
        }
    }
    
    /**
     * 메모리 상태에 따른 최적화 작업 결정
     * @return 수행할 최적화 작업
     */
    fun getOptimizationAction(): OptimizationAction {
        return when (checkMemoryState()) {
            MemoryState.CRITICAL -> OptimizationAction.AGGRESSIVE_CLEANUP
            MemoryState.WARNING -> OptimizationAction.REDUCE_CACHE
            else -> OptimizationAction.NONE
        }
    }
    
    /**
     * 앱 시작 및 포그라운드 전환 시 메모리 상태 체크
     * 메모리 상태가 좋지 않으면 콜백 실행
     * @param onLowMemory 메모리 부족 시 실행할 콜백
     */
    fun checkOnStartAndForeground(onLowMemory: (Boolean) -> Unit) {
        val state = checkMemoryState()
        Timber.d("메모리 상태 확인: $state")
        
        when (state) {
            MemoryState.CRITICAL -> {
                Timber.w("메모리 상태 심각: 공격적 정리 필요")
                onLowMemory(true) // true = 공격적 정리
            }
            MemoryState.WARNING -> {
                Timber.w("메모리 상태 경고: 일반 정리 필요")
                onLowMemory(false) // false = 일반 정리
            }
            else -> {
                Timber.d("메모리 상태 양호")
            }
        }
    }
    
    /**
     * 저장된 콜백을 사용하여 메모리 상태 체크
     */
    fun checkWithStoredCallback() {
        val callback = lowMemoryCallback?.get()
        if (callback != null) {
            checkOnStartAndForeground(callback)
        } else {
            Timber.d("저장된 콜백이 없어 메모리 상태 체크만 수행")
            checkMemoryState()
        }
    }
    
    /**
     * 현재 메모리 사용량 로깅
     */
    fun logMemoryInfo() {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        val availableMB = memInfo.availMem / (1024 * 1024)
        val totalMB = memInfo.totalMem / (1024 * 1024)
        val usedPercent = 100 - (availableMB * 100 / totalMB)
        
        Timber.d("===== 메모리 정보 =====")
        Timber.d("전체 메모리: ${totalMB}MB")
        Timber.d("사용 가능 메모리: ${availableMB}MB")
        Timber.d("사용 비율: ${usedPercent}%")
        Timber.d("저메모리 상태: ${memInfo.lowMemory}")
        Timber.d("======================")
    }
    
    /**
     * 리소스 정리
     */
    fun cleanup() {
        Timber.d("MemoryWatchdog 리소스 정리 중")
        weakActivity = null
        weakFragment = null
        lowMemoryCallback = null
    }
} 