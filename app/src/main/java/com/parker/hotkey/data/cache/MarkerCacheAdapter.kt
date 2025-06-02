package com.parker.hotkey.data.cache

import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.repository.MarkerQueryOptions

/**
 * 마커 캐시 어댑터 인터페이스
 * 서로 다른 캐시 구현체(메모리, 디스크 등)를 통합하여 사용하기 위한 일관된 인터페이스 제공
 */
interface MarkerCacheAdapter {
    /**
     * 캐시에서 마커 데이터 조회
     * 
     * @param geohash 기준 지역 geohash
     * @param neighbors 이웃 지역 geohash 목록
     * @param options 쿼리 옵션
     * @return 캐시된 마커 목록 또는 null (캐시 미스)
     */
    suspend fun getMarkersFromCache(
        geohash: String,
        neighbors: List<String>,
        options: MarkerQueryOptions
    ): List<Marker>?
    
    /**
     * 캐시에 마커 데이터 저장
     * 
     * @param geohash 기준 지역 geohash
     * @param neighbors 이웃 지역 geohash 목록
     * @param options 쿼리 옵션
     * @param markers 캐시할 마커 목록
     */
    suspend fun cacheMarkers(
        geohash: String,
        neighbors: List<String>,
        options: MarkerQueryOptions,
        markers: List<Marker>
    )
    
    /**
     * 특정 지역의 캐시 데이터 무효화
     * 
     * @param geohash 무효화할 지역 geohash
     */
    suspend fun invalidateArea(geohash: String)
    
    /**
     * 모든 캐시 데이터 초기화
     */
    suspend fun clearAll()
    
    /**
     * 캐시 통계 정보 조회
     * 
     * @return 캐시 상태에 대한 통계 정보
     */
    suspend fun getCacheStats(): Map<String, Any>
    
    /**
     * 캐시 어댑터 이름 반환
     * 
     * @return 어댑터 식별자
     */
    fun getName(): String
} 