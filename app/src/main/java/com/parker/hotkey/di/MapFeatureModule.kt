package com.parker.hotkey.di

import androidx.lifecycle.ViewModel
import com.parker.hotkey.di.qualifier.ApplicationScope
import com.parker.hotkey.domain.manager.EditModeManager
import com.parker.hotkey.domain.manager.LocationTracker
import com.parker.hotkey.domain.manager.MarkerManager
import com.parker.hotkey.domain.manager.MemoManager
import com.parker.hotkey.domain.manager.TemporaryMarkerManager
import com.parker.hotkey.domain.repository.AuthRepository
import com.parker.hotkey.domain.repository.LocationManager
import com.parker.hotkey.domain.repository.MarkerRepository
import com.parker.hotkey.domain.repository.SyncRepository
import com.parker.hotkey.domain.usecase.marker.CreateMarkerUseCase
import com.parker.hotkey.domain.usecase.marker.DeleteMarkerWithValidationUseCase
import com.parker.hotkey.domain.usecase.memo.CreateMemoUseCase
import com.parker.hotkey.domain.usecase.memo.DeleteMemoUseCase
import com.parker.hotkey.domain.usecase.memo.GetMemosByMarkerIdUseCase
import com.parker.hotkey.presentation.map.event.MapEventHandler
import com.parker.hotkey.presentation.map.event.MapEventHandlerImpl
import com.parker.hotkey.presentation.map.markers.MarkerEventHandler
import com.parker.hotkey.presentation.map.markers.MarkerEventHandlerImpl
import com.parker.hotkey.presentation.map.markers.MarkerInteractor
import com.parker.hotkey.presentation.map.markers.MarkerInteractorImpl
import com.parker.hotkey.presentation.map.markers.MarkerStateProcessor
import com.parker.hotkey.presentation.map.markers.MarkerStateProcessorImpl
import com.parker.hotkey.presentation.map.markers.MarkerViewModel
import com.parker.hotkey.presentation.map.processor.LocationStateHolder
import com.parker.hotkey.presentation.map.processor.LocationTrackingManager
import com.parker.hotkey.presentation.map.processor.MapStateProcessor
import com.parker.hotkey.presentation.memo.MemoInteractor
import com.parker.hotkey.domain.map.MarkerLoadingCoordinator
import com.parker.hotkey.domain.map.MarkerLoadingCoordinatorImpl
import com.parker.hotkey.presentation.map.markers.MarkerUIDelegate
import com.parker.hotkey.domain.util.AppStateManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton
import dagger.Lazy

/**
 * 지도 관련 기능(프로세서, 이벤트 핸들러, 상태 관리) 의존성을 제공하는 모듈
 */
@Module
@InstallIn(SingletonComponent::class)
object MapFeatureModule {

    /**
     * MapStateProcessor 의존성 제공
     * 지도 상태 관리 프로세서
     */
    @Provides
    @Singleton
    fun provideMapStateProcessor(): MapStateProcessor {
        return MapStateProcessor()
    }
    
    /**
     * LocationStateHolder 의존성 제공
     * 위치 상태 저장소
     */
    @Provides
    @Singleton
    fun provideLocationStateHolder(): LocationStateHolder {
        return LocationStateHolder()
    }
    
    /**
     * LocationTrackingManager 의존성 제공
     * 위치 추적 관리자
     */
    @Provides
    @Singleton
    fun provideLocationTrackingManager(
        locationTracker: LocationTracker,
        locationManager: LocationManager,
        locationStateHolder: LocationStateHolder,
        @ApplicationScope coroutineScope: CoroutineScope
    ): LocationTrackingManager {
        return LocationTrackingManager(
            locationTracker,
            locationManager,
            locationStateHolder,
            coroutineScope
        )
    }
    
