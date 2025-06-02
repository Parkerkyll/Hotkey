package com.parker.hotkey.di

import com.parker.hotkey.di.qualifier.UseTemporaryMarkerFeature
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 공통적으로 사용되는 Map 관련 기능 플래그를 제공하는 모듈
 */
@Module
@InstallIn(SingletonComponent::class)
object MapModuleBindings {

    /**
     * 임시 마커 기능 사용 여부 플래그 제공
     * 한정자를 사용하여 제공되는 플래그
     */
    @Provides
    @Singleton
    @UseTemporaryMarkerFeature
    fun provideUseTemporaryMarkerFeature(): Boolean {
        // 임시 마커 기능 활성화
        return true
    }
} 