package com.parker.hotkey.di

import androidx.work.WorkManager
import com.parker.hotkey.domain.repository.MarkerRepository
import com.parker.hotkey.domain.repository.MemoRepository
import com.parker.hotkey.domain.usecase.marker.DeleteMarkerWithValidationUseCase
import com.parker.hotkey.domain.manager.MarkerDeletionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides
    @Singleton
    fun provideDeleteMarkerWithValidationUseCase(
        markerRepository: MarkerRepository,
        memoRepository: MemoRepository
    ): DeleteMarkerWithValidationUseCase {
        return DeleteMarkerWithValidationUseCase(
            markerRepository = markerRepository,
            memoRepository = memoRepository
        )
    }

    @Provides
    @Singleton
    fun provideMarkerDeletionManager(
        workManager: WorkManager
    ): MarkerDeletionManager {
        return MarkerDeletionManager(workManager)
    }
} 