package com.parker.hotkey.presentation.map.processor

import com.parker.hotkey.domain.manager.LocationTracker
import com.parker.hotkey.domain.repository.LocationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 위치 추적 관련 기능을 관리하는 클래스
 * LocationTracker와 LocationStateHolder 사이의 중개자 역할을 합니다.
 */
@Singleton
class LocationTrackingManager @Inject constructor(
    private val locationTracker: LocationTracker,
    private val locationManager: LocationManager,
    private val locationStateHolder: LocationStateHolder,
    private val coroutineScope: CoroutineScope
) {
    private var locationTrackingJob: Job? = null
    private var geohashTrackingJob: Job? = null
    
    /**
     * 위치 추적 초기화
     * 위치 추적기를 초기화하고 상태 구독을 설정합니다.
     */
    fun initialize() {
        Timber.d("LocationTrackingManager 초기화")
        locationTracker.initialize()
        setupLocationSubscription()
        setupGeohashSubscription()
    }
    
    /**
     * 위치 구독 설정
     * LocationTracker의 위치 업데이트를 구독하고 LocationStateHolder에 반영합니다.
     */
    private fun setupLocationSubscription() {
        locationTrackingJob?.cancel()
        locationTrackingJob = coroutineScope.launch {
            locationTracker.currentLocation.collectLatest { location ->
                location?.let {
                    val latLng = locationStateHolder.mapDomainLocationToUiModel(it)
                    locationStateHolder.updateLocation(latLng)
                }
            }
        }
    }
    
    /**
     * Geohash 구독 설정
     * LocationTracker의 geohash 업데이트를 구독하고 LocationStateHolder에 반영합니다.
     */
    private fun setupGeohashSubscription() {
        geohashTrackingJob?.cancel()
        geohashTrackingJob = coroutineScope.launch {
            locationTracker.getGeohashWithNeighborsFlow().collectLatest { (geohash, neighbors) ->
                locationStateHolder.updateGeohash(geohash, neighbors)
            }
        }
    }
    
    /**
     * 위치 추적 시작
     * @return 위치 추적 시작 성공 여부
     */
    suspend fun startTracking(): Result<Boolean> {
        Timber.d("위치 추적 시작 요청")
        
        // 안드로이드 10 기기에서 위치 권한 확인 추가
        if (!locationManager.hasLocationPermission()) {
            Timber.w("위치 권한이 없어 위치 추적을 시작할 수 없습니다")
            locationStateHolder.updateLocationTrackingState(false)
            return Result.failure(SecurityException("위치 권한이 필요합니다"))
        }
        
        // 위치 서비스가 활성화되어 있는지 확인
        try {
            // 먼저 시스템 속성 체크로 위치 서비스 활성화 먼저 확인
            val result = locationTracker.startTracking()
            
            result.onSuccess { success ->
                if (success) {
                    locationStateHolder.updateLocationTrackingState(true)
                    Timber.d("위치 추적 시작 성공")
                } else {
                    locationStateHolder.updateLocationTrackingState(false)
                    Timber.d("위치 추적 시작 실패")
                }
            }.onFailure { error ->
                locationStateHolder.updateLocationTrackingState(false)
                locationStateHolder.setError(error)
                Timber.e(error, "위치 추적 시작 중 오류 발생")
            }
            
            return result
        } catch (e: Exception) {
            Timber.e(e, "위치 추적 시작 중 예외 발생")
            locationStateHolder.updateLocationTrackingState(false)
            locationStateHolder.setError(e)
            return Result.failure(e)
        }
    }
    
    /**
     * 위치 추적 중지
     */
    fun stopTracking() {
        Timber.d("위치 추적 중지 요청")
        locationTracker.stopTracking()
        locationStateHolder.updateLocationTrackingState(false)
    }
    
    /**
     * 리소스 정리
     */
    fun cleanup() {
        Timber.d("LocationTrackingManager 정리 중")
        locationTrackingJob?.cancel()
        geohashTrackingJob?.cancel()
        locationTrackingJob = null
        geohashTrackingJob = null
        stopTracking()
    }
} 