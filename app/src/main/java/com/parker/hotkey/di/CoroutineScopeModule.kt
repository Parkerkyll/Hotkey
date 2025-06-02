package com.parker.hotkey.di

import com.parker.hotkey.di.qualifier.ApplicationScope
import com.parker.hotkey.di.qualifier.DefaultDispatcher
import com.parker.hotkey.di.qualifier.IoDispatcher
import com.parker.hotkey.di.qualifier.MainDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * 코루틴 스코프와 디스패처를 제공하는 모듈
 */
@Module
@InstallIn(SingletonComponent::class)
object CoroutineScopeModule {
    
    /**
     * 애플리케이션 수명 주기 동안 유지되는 코루틴 스코프를 제공합니다.
     * SupervisorJob을 사용하여 자식 코루틴의 실패가 다른 코루틴에 영향을 미치지 않도록 합니다.
     */
    @Singleton
    @ApplicationScope
    @Provides
    fun provideApplicationCoroutineScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    /**
     * IO 디스패처를 제공합니다.
     * 파일 읽기/쓰기, 네트워크 요청 등 I/O 작업에 최적화된 디스패처입니다.
     */
    @Singleton
    @IoDispatcher
    @Provides
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    
    /**
     * 기본 디스패처를 제공합니다.
     * CPU 집약적 작업을 위한 디스패처입니다.
     */
    @Singleton
    @DefaultDispatcher
    @Provides
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
    
    /**
     * 메인 디스패처를 제공합니다.
     * UI 작업을 위한 디스패처입니다.
     */
    @Singleton
    @MainDispatcher
    @Provides
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
} 