    /**
     * MemoInteractor 의존성 제공
     * 메모 관련 상호작용 로직
     */
    @Provides
    @Singleton
    fun provideMemoInteractor(
        createMemoUseCase: CreateMemoUseCase,
        deleteMemoUseCase: DeleteMemoUseCase,
        getMemosByMarkerIdUseCase: GetMemosByMarkerIdUseCase,
        editModeManager: EditModeManager,
        memoManager: MemoManager,
        authRepository: AuthRepository
    ): MemoInteractor {
        return MemoInteractor(
            createMemoUseCase,
            deleteMemoUseCase,
            getMemosByMarkerIdUseCase,
            editModeManager,
            memoManager,
            authRepository
        )
    }
    
    /**
     * MapEventHandler 의존성 제공
     */
    @Provides
    @Singleton
    fun provideMapEventHandler(
        markerManager: Lazy<MarkerManager>,
        memoManager: Lazy<MemoManager>,
        editModeManager: Lazy<EditModeManager>,
        @ApplicationScope scope: CoroutineScope,
        temporaryMarkerManager: Lazy<TemporaryMarkerManager>,
        authRepository: Lazy<AuthRepository>
    ): MapEventHandler {
        return MapEventHandlerImpl(
            markerManager, 
            memoManager, 
            editModeManager, 
            scope, 
            temporaryMarkerManager, 
            authRepository
        )
    }
    
    /**
     * MarkerEventHandler 의존성 제공
     */
    @Provides
    @Singleton
    fun provideMarkerEventHandler(
        markerManager: Lazy<MarkerManager>,
        memoManager: Lazy<MemoManager>
    ): MarkerEventHandler {
        return MarkerEventHandlerImpl(markerManager, memoManager)
    }
    
    /**
     * MarkerInteractor 의존성 제공
     */
    @Provides
    @Singleton
    fun provideMarkerInteractor(
        impl: MarkerInteractorImpl
    ): MarkerInteractor {
        return impl
    }
    
    /**
     * MarkerStateProcessor 의존성 제공
     */
    @Provides
    @Singleton
    fun provideMarkerStateProcessor(
        impl: MarkerStateProcessorImpl
    ): MarkerStateProcessor {
        return impl
    }
    
    /**
     * MapEventHandlerImpl에서 사용할 코루틴 스코프를 제공합니다.
     */
    @Provides
    @Singleton
    fun provideEventHandlerCoroutineScope(
        @ApplicationScope applicationScope: CoroutineScope
    ): CoroutineScope {
        return applicationScope
    }
    
    /**
     * MarkerViewModel 의존성 제공
     * 이제 HiltViewModel이 아닌 일반 ViewModel로 제공
     */
    @Provides
    @Singleton
    fun provideMarkerViewModel(
        markerRepository: MarkerRepository,
        syncRepository: SyncRepository,
        markerManager: MarkerManager,
        markerInteractor: MarkerInteractor,
        markerEventHandler: MarkerEventHandler,
        markerStateProcessor: MarkerStateProcessor,
        createMarkerUseCase: CreateMarkerUseCase,
        deleteMarkerWithValidationUseCase: DeleteMarkerWithValidationUseCase
    ): MarkerViewModel {
        return MarkerViewModel(
            markerRepository,
            syncRepository,
            markerManager,
            markerInteractor,
            markerEventHandler,
            markerStateProcessor,
            createMarkerUseCase,
            deleteMarkerWithValidationUseCase
        )
    }

    /**
     * MarkerLoadingCoordinator 의존성 제공
     * 마커 로딩 중복 방지 및 조율 담당
     */
    @Provides
    @Singleton
    fun provideMarkerLoadingCoordinator(
        markerRepository: MarkerRepository,
        markerUIDelegate: MarkerUIDelegate,
        appStateManager: AppStateManager
    ): MarkerLoadingCoordinator {
        return MarkerLoadingCoordinatorImpl(
            markerRepository,
            markerUIDelegate,
            appStateManager
        )
    }
} 