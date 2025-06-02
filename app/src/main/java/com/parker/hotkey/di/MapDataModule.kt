package com.parker.hotkey.di

import android.content.Context
import com.parker.hotkey.data.remote.network.ConnectionStateMonitor
import com.parker.hotkey.di.qualifier.UseTemporaryMarkerFeature
import com.parker.hotkey.domain.repository.AuthRepository
import com.parker.hotkey.domain.repository.MarkerRepository
import com.parker.hotkey.domain.repository.MemoRepository
import com.parker.hotkey.domain.repository.SyncRepository
import com.parker.hotkey.domain.usecase.SyncDataUseCase
import com.parker.hotkey.domain.usecase.marker.CreateMarkerUseCase
import com.parker.hotkey.domain.usecase.marker.DeleteMarkerWithValidationUseCase
import com.parker.hotkey.domain.util.AppStateManager
import com.parker.hotkey.presentation.map.markers.MarkerViewModel
import com.parker.hotkey.util.SharedPrefsManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.FragmentComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.FragmentScoped
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 지도 데이터 관련 의존성을 제공하는 모듈
 * - 지도 관련 데이터 접근, 저장소, 유스케이스 등
 */
@Module
@InstallIn(SingletonComponent::class)
object MapDataModule {

    /**
     * MapViewModel에 필요한 저장소 의존성 그룹 제공
     */
    @Provides
    @Singleton
    fun provideMapRepositoryDependencies(
        markerRepository: MarkerRepository,
        memoRepository: MemoRepository,
        authRepository: AuthRepository,
        syncRepository: SyncRepository
    ): MapRepositoryDependencies {
        return MapRepositoryDependencies(
            markerRepository,
            memoRepository,
            authRepository,
            syncRepository
        )
    }

    /**
     * MapViewModel에 필요한 유스케이스 의존성 그룹 제공
     */
    @Provides
    @Singleton
    fun provideMapUseCaseDependencies(
        createMarkerUseCase: CreateMarkerUseCase,
        deleteMarkerWithValidationUseCase: DeleteMarkerWithValidationUseCase,
        syncDataUseCase: SyncDataUseCase
    ): MapUseCaseDependencies {
        return MapUseCaseDependencies(
            createMarkerUseCase,
            deleteMarkerWithValidationUseCase,
            syncDataUseCase
        )
    }

    /**
     * MapViewModel에 필요한 유틸리티 의존성 그룹 제공
     */
    @Provides
    @Singleton
    fun provideMapUtilityDependencies(
        @ApplicationContext context: Context,
        connectionStateMonitor: ConnectionStateMonitor,
        sharedPrefsManager: SharedPrefsManager,
        appStateManager: AppStateManager
    ): MapUtilityDependencies {
        return MapUtilityDependencies(
            context,
            connectionStateMonitor,
            sharedPrefsManager,
            appStateManager
        )
    }

    /**
     * MapViewModel에 필요한 기능 플래그 의존성 그룹 제공
     */
    @Provides
    @Singleton
    fun provideMapFeatureFlagDependencies(
        @UseTemporaryMarkerFeature useTemporaryMarkerFeature: Boolean
    ): MapFeatureFlagDependencies {
        return MapFeatureFlagDependencies(
            useTemporaryMarkerFeature
        )
    }
}

/**
 * Fragment 범위의 지도 데이터 의존성 제공
 */
@Module
@InstallIn(FragmentComponent::class)
object MapDataFragmentModule {
    
    /**
     * MapViewModel 의존성 제공
     */
    @Provides
    @FragmentScoped
    fun providesMapViewModel(
        repositoryDependencies: MapRepositoryDependencies,
        useCaseDependencies: MapUseCaseDependencies,
        managerDependencies: MapManagerDependencies,
        processorDependencies: MapProcessorDependencies,
        utilityDependencies: MapUtilityDependencies,
        featureFlagDependencies: MapFeatureFlagDependencies,
        markerViewModel: MarkerViewModel,
        markerLoadingCoordinator: com.parker.hotkey.domain.map.MarkerLoadingCoordinator
    ): com.parker.hotkey.presentation.map.MapViewModel {
        return com.parker.hotkey.presentation.map.MapViewModel(
            repositoryDependencies,
            useCaseDependencies,
            managerDependencies,
            processorDependencies,
            utilityDependencies,
            featureFlagDependencies,
            markerViewModel,
            markerLoadingCoordinator,
            utilityDependencies.appStateManager
        )
    }
} 