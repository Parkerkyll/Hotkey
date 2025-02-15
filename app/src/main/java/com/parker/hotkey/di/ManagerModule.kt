package com.parker.hotkey.di

import android.content.Context
import com.parker.hotkey.data.manager.LocationManagerImpl
import com.parker.hotkey.domain.repository.LocationManager
import com.parker.hotkey.data.manager.TokenManagerImpl
import com.parker.hotkey.domain.manager.TokenManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ManagerModule {
    @Binds
    @Singleton
    abstract fun bindLocationManager(
        locationManagerImpl: LocationManagerImpl
    ): LocationManager

    companion object {
        @Provides
        @Singleton
        fun provideTokenManager(
            @ApplicationContext context: Context
        ): TokenManager {
            return TokenManagerImpl(context)
        }
    }
} 