package com.parker.hotkey.di

import com.parker.hotkey.data.remote.util.ApiPerformanceMonitor
import com.parker.hotkey.data.remote.util.ApiRequestManager
import com.parker.hotkey.data.remote.util.ApiRequestManagerImpl
import com.parker.hotkey.di.qualifier.ApplicationScope
import com.parker.hotkey.domain.util.AppStateManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/**
 * API 요청 관리 관련 의존성을 제공하는 모듈
 */
@Module
@InstallIn(SingletonComponent::class)
object ApiModule {
    
    /**
     * ApiRequestManager 구현체를 제공합니다.
     * 우선순위 기반 API 요청 관리 시스템을 제공합니다.
     * AppStateManager를 주입하여 네비게이션 복귀 상태에서 API 호출을 방지합니다.
     */
    @Provides
    @Singleton
    fun provideApiRequestManager(
        @ApplicationScope coroutineScope: CoroutineScope,
        appStateManager: AppStateManager,
        performanceMonitor: ApiPerformanceMonitor
    ): ApiRequestManager {
        return ApiRequestManagerImpl(coroutineScope, appStateManager, performanceMonitor)
    }
    
    /**
     * API 성능 모니터링 유틸리티를 제공합니다.
     * API 호출 횟수와 응답 시간을 추적하고 분석합니다.
     */
    @Provides
    @Singleton
    fun provideApiPerformanceMonitor(): ApiPerformanceMonitor {
        return ApiPerformanceMonitor()
    }
} 