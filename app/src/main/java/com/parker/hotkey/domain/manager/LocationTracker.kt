package com.parker.hotkey.domain.manager

import com.parker.hotkey.domain.manager.MarkerManager
import com.parker.hotkey.domain.model.Location
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 위치 추적 및 Geohash 관리를 담당하는 인터페이스
 * 위치 업데이트를 수신하고, Geohash 계산 및 이웃 Geohash를 제공합니다.
 */
interface LocationTracker {
    /**
     * 현재 위치 상태
     */
    val currentLocation: StateFlow<Location?>
    
    /**
     * 현재 위치의 Geohash
     */
    val currentGeohash: StateFlow<String?>
    
    /**
     * 현재 Geohash의 이웃 Geohash 목록
     */
    val neighbors: StateFlow<List<String>>
    
    /**
     * 초기화 완료 상태
     */
    val initialized: StateFlow<Boolean>
    
    /**
     * 위치 변경 이벤트
     */
    val locationChangedEvent: SharedFlow<com.parker.hotkey.domain.manager.impl.LocationTrackerImpl.LocationChangedEvent>
    
    /**
     * 매니저 초기화
     * 필요한 초기 설정을 수행합니다.
     */
    fun initialize()
    
    /**
     * 위치 추적 시작
     * @return 위치 추적 시작 성공 여부를 포함한 Result
     */
    suspend fun startTracking(): Result<Boolean>
    
    /**
     * 위치 추적 중지
     */
    fun stopTracking()
    
    /**
     * Geohash와 이웃 Geohash를 함께 제공하는 Flow
     *
     * @return Pair<String?, List<String>> 형태의 Flow
     */
    fun getGeohashWithNeighborsFlow(): Flow<Pair<String?, List<String>>>
    
    /**
     * 주어진 위치의 Geohash 계산
     *
     * @param location 위치 객체
     * @return Geohash 문자열
     */
    fun calculateGeohash(location: Location): String
    
    /**
     * 현재 위치를 업데이트
     * 
     * @param location 새로운 위치 객체
     */
    fun updateLocation(location: Location)
    
    /**
     * 위치 추적과 마커 로딩을 연결하는 메서드
     * 위치가 변경될 때마다 해당 영역의 마커를 로드합니다.
     * 
     * @param markerManager 마커 매니저 인스턴스
     */
    fun setupMarkerTracking(markerManager: MarkerManager)
    
    /**
     * 현재 줌 레벨 반환
     * 
     * @return 현재 줌 레벨 또는 null (줌 레벨을 알 수 없는 경우)
     */
    fun getCurrentZoom(): Double?
} 