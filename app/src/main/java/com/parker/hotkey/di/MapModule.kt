package com.parker.hotkey.di

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.naver.maps.map.NaverMap
import com.naver.maps.map.util.FusedLocationSource
import com.parker.hotkey.domain.repository.LocationManager
import com.parker.hotkey.domain.repository.MarkerRepository
import com.parker.hotkey.domain.repository.MemoRepository
import com.parker.hotkey.domain.usecase.marker.DeleteMarkerWithValidationUseCase
import com.parker.hotkey.domain.manager.MarkerDeletionManager
import com.parker.hotkey.presentation.map.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.FragmentComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.FragmentScoped
import kotlinx.coroutines.CoroutineScope
import javax.inject.Named

@Module
@InstallIn(FragmentComponent::class)
object MapModule {
    @Provides
    @FragmentScoped
    @MapLocationSource
    fun provideFusedLocationSource(
        fragment: Fragment
    ): FusedLocationSource {
        return FusedLocationSource(fragment, 1000)
    }

    @Provides
    @FragmentScoped
    @FragmentCoroutineScope
    fun provideFragmentCoroutineScope(
        fragment: Fragment
    ): CoroutineScope {
        return fragment.lifecycleScope
    }

    @Provides
    @FragmentScoped
    fun provideLocationPermissionDelegate(
        fragment: Fragment,
        @FragmentCoroutineScope coroutineScope: CoroutineScope,
        @MapLocationSource locationSource: FusedLocationSource
    ): LocationPermissionDelegate {
        return LocationPermissionDelegate(
            fragment = fragment,
            coroutineScope = coroutineScope,
            locationSource = locationSource
        )
    }

    @Provides
    @FragmentScoped
    @DefaultNaverMap
    fun provideDefaultNaverMap(): NaverMap? {
        return null  // 실제 NaverMap은 MapFragment에서 초기화됩니다
    }

    @Provides
    @FragmentScoped
    fun provideMarkerUIDelegate(): MarkerUIDelegate {
        return MarkerUIDelegate()
    }

    @Provides
    @FragmentScoped
    fun provideMapConfigDelegate(
        @DefaultNaverMap naverMap: NaverMap?,
        @ApplicationContext context: Context,
        @MapLocationSource locationSource: FusedLocationSource
    ): MapConfigDelegate {
        return MapConfigDelegate(naverMap, context, locationSource)
    }

    @Provides
    @FragmentScoped
    fun provideLocationViewModel(): LocationViewModel {
        return LocationViewModel()
    }

    @Provides
    @FragmentScoped
    fun provideMarkerViewModel(
        markerRepository: MarkerRepository,
        workManager: WorkManager
    ): MarkerViewModel {
        return MarkerViewModel(markerRepository, workManager)
    }

    @Provides
    @FragmentScoped
    fun provideMapViewModel(
        markerRepository: MarkerRepository,
        memoRepository: MemoRepository,
        deleteMarkerWithValidationUseCase: DeleteMarkerWithValidationUseCase,
        markerDeletionManager: MarkerDeletionManager,
        @ApplicationContext context: Context,
        locationManager: LocationManager
    ): MapViewModel {
        return MapViewModel(
            markerRepository = markerRepository,
            memoRepository = memoRepository,
            deleteMarkerWithValidationUseCase = deleteMarkerWithValidationUseCase,
            markerDeletionManager = markerDeletionManager,
            context = context,
            locationManager = locationManager
        )
    }

    @Provides
    @FragmentScoped
    fun provideMemoViewModel(
        memoRepository: MemoRepository,
        markerDeletionManager: MarkerDeletionManager
    ): MemoViewModel {
        return MemoViewModel(
            memoRepository = memoRepository,
            markerDeletionManager = markerDeletionManager
        )
    }
} 