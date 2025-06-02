package com.parker.hotkey.di

import android.content.Context
import com.parker.hotkey.data.remote.network.ConnectionStateMonitor
import com.parker.hotkey.di.qualifier.UseTemporaryMarkerFeature
import com.parker.hotkey.domain.manager.EditModeManager
import com.parker.hotkey.domain.manager.LocationTracker
import com.parker.hotkey.domain.manager.MarkerManager
import com.parker.hotkey.domain.manager.MemoManager
import com.parker.hotkey.domain.manager.TemporaryMarkerManager
import com.parker.hotkey.domain.repository.AuthRepository
import com.parker.hotkey.domain.repository.LocationManager
import com.parker.hotkey.domain.repository.MarkerRepository
import com.parker.hotkey.domain.repository.MemoRepository
import com.parker.hotkey.domain.repository.SyncRepository
import com.parker.hotkey.domain.usecase.SyncDataUseCase
import com.parker.hotkey.domain.usecase.marker.CreateMarkerUseCase
import com.parker.hotkey.domain.usecase.marker.DeleteMarkerWithValidationUseCase
import com.parker.hotkey.domain.util.AppStateManager
import com.parker.hotkey.presentation.map.event.MapEventHandler
import com.parker.hotkey.presentation.map.markers.MarkerViewModel
import com.parker.hotkey.presentation.map.processor.MapStateProcessor
import com.parker.hotkey.presentation.memo.MemoInteractor
import com.parker.hotkey.util.SharedPrefsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * MapViewModel의 저장소(Repository) 의존성을 그룹화한 클래스
 */
class MapRepositoryDependencies @Inject constructor(
    val markerRepository: MarkerRepository,
    val memoRepository: MemoRepository,
    val authRepository: AuthRepository,
    val syncRepository: SyncRepository
)

/**
 * MapViewModel의 유스케이스 의존성을 그룹화한 클래스
 */
class MapUseCaseDependencies @Inject constructor(
    val createMarkerUseCase: CreateMarkerUseCase,
    val deleteMarkerWithValidationUseCase: DeleteMarkerWithValidationUseCase,
    val syncDataUseCase: SyncDataUseCase
)

/**
 * MapViewModel의 매니저 의존성을 그룹화한 클래스
 */
class MapManagerDependencies @Inject constructor(
    val editModeManager: EditModeManager,
    val locationManager: LocationManager,
    val locationTracker: LocationTracker,
    val markerManager: MarkerManager,
    val memoManager: MemoManager,
    val temporaryMarkerManager: TemporaryMarkerManager
)

/**
 * MapViewModel의 프로세서 및 이벤트 핸들러 의존성을 그룹화한 클래스
 */
class MapProcessorDependencies @Inject constructor(
    val mapStateProcessor: MapStateProcessor,
    val mapEventHandler: MapEventHandler,
    val memoInteractor: MemoInteractor
)

/**
 * MapViewModel의 유틸리티 의존성을 그룹화한 클래스
 */
class MapUtilityDependencies @Inject constructor(
    @ApplicationContext val context: Context,
    val connectionStateMonitor: ConnectionStateMonitor,
    val sharedPrefsManager: SharedPrefsManager,
    val appStateManager: AppStateManager
)

/**
 * MapViewModel의 기능 플래그 의존성을 그룹화한 클래스
 */
class MapFeatureFlagDependencies @Inject constructor(
    @UseTemporaryMarkerFeature val useTemporaryMarkerFeature: Boolean
) 