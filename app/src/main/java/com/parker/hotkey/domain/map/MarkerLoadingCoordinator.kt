package com.parker.hotkey.domain.map

/**
 * 마커 로딩 작업을 조율하는 코디네이터
 * 모든 마커 로딩 요청을 일원화하여 중복 로딩을 방지합니다.
 */
interface MarkerLoadingCoordinator {
    /**
     * 지정된 지역의 마커를 로딩합니다.
     * @param geohash 중심 지오해시
     * @param neighbors 주변 지오해시 목록
     * @param zoom 현재 줌 레벨
     * @param forceRefresh 강제 새로고침 여부
     * @return 로딩 작업 성공 여부
     */
    suspend fun loadMarkers(geohash: String, neighbors: List<String>, zoom: Double, forceRefresh: Boolean = false): Boolean
    
    /**
     * UI에 표시된 마커를 새로고침합니다.
     */
    suspend fun refreshMarkersUI()
    
    /**
     * 앱 최초 시작 시 초기 마커 로딩
     */
    suspend fun initialLoadMarkers(geohash: String, neighbors: List<String>, zoom: Double)
    
    /**
     * 백그라운드에서 포그라운드로 전환 시 마커 새로고침
     */
    suspend fun foregroundRefreshMarkers(geohash: String, neighbors: List<String>, zoom: Double)
    
    /**
     * 새로운 지역 방문 시 마커 로딩
     */
    suspend fun loadMarkersForNewArea(geohash: String, neighbors: List<String>, zoom: Double)
} 