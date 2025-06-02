package com.parker.hotkey.di

import com.parker.hotkey.di.qualifier.ApplicationScope
import com.parker.hotkey.di.qualifier.UseTemporaryMarkerFeature
import com.parker.hotkey.domain.manager.EditModeManager
import com.parker.hotkey.domain.manager.MarkerManager
import com.parker.hotkey.domain.manager.MarkerStateAdapter
import com.parker.hotkey.domain.manager.MemoManager
import com.parker.hotkey.domain.manager.LocationTracker
import com.parker.hotkey.domain.manager.TemporaryMarkerManager
import com.parker.hotkey.domain.manager.impl.EditModeManagerImpl
import com.parker.hotkey.domain.manager.impl.LocationTrackerImpl
import com.parker.hotkey.domain.manager.impl.MarkerManagerImpl
import com.parker.hotkey.domain.manager.impl.MemoManagerImpl
import com.parker.hotkey.domain.manager.impl.TemporaryMarkerManagerImpl
import com.parker.hotkey.domain.manager.wrapper.LocationTrackerWrapper
import com.parker.hotkey.domain.repository.AuthRepository
import com.parker.hotkey.domain.repository.LocationManager
import com.parker.hotkey.domain.repository.MarkerRepository
import com.parker.hotkey.domain.repository.MemoRepository
import com.parker.hotkey.domain.repository.SyncRepository
import com.parker.hotkey.domain.usecase.UploadChangesUseCase
import com.parker.hotkey.domain.usecase.marker.CreateMarkerUseCase
import com.parker.hotkey.domain.usecase.marker.DeleteMarkerUseCase
import com.parker.hotkey.domain.usecase.marker.DeleteMarkerWithValidationUseCase
import com.parker.hotkey.domain.usecase.memo.CreateMemoUseCase
import com.parker.hotkey.domain.usecase.memo.DeleteMemoUseCase
import com.parker.hotkey.domain.usecase.memo.GetMemosByMarkerIdUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Qualifier
import javax.inject.Singleton
import dagger.Lazy

