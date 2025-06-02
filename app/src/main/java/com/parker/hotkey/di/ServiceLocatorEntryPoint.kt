package com.parker.hotkey.di

import com.parker.hotkey.data.remote.util.ApiRequestManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt 외부에서 의존성을 가져오기 위한 EntryPoint 인터페이스
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ServiceLocatorEntryPoint {
    /**
     * ApiRequestManager 의존성을 제공합니다.
     */
    fun apiRequestManager(): ApiRequestManager
} 