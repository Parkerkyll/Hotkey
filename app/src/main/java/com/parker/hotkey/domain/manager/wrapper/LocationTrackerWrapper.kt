package com.parker.hotkey.domain.manager.wrapper

import com.parker.hotkey.domain.manager.LocationTracker
import com.parker.hotkey.domain.manager.MarkerManager
import com.parker.hotkey.domain.manager.impl.LocationTrackerImpl.LocationChangedEvent
import com.parker.hotkey.domain.model.Location
import com.parker.hotkey.util.Debouncer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicInteger

/**
 * LocationTracker에 디바운싱 기능을 추가한 래퍼 클래스
 * 원래 LocationTracker의 모든 기능을 제공하면서 위치 업데이트 요청에 디바운싱을 적용합니다.
 */
@Singleton
class LocationTrackerWrapper @Inject constructor(
    private val locationTracker: LocationTracker,
    private val coroutineScope: CoroutineScope
) : LocationTracker {
    
    // 위치 변경 디바운서 (300ms)
    private val locationDebouncer = Debouncer<Location>(300L)
    
    // 지오해시 변경 디바운서 (500ms)
    private val geohashDebouncer = Debouncer<Pair<String, List<String>>>(500L)
    
    // 테스트용 로그 카운터
    private val locationUpdateCounter = AtomicInteger(0)
    private val markerLoadingCounter = AtomicInteger(0)
    private val skippedMarkerLoadingCounter = AtomicInteger(0)
    
    // LocationTracker 인터페이스 구현
    override val currentLocation: StateFlow<Location?> = locationTracker.currentLocation
    override val currentGeohash: StateFlow<String?> = locationTracker.currentGeohash
    override val neighbors: StateFlow<List<String>> = locationTracker.neighbors
    override val initialized: StateFlow<Boolean> = locationTracker.initialized
    override val locationChangedEvent: SharedFlow<LocationChangedEvent> = locationTracker.locationChangedEvent
    
    override fun initialize() {
        Timber.d("LocationTrackerWrapper 초기화")
        locationTracker.initialize()
    }
    
    override suspend fun startTracking(): Result<Boolean> {
        Timber.d("LocationTrackerWrapper 위치 추적 시작")
        resetCounters()
        return locationTracker.startTracking()
    }
    
    override fun stopTracking() {
        Timber.d("LocationTrackerWrapper 위치 추적 중지")
        locationDebouncer.cancel()
        geohashDebouncer.cancel()
        locationTracker.stopTracking()
    }
    
    override fun getGeohashWithNeighborsFlow(): Flow<Pair<String?, List<String>>> {
        return locationTracker.getGeohashWithNeighborsFlow()
    }
    
    override fun calculateGeohash(location: Location): String {
        return locationTracker.calculateGeohash(location)
    }
    
    /**
     * 위치 업데이트를 디바운싱하여 처리합니다.
     * 짧은 시간 내 여러 위치 업데이트가 들어오면 마지막 업데이트만 처리합니다.
     */
    override fun updateLocation(location: Location) {
        val count = locationUpdateCounter.incrementAndGet()
        Timber.d("위치 업데이트 요청 #$count: lat=${location.latitude}, lng=${location.longitude}")
        
        // 위치 업데이트에 디바운싱 적용
        locationDebouncer.debounce(location) { finalLocation ->
            Timber.d("디바운싱 후 위치 업데이트(#$count): lat=${finalLocation.latitude}, lng=${finalLocation.longitude}")
            locationTracker.updateLocation(finalLocation)
            
            // 통계 출력
            logStatistics()
        }
    }
    
    /**
     * 위치 추적과 마커 로딩을 연결하는 메서드
     * 위치가 변경될 때마다 해당 영역의 마커를 로드합니다.
     * 디바운싱을 통해 빈번한 위치 변경 시 불필요한 마커 로딩을 방지합니다.
     */
    override fun setupMarkerTracking(markerManager: MarkerManager) {
        Timber.d("마커 트래킹 설정 (디바운싱 버전)")
        
        locationTracker.setupMarkerTracking(object : MarkerManager by markerManager {
            override fun loadMarkersInArea(geohash: String, neighbors: List<String>) {
                val requestCount = markerLoadingCounter.incrementAndGet()
                
                // 지오해시 변경 요청에 디바운싱 적용
                geohashDebouncer.debounce(Pair(geohash, neighbors)) { (finalGeohash, finalNeighbors) ->
                    val loadCount = requestCount - skippedMarkerLoadingCounter.getAndIncrement()
                    Timber.d("===== 마커 로딩 통계 =====")
                    Timber.d("디바운싱 후 마커 로딩 #$loadCount (요청 #$requestCount): geohash=$finalGeohash, 이웃 수=${finalNeighbors.size}")
                    Timber.d("전체 요청: ${markerLoadingCounter.get()}, 실제 로딩: $loadCount, 스킵: ${skippedMarkerLoadingCounter.get() - 1}")
                    Timber.d("디바운싱 효율: ${String.format("%.1f", (requestCount - loadCount).toFloat() / requestCount.toFloat() * 100)}%")
                    Timber.d("============================")
                    
                    coroutineScope.launch {
                        markerManager.loadMarkersInArea(finalGeohash, finalNeighbors)
                    }
                }
            }
            
            override fun loadMarkersInAreaOptimized(geohash: String, neighbors: List<String>, zoomLevel: Double) {
                val requestCount = markerLoadingCounter.incrementAndGet()
                
                // 지오해시 변경 요청에 디바운싱 적용
                geohashDebouncer.debounce(Pair(geohash, neighbors)) { (finalGeohash, finalNeighbors) ->
                    val loadCount = requestCount - skippedMarkerLoadingCounter.getAndIncrement()
                    Timber.d("===== 마커 로딩 통계 (최적화) =====")
                    Timber.d("디바운싱 후 마커 로딩 #$loadCount (요청 #$requestCount): geohash=$finalGeohash, 이웃 수=${finalNeighbors.size}, 줌=$zoomLevel")
                    Timber.d("전체 요청: ${markerLoadingCounter.get()}, 실제 로딩: $loadCount, 스킵: ${skippedMarkerLoadingCounter.get() - 1}")
                    Timber.d("디바운싱 효율: ${String.format("%.1f", (requestCount - loadCount).toFloat() / requestCount.toFloat() * 100)}%")
                    Timber.d("============================")
                    
                    coroutineScope.launch {
                        markerManager.loadMarkersInAreaOptimized(finalGeohash, finalNeighbors, zoomLevel)
                    }
                }
            }
        })
    }
    
    /**
     * 측정 카운터 초기화
     */
    private fun resetCounters() {
        locationUpdateCounter.set(0)
        markerLoadingCounter.set(0)
        skippedMarkerLoadingCounter.set(0)
        Timber.d("위치 트래커 통계 카운터 초기화")
    }
    
    /**
     * 현재 통계 상태 로깅
     */
    private fun logStatistics() {
        val locationTotal = locationUpdateCounter.get()
        val markerTotal = markerLoadingCounter.get()
        val skipped = skippedMarkerLoadingCounter.get() - 1
        val actual = if (markerTotal > skipped) markerTotal - skipped else 0
        
        Timber.d("===== 위치 트래커 통계 =====")
        Timber.d("위치 업데이트: $locationTotal 회")
        Timber.d("마커 로딩 요청: $markerTotal 회")
        Timber.d("실제 마커 로딩: $actual 회")
        Timber.d("스킵된 마커 로딩: $skipped 회")
        Timber.d("============================")
    }
    
    override fun getCurrentZoom(): Double? {
        return locationTracker.getCurrentZoom()
    }
} 