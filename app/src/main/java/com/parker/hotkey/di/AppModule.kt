package com.parker.hotkey.di

import android.content.Context
import com.parker.hotkey.data.manager.LoadingManager
import com.parker.hotkey.data.manager.UserPreferencesManager
import com.parker.hotkey.util.SharedPrefsManager
import com.parker.hotkey.util.MemoryWatchdog
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 애플리케이션 수준에서 필요한 의존성을 제공하는 모듈
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    /**
     * SharedPrefsManager 의존성 제공
     * 애플리케이션 전체에서 단일 인스턴스를 사용합니다.
     */
    @Provides
    @Singleton
    fun provideSharedPrefsManager(@ApplicationContext context: Context): SharedPrefsManager {
        return SharedPrefsManager(context)
    }
    
    /**
     * UserPreferencesManager 의존성 제공
     * 사용자 설정 및 정보를 관리하는 매니저입니다.
     */
    @Provides
    @Singleton
    fun provideUserPreferencesManager(@ApplicationContext context: Context): UserPreferencesManager {
        return UserPreferencesManager(context)
    }
    
    /**
     * LoadingManager 의존성 제공
     * 앱 전체에서 로딩 화면을 관리하는 매니저입니다.
     */
    @Provides
    @Singleton
    fun provideLoadingManager(@ApplicationContext context: Context): LoadingManager {
        return LoadingManager(context)
    }

    /**
     * 메모리 상태 모니터링 및 관리
     */
    @Provides
    @Singleton
    fun provideMemoryWatchdog(@ApplicationContext context: Context): MemoryWatchdog {
        return MemoryWatchdog(context)
    }
} 