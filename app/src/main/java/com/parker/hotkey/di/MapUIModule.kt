package com.parker.hotkey.di

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.naver.maps.map.NaverMap
import com.naver.maps.map.util.FusedLocationSource
import com.parker.hotkey.di.qualifier.DefaultNaverMap
import com.parker.hotkey.di.qualifier.FragmentCoroutineScope
import com.parker.hotkey.di.qualifier.MapLocationSource
import com.parker.hotkey.presentation.map.MapConfigDelegate
import com.parker.hotkey.presentation.map.controllers.LocationPermissionController
import com.parker.hotkey.presentation.map.controllers.MapUIController
import com.parker.hotkey.presentation.map.markers.MarkerPool
import com.parker.hotkey.presentation.map.markers.MarkerUIDelegate
import com.parker.hotkey.presentation.map.markers.MarkerListenerManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.FragmentComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.FragmentScoped
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/**
 * 지도 UI 관련 의존성을 제공하는 모듈
 */
@Module
@InstallIn(SingletonComponent::class)
object MapUISingletonModule {

    /**
     * MarkerListenerManager 의존성 제공
     * 마커 리스너 관리를 위한 매니저
     */
    @Provides
    @Singleton
    fun provideMarkerListenerManager(@ApplicationContext context: Context): MarkerListenerManager {
        return MarkerListenerManager(context)
    }

    /**
     * MarkerPool 의존성 제공
     * 마커 객체 풀링을 통한 성능 최적화
     */
    @Provides
    @Singleton
    fun provideMarkerPool(@ApplicationContext context: Context, markerListenerManager: MarkerListenerManager): MarkerPool {
        return MarkerPool(context, markerListenerManager)
    }
    
    /**
     * MarkerUIDelegate 의존성 제공
     * 마커 UI 스타일 및 렌더링 관련 델리게이트
     */
    @Provides
    @Singleton
    fun provideMarkerUIDelegate(@ApplicationContext context: Context, markerPool: MarkerPool, markerListenerManager: MarkerListenerManager): MarkerUIDelegate {
        return MarkerUIDelegate(context, markerPool, markerListenerManager)
    }
    
    /**
     * MapUIController 의존성 제공
     * 지도 UI 컨트롤(버튼, 컨트롤러 등) 관련 클래스
     */
    @Provides
    @Singleton
    fun provideMapUIController(@ApplicationContext context: Context): MapUIController {
        return MapUIController(context)
    }
    
    /**
     * LocationPermissionController 의존성 제공
     * 위치 권한 관련 컨트롤러
     */
    @Provides
    @Singleton
    fun provideLocationPermissionController(@ApplicationContext context: Context): LocationPermissionController {
        return LocationPermissionController(context)
    }
}

/**
 * Fragment 범위의 지도 UI 관련 의존성을 제공하는 모듈
 */
@Module
@InstallIn(FragmentComponent::class)
object MapUIFragmentModule {
    
    /**
     * FusedLocationSource 의존성 제공
     * Fragment 컨텍스트가 필요한 위치 소스
     */
    @Provides
    @FragmentScoped
    @MapLocationSource
    fun provideFusedLocationSource(fragment: Fragment): FusedLocationSource {
        return FusedLocationSource(fragment, 1000)
    }

    /**
     * Fragment 코루틴 스코프 제공
     */
    @Provides
    @FragmentScoped
    @FragmentCoroutineScope
    fun provideFragmentCoroutineScope(fragment: Fragment): CoroutineScope {
        return fragment.lifecycleScope
    }

    /**
     * 기본 NaverMap 인스턴스 제공 (초기에는 null)
     */
    @Provides
    @FragmentScoped
    @DefaultNaverMap
    fun provideDefaultNaverMap(): NaverMap? {
        return null  // 실제 NaverMap은 MapFragment에서 초기화됩니다
    }

    /**
     * MapConfigDelegate 의존성 제공
     * 지도 설정 관련 델리게이트
     */
    @Provides
    @FragmentScoped
    fun provideMapConfigDelegate(
        @DefaultNaverMap naverMap: NaverMap?,
        @ApplicationContext context: Context,
        @MapLocationSource locationSource: FusedLocationSource,
        mapUIController: MapUIController,
        locationPermissionController: LocationPermissionController
    ): MapConfigDelegate {
        return MapConfigDelegate(naverMap, context, locationSource, mapUIController, locationPermissionController)
    }
} 