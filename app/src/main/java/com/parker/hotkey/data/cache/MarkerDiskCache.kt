package com.parker.hotkey.data.cache

import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.repository.MarkerQueryOptions

/**
 * 마커 데이터를 위한 디스크 캐시 인터페이스
 * 파일 시스템 기반 캐싱을 통해 애플리케이션 재시작 간에도 데이터 보존
 */
interface MarkerDiskCache {
    /**
     * 디스크 캐시에서 마커 데이터 조회
     * 
     * @param geohash 기준 지역 geohash
     * @param neighbors 이웃 지역 geohash 목록
     * @param options 쿼리 옵션
     * @return 캐시된 마커 목록 또는 null (캐시 미스)
     */
    suspend fun getFromCache(
        geohash: String,
        neighbors: List<String>,
        options: MarkerQueryOptions
    ): List<Marker>?
    
    /**
     * 디스크 캐시에 마커 데이터 저장
     * 
     * @param geohash 기준 지역 geohash
     * @param neighbors 이웃 지역 geohash 목록
     * @param options 쿼리 옵션
     * @param markers 캐시할 마커 목록
     */
    suspend fun saveToCache(
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
     * 캐시 엔트리 유효성 검사
     * 
     * @param geohash 기준 지역 geohash
     * @param neighbors 이웃 지역 geohash 목록
     * @param options 쿼리 옵션
     * @return 캐시가 유효한지 여부
     */
    suspend fun isValid(
        geohash: String,
        neighbors: List<String>,
        options: MarkerQueryOptions
    ): Boolean
    
    /**
     * 캐시 통계 정보 조회
     * 
     * @return 디스크 캐시 상태에 대한 통계 정보
     */
    suspend fun getCacheStats(): Map<String, Any>
} 