package com.parker.hotkey.di

import com.parker.hotkey.data.repository.AuthRepositoryImpl
import com.parker.hotkey.data.repository.MarkerRepositoryImpl
import com.parker.hotkey.data.repository.MemoRepositoryImpl
import com.parker.hotkey.domain.repository.AuthRepository
import com.parker.hotkey.domain.repository.MarkerRepository
import com.parker.hotkey.domain.repository.MemoRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindMarkerRepository(
        markerRepositoryImpl: MarkerRepositoryImpl
    ): MarkerRepository

    @Binds
    @Singleton
    abstract fun bindMemoRepository(
        memoRepositoryImpl: MemoRepositoryImpl
    ): MemoRepository
} 