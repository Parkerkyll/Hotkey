package com.parker.hotkey.domain.manager.impl

import com.parker.hotkey.domain.model.Location
import com.parker.hotkey.domain.repository.LocationManager
import com.parker.hotkey.domain.manager.LocationTracker
import com.parker.hotkey.domain.manager.MarkerManager
import com.parker.hotkey.util.GeoHashUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import com.parker.hotkey.domain.constants.MapConstants
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.parker.hotkey.domain.constants.TimingConstants
import com.parker.hotkey.domain.constants.GeohashConstants
import java.util.UUID

/**
 * 위치 추적 및 Geohash 관리를 담당하는 클래스
 * 위치 업데이트를 수신하고, Geohash 계산 및 이웃 Geohash를 제공합니다.
 */
@Singleton
class LocationTrackerImpl @Inject constructor(
    private val locationManager: LocationManager,
    private val coroutineScope: CoroutineScope
) : LocationTracker {
    // 초기화 상태
    private val _initialized = MutableStateFlow(false)
    override val initialized: StateFlow<Boolean> = _initialized.asStateFlow()
    
    // 현재 위치 상태
    private val _currentLocation = MutableStateFlow<Location?>(null)
    override val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()
    
    // 현재 위치의 Geohash
    private val _currentGeohash = MutableStateFlow<String?>(null)
    override val currentGeohash: StateFlow<String?> = _currentGeohash.asStateFlow()
    
    // 현재 Geohash의 이웃 Geohash 목록
    private val _neighbors = MutableStateFlow<List<String>>(emptyList())
    override val neighbors: StateFlow<List<String>> = _neighbors.asStateFlow()
    
    // 현재 줌 레벨 상태 추가
    private val _currentZoom = MutableStateFlow<Double?>(null)
    
    // 위치 변경 이벤트를 위한 SharedFlow 추가
    private val _locationChangedEvent = MutableSharedFlow<LocationChangedEvent>()
    override val locationChangedEvent: SharedFlow<LocationChangedEvent> = _locationChangedEvent.asSharedFlow()
    
    // 위치 추적 활성화 상태
    private var isTracking = false
    
    // 위치 추적 잡
    private var trackingJob: Job? = null
    
    // 마커 추적 잡
    private var markerTrackingJob: Job? = null
    
    /**
     * 위치 변경 이벤트를 담는 데이터 클래스
     */
    data class LocationChangedEvent(
        val newGeohash: String,
        val previousGeohash: String?
    )
    
    /**
     * 매니저 초기화 메서드
     * 이 메서드는 위치 추적을 시작하고 초기화 상태를 설정합니다.
     */
    override fun initialize() {
        if (_initialized.value) {
            Timber.d("이미 초기화되었습니다.")
            return
        }
        
        Timber.d("LocationTracker 초기화 시작")
        
        // 약간의 지연을 추가하여 위치 권한 및 서비스가 확인되도록
        coroutineScope.launch {
            try {
                // 안드로이드 10 이상에서 위치 초기화 시 약간의 지연 추가
                delay(500)
                
                // 위치 추적 시작 시도
                val result = startTracking()
                result.onSuccess { success ->
                    if (success) {
                        Timber.d("위치 추적 시작 성공")
                        _initialized.value = true
                    } else {
                        Timber.d("위치 추적 시작 실패, 3초 후 재시도")
                        // 재시도 로직 추가
                        delay(3000)
                        retryStartTracking()
                    }
                }.onFailure { error ->
                    Timber.e(error, "위치 추적 초기화 오류, 3초 후 재시도")
                    // 재시도 로직 추가
                    delay(3000)
                    retryStartTracking()
                }
            } catch (e: Exception) {
                Timber.e(e, "위치 추적 초기화 중 예외 발생")
            }
        }
    }
    
    /**
     * 위치 추적 시작 재시도
     */
    private fun retryStartTracking() {
        coroutineScope.launch {
            try {
                Timber.d("위치 추적 시작 재시도")
                val result = startTracking()
                result.onSuccess { success ->
                    if (success) {
                        Timber.d("위치 추적 재시도 성공")
                        _initialized.value = true
                    } else {
                        Timber.d("위치 추적 재시도 실패")
                    }
                }.onFailure { error ->
                    Timber.e(error, "위치 추적 재시도 오류")
                }
            } catch (e: Exception) {
                Timber.e(e, "위치 추적 재시도 중 예외 발생")
            }
        }
    }
    
    /**
     * 위치 추적 시작
     * @return 위치 추적 시작 성공 여부를 포함한 Result
     */
    override suspend fun startTracking(): Result<Boolean> {
        if (isTracking) {
            Timber.d("이미 위치 추적 중입니다.")
            return Result.success(true)
        }
        
        return try {
            Timber.d("위치 추적 시작 (GPS 전용)")
            
            // 이전 Job이 있다면 취소
            trackingJob?.cancel()
            
            // 새로운 Job 시작
            trackingJob = coroutineScope.launch {
                try {
                    // 기존 상태 초기화
                    if (_currentLocation.value == null) {
                        Timber.d("위치 정보가 없어 위치 초기화 시작")
                    } else {
                        Timber.d("이전 위치 정보 있음: lat=${_currentLocation.value?.latitude}, lng=${_currentLocation.value?.longitude}")
                    }
                    
                    // 위치 업데이트 구독
                    locationManager.getLocationUpdates()
                        .catch { e ->
                            when (e) {
                                is kotlinx.coroutines.CancellationException -> {
                                    // 코루틴 취소는 정상적인 작업 중단이므로 디버그 로그만 남김
                                    Timber.d("위치 업데이트 코루틴이 취소됨: ${e.message}")
                                }
                                else -> {
                                    // 실제 오류는 에러로 로깅
                                    Timber.e(e, "위치 업데이트 에러")
                                    isTracking = false
                                }
                            }
                        }
                        .collect { location ->
                            Timber.d("위치 업데이트 수신: lat=${location.latitude}, lng=${location.longitude}")
                            updateLocation(location)
                        }
                } catch (e: Exception) {
                    Timber.e(e, "위치 추적 코루틴 내부 오류")
                    throw e
                }
            }
            
            isTracking = true
            Timber.d("위치 추적 시작 성공")
            Result.success(true)
        } catch (e: Exception) {
            when (e) {
                is kotlinx.coroutines.CancellationException -> {
                    // 코루틴 취소는 정상적인 작업 중단이므로 디버그 로그만 남김
                    Timber.d("위치 추적 코루틴이 취소됨: ${e.message}")
                    Result.success(false)
                }
                else -> {
                    // 실제 오류는 에러로 로깅
                    Timber.e(e, "위치 추적 시작 에러")
                    isTracking = false
                    Result.failure(e)
                }
            }
        }
    }
    
    /**
     * 위치 추적 중지
     */
    override fun stopTracking() {
        if (!isTracking) {
            Timber.d("이미 위치 추적이 중지된 상태입니다.")
            return
        }
        
        Timber.d("위치 추적 중지")
        trackingJob?.cancel()
        markerTrackingJob?.cancel()
        trackingJob = null
        markerTrackingJob = null
        isTracking = false
        
        // 상태 초기화
        _currentLocation.value = null
    }
    
    /**
     * 위치 업데이트 처리
     * 새 위치를 상태에 반영하고 Geohash를 계산합니다.
     * @param location 새 위치
     */
    override fun updateLocation(location: Location) {
        val locationId = UUID.randomUUID().toString().take(6)
        Timber.tag("API_FLOW").d("[$locationId] 위치 업데이트 발생: lat=${location.latitude}, lng=${location.longitude}")
        
        try {
            val previousGeohash = _currentGeohash.value
            
            // 현재 위치 업데이트
            _currentLocation.value = location
            
            // 새 위치의 Geohash 계산 (정밀도 7 사용)
            val startTime = System.currentTimeMillis()
            val newGeohash = GeoHashUtil.encode(
                location.latitude,
                location.longitude,
                GeohashConstants.GEOHASH_PRECISION
            )
            
            // 이웃 Geohash 계산
            val neighborGeohashes = GeoHashUtil.getNeighbors(newGeohash)
            val calcDuration = System.currentTimeMillis() - startTime
            
            Timber.tag("API_FLOW").d("[$locationId] Geohash 계산: $newGeohash (${calcDuration}ms)")
            Timber.tag("API_FLOW").d("[$locationId] 이전 Geohash: $previousGeohash, 변경 여부: ${newGeohash != previousGeohash}")
            
            // Geohash 변경 여부 확인
            if (newGeohash != previousGeohash) {
                _currentGeohash.value = newGeohash
                _neighbors.value = neighborGeohashes
                
                // Geohash 변경 이벤트 발생
                Timber.tag("API_FLOW").d("[$locationId] 새 Geohash 영역으로 이동: $newGeohash, 이벤트 발행")
                coroutineScope.launch {
                    _locationChangedEvent.emit(LocationChangedEvent(newGeohash, previousGeohash))
                }
            } else {
                Timber.tag("API_FLOW").d("[$locationId] Geohash 변경 없음, 위치만 업데이트")
            }
        } catch (e: Exception) {
            Timber.tag("API_FLOW").e(e, "[$locationId] 위치 업데이트 처리 중 오류 발생")
        }
    }
    
    /**
     * 위치를 geohash로 변환
     * 항상 정밀도 6의 geohash를 사용합니다.
     */
    override fun calculateGeohash(location: Location): String {
        return GeoHashUtil.encode(location.latitude, location.longitude, GeohashConstants.GEOHASH_PRECISION)
    }
    
    /**
     * 현재 위치와 이웃 Geohash 쌍을 Flow로 제공
     * 이 Flow는 위치 변경 시 새 Geohash 및 이웃 목록으로 업데이트됩니다.
     */
    override fun getGeohashWithNeighborsFlow(): Flow<Pair<String?, List<String>>> {
        Timber.tag("API_FLOW").d("Geohash와 이웃 Flow 구독 시작")
        
        return combine(
            _currentGeohash.asStateFlow(),
            _neighbors.asStateFlow()
        ) { geohash, neighbors ->
            if (geohash != null) {
                Timber.tag("API_FLOW").d("Geohash Flow 업데이트: $geohash, 이웃: ${neighbors.size}개")
            }
            Pair(geohash, neighbors)
        }
    }
    
    /**
     * 위치 추적과 마커 로딩을 연결하는 개선된 메서드
     * 위치가 변경될 때만 해당 영역의 마커를 로드합니다. (카메라 이동 시에는 로드하지 않음)
     * @param markerManager 마커 매니저 인스턴스
     */
    override fun setupMarkerTracking(markerManager: MarkerManager) {
        // 이전 Job이 있다면 취소
        markerTrackingJob?.cancel()
        
        // 새로운 Job 시작
        markerTrackingJob = coroutineScope.launch {
            try {
                Timber.d("마커 추적 설정 시작 (개선된 방식)")
                
                // 이전 geohash 값을 저장하기 위한 변수
                var previousGeohash: String? = null
                
                getGeohashWithNeighborsFlow().collect { (geohash, neighbors) ->
                    if (geohash != null) {
                        // 새로운 geohash일 경우에만 마커 로드 및 데이터 동기화
                        if (geohash != previousGeohash) {
                            Timber.d("새로운 지역으로 이동 감지: $previousGeohash → $geohash")
                            
                            // 마커 매니저를 통해 마커 로드
                            Timber.d("Geohash 변경에 따른 마커 로드: $geohash, 이웃: ${neighbors.size}개")
                            markerManager.loadMarkersInAreaOptimized(geohash, neighbors, MapConstants.DEFAULT_ZOOM)
                            
                            // 이벤트 발행 - 외부에서 처리할 수 있도록
                            _locationChangedEvent.emit(LocationChangedEvent(geohash, previousGeohash))
                            
                            // 이전 geohash 업데이트
                            previousGeohash = geohash
                        }
                    }
                }
            } catch (e: Exception) {
                when (e) {
                    is kotlinx.coroutines.CancellationException -> {
                        // 코루틴 취소는 정상적인 작업 중단이므로 디버그 로그만 남김
                        Timber.d("마커 추적 코루틴이 취소됨: ${e.message}")
                    }
                    else -> {
                        // 실제 오류는 에러로 로깅
                        Timber.e(e, "마커 추적 중 오류 발생")
                        // 오류 발생 시 자동으로 복구 시도
                        delay(1000)
                        setupMarkerTracking(markerManager)
                    }
                }
            }
        }
    }
    
    /**
     * 레거시 메서드 - 내부 호환성을 위해 유지
     */
    @Deprecated("Use setupMarkerTracking(MarkerManager) instead")
    fun setupMarkerTrackingOptimized(
        markerLoader: (geohash: String, neighbors: List<String>, zoomLevel: Double) -> Unit,
        zoomLevelProvider: () -> Double,
        errorHandler: (Throwable) -> Unit
    ) {
        setupMarkerTrackingLegacy(
            markerLoader = markerLoader,
            zoomLevelProvider = zoomLevelProvider,
            errorHandler = errorHandler,
            logPrefix = "최적화된"
        )
    }
    
    /**
     * 레거시 메서드 - 내부 호환성을 위해 유지
     */
    @Deprecated("Use setupMarkerTracking(MarkerManager) instead")
    fun setupMarkerTrackingGeohash6Optimized(
        markerLoader: (geohash: String, neighbors: List<String>, zoomLevel: Double) -> Unit,
        zoomLevelProvider: () -> Double,
        errorHandler: (Throwable) -> Unit
    ) {
        setupMarkerTrackingLegacy(
            markerLoader = markerLoader,
            zoomLevelProvider = zoomLevelProvider,
            errorHandler = errorHandler,
            logPrefix = "geohash6 전용"
        )
    }
    
    /**
     * 레거시 마커 추적 설정을 위한 공통 메서드
     * setupMarkerTrackingOptimized와 setupMarkerTrackingGeohash6Optimized의 중복 코드를 제거하기 위함
     */
    private fun setupMarkerTrackingLegacy(
        markerLoader: (geohash: String, neighbors: List<String>, zoomLevel: Double) -> Unit,
        zoomLevelProvider: () -> Double,
        errorHandler: (Throwable) -> Unit,
        logPrefix: String
    ) {
        coroutineScope.launch {
            try {
                Timber.d("$logPrefix 마커 추적 설정 시작 (레거시)")
                
                // 이전 geohash 값을 저장하기 위한 변수
                var previousGeohash: String? = null
                
                getGeohashWithNeighborsFlow().collect { (geohash, neighbors) ->
                    if (geohash != null) {
                        // 새로운 geohash일 경우에만 마커 로드
                        if (geohash != previousGeohash) {
                            val currentZoomLevel = try {
                                zoomLevelProvider()
                            } catch (e: Exception) {
                                Timber.w(e, "줌 레벨 가져오기 실패, 기본값 사용")
                                MapConstants.DEFAULT_ERROR_ZOOM // 기본 줌 레벨
                            }
                            
                            Timber.d("$logPrefix 마커 로드: geohash=$geohash, 줌=$currentZoomLevel")
                            markerLoader(geohash, neighbors, currentZoomLevel)
                            
                            // 이전 geohash 업데이트
                            previousGeohash = geohash
                        }
                    }
                }
            } catch (e: Exception) {
                when (e) {
                    is kotlinx.coroutines.CancellationException -> {
                        // 코루틴 취소는 정상적인 작업 중단이므로 디버그 로그만 남김
                        Timber.d("$logPrefix 마커 추적 코루틴이 취소됨: ${e.message}")
                    }
                    else -> {
                        // 실제 오류만 에러 핸들러에 전달
                        Timber.e(e, "$logPrefix 마커 추적 중 오류 발생")
                        errorHandler(e)
                    }
                }
            }
        }
    }
    
    /**
     * 리소스 정리
     */
    suspend fun cleanup() {
        // 위치 추적 중지
        stopTracking()
        
        // 상태 초기화
        _currentLocation.value = null
        _currentGeohash.value = null
        _neighbors.value = emptyList()
        _initialized.value = false
        
        // 작업 취소
        markerTrackingJob?.cancel()
        markerTrackingJob = null
        
        Timber.d("LocationTracker 리소스 정리 완료")
    }
    
    /**
     * 현재 줌 레벨 반환
     * 
     * @return 현재 줌 레벨 또는 기본값 (줌 레벨을 알 수 없는 경우)
     */
    override fun getCurrentZoom(): Double? {
        return _currentZoom.value
    }
    
    /**
     * 현재 줌 레벨 설정
     * @param zoomLevel 맵 줌 레벨
     */
    fun updateZoom(zoomLevel: Double) {
        // 이전 줌 레벨과 현재 줌 레벨 사이에 임계값 이상 차이가 있을 때만 로깅
        val prevZoom = _currentZoom.value
        val zoomDiff = prevZoom?.let { Math.abs(it - zoomLevel) } ?: 0.0
        
        if (prevZoom == null || zoomDiff > 0.5) {
            Timber.tag("API_FLOW").d("줌 레벨 업데이트: $zoomLevel (이전: $prevZoom)")
        }
        
        _currentZoom.value = zoomLevel
    }
} 