/**
 * 디바운싱 위치 트래커 사용 여부를 결정하는 qualifier
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class UseLocationDebouncing

/**
 * 도메인 계층 관련 매니저 제공 모듈
 * - MapManagerModule
 * - ManagerModule
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainManagerModule {
    
    /**
     * 마커 상태 어댑터 제공
     */
    @Provides
    @Singleton
    fun provideMarkerStateAdapter(
        markerManager: Lazy<MarkerManager>,
        temporaryMarkerManager: Lazy<TemporaryMarkerManager>,
        @ApplicationScope coroutineScope: CoroutineScope
    ): MarkerStateAdapter {
        return MarkerStateAdapter(
            markerManager,
            temporaryMarkerManager, 
            coroutineScope
        )
    }
    
    /**
     * DeleteMarkerWithValidationUseCase 제공 - 순환 의존성 해결
     * Lazy<MarkerStateAdapter>를 주입받아 순환 의존성 해결
     */
    @Provides
    @Singleton
    fun provideDeleteMarkerWithValidationUseCase(
        markerRepository: MarkerRepository,
        memoRepository: MemoRepository,
        syncRepository: SyncRepository,
        markerStateAdapter: Lazy<MarkerStateAdapter>
    ): DeleteMarkerWithValidationUseCase {
        return DeleteMarkerWithValidationUseCase(
            markerRepository,
            memoRepository,
            syncRepository,
            markerStateAdapter.get()
        )
    }
    
    /**
     * 임시 마커 관리자 제공
     */
    @Provides
    @Singleton
    fun provideTemporaryMarkerManager(
        markerManager: Lazy<MarkerManager>,
        markerRepository: MarkerRepository,
        uploadChangesUseCase: UploadChangesUseCase,
        @ApplicationScope coroutineScope: CoroutineScope
    ): TemporaryMarkerManager {
        return TemporaryMarkerManagerImpl(
            markerManager,
            markerRepository,
            coroutineScope,
            uploadChangesUseCase
        )
    }
    
    /**
     * 마커 관리자 제공
     */
    @Provides
    @Singleton
    fun provideMarkerManager(
        markerRepository: MarkerRepository,
        createMarkerUseCase: CreateMarkerUseCase,
        deleteMarkerWithValidationUseCase: Lazy<DeleteMarkerWithValidationUseCase>,
        memoManager: Lazy<MemoManager>,
        markerStateAdapter: Lazy<MarkerStateAdapter>,
        temporaryMarkerManager: Lazy<TemporaryMarkerManager>,
        @ApplicationScope coroutineScope: CoroutineScope
    ): MarkerManager {
        return MarkerManagerImpl(
            markerRepository, 
            createMarkerUseCase, 
            deleteMarkerWithValidationUseCase.get(), 
            memoManager.get(),
            markerStateAdapter.get(),
            temporaryMarkerManager,
            coroutineScope
        )
    }
    
    /**
     * 메모 관리자 제공
     */
    @Provides
    @Singleton
    fun provideMemoManager(
        memoRepository: MemoRepository,
        markerRepository: MarkerRepository,
        authRepository: AuthRepository,
        createMemoUseCase: CreateMemoUseCase,
        deleteMemoUseCase: DeleteMemoUseCase,
        deleteMarkerUseCase: DeleteMarkerUseCase,
        deleteMarkerWithValidationUseCase: Lazy<DeleteMarkerWithValidationUseCase>,
        editModeManager: EditModeManager,
        uploadChangesUseCase: UploadChangesUseCase,
        @ApplicationScope coroutineScope: CoroutineScope
    ): MemoManager {
        return MemoManagerImpl(
            memoRepository = memoRepository,
            markerRepository = markerRepository,
            authRepository = authRepository,
            createMemoUseCase = createMemoUseCase,
            deleteMemoUseCase = deleteMemoUseCase,
            deleteMarkerUseCase = deleteMarkerUseCase,
            deleteMarkerWithValidationUseCase = deleteMarkerWithValidationUseCase.get(),
            editModeManager = editModeManager,
            uploadChangesUseCase = uploadChangesUseCase,
            coroutineScope = coroutineScope
        )
    }
    
    /**
     * 위치 추적 관리자 제공 (기본)
     */
    @Provides
    @Singleton
    fun provideLocationTrackerBase(
        locationManager: LocationManager,
        @ApplicationScope coroutineScope: CoroutineScope
    ): LocationTrackerImpl {
        return LocationTrackerImpl(locationManager, coroutineScope)
    }
    
    /**
     * 디바운싱을 적용한 위치 추적 관리자 제공
     */
    @Provides
    @Singleton
    fun provideLocationTrackerWrapper(
        locationTracker: LocationTrackerImpl,
        @ApplicationScope coroutineScope: CoroutineScope
    ): LocationTrackerWrapper {
        return LocationTrackerWrapper(locationTracker, coroutineScope)
    }
    
    /**
     * 사용할 위치 추적 관리자 선택
     * @param useDebouncing 디바운싱 적용 여부 (기본값: true)
     */
    @Provides
    @Singleton
    fun provideLocationTracker(
        locationTrackerImpl: LocationTrackerImpl,
        locationTrackerWrapper: LocationTrackerWrapper,
        @UseLocationDebouncing useDebouncing: Boolean
    ): LocationTracker {
        return if (useDebouncing) {
            locationTrackerWrapper
        } else {
            locationTrackerImpl
        }
    }
}

/**
 * 도메인 계층 관련 바인딩 모듈
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DomainManagerBindingsModule {
    @Binds
    @Singleton
    abstract fun bindEditModeManager(
        editModeManagerImpl: EditModeManagerImpl
    ): EditModeManager
}

/**
 * 도메인 계층의 UseCase 관련 모듈
 * - 기존 UseCaseModule 대체
 */
@Module
@InstallIn(ViewModelComponent::class)
object DomainUseCaseModule {
    // 대부분의 UseCase는 @Inject constructor로 직접 주입이 가능하나,
    // 필요시 여기에 Provides 메서드 추가
}

/**
 * 기능 플래그 관련 모듈
 * 이 모듈은 다양한 기능의 활성화 여부를 제어합니다.
 */
@Module
@InstallIn(SingletonComponent::class)
object FeatureFlagsModule {
    /**
     * 디바운싱 사용 여부
     * 개발자 옵션이나 빌드 설정을 통해 변경 가능합니다.
     */
    @Provides
    @Singleton
    @UseLocationDebouncing
    fun provideUseLocationDebouncing(): Boolean {
        // 현재는 기본적으로 활성화
        // 추후 BuildConfig나 설정으로 변경 가능
        return true
    }
} 