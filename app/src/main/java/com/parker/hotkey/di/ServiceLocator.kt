package com.parker.hotkey.di

import android.content.Context
import com.parker.hotkey.data.remote.util.ApiRequestManager
import dagger.hilt.android.EntryPointAccessors
import timber.log.Timber

/**
 * Hilt 외부에서 의존성을 가져올 수 있게 하는 ServiceLocator 패턴 구현
 * Application context 접근이 가능한 곳에서만 사용해야 합니다.
 */
object ServiceLocator {
    private var applicationContext: Context? = null
    
    /**
     * ServiceLocator 초기화 (애플리케이션 시작 시 호출)
     */
    fun init(context: Context) {
        applicationContext = context.applicationContext
        Timber.d("ServiceLocator가 초기화되었습니다.")
    }
    
    /**
     * API 요청 관리자를 가져옵니다.
     * @return ApiRequestManager 인스턴스
     * @throws IllegalStateException ServiceLocator가 초기화되지 않은 경우
     */
    fun getApiRequestManager(): ApiRequestManager {
        val appContext = applicationContext
            ?: throw IllegalStateException("ServiceLocator가 초기화되지 않았습니다. init() 메서드를 먼저 호출하세요.")
        
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            ServiceLocatorEntryPoint::class.java
        )
        
        return entryPoint.apiRequestManager()
    }
} 