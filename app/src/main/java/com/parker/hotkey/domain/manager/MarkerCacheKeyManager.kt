package com.parker.hotkey.domain.manager

import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * 마커 캐싱을 위한 키 생성 및 관리 유틸리티
 */
@Singleton
class MarkerCacheKeyManager @Inject constructor() {
    
    /**
     * geohash와 이웃 지역 목록, 줌 레벨을 기반으로 캐시 키 생성
     * @return 고유한 캐시 키 문자열
     */
    fun createCacheKey(geohash: String, neighbors: List<String>, zoom: Double): String {
        // 1. 정렬된 이웃 목록 (순서가 달라도 같은 키 생성)
        val sortedNeighbors = neighbors.sorted()
        
        // 2. 줌 레벨 범주화 (유사한 줌 레벨은 같은 캐시 사용)
        val zoomCategory = categorizeZoom(zoom)
        
        // 3. 최종 키 생성
        return "${geohash}_${sortedNeighbors.joinToString("_")}_$zoomCategory"
    }
    
    /**
     * 줌 레벨 범주화 - 유사한 줌 레벨을 하나의 카테고리로 묶음
     * (예: 15.1, 15.2, 15.3 → 모두 15로 취급)
     */
    private fun categorizeZoom(zoom: Double): Int {
        return zoom.toInt()
    }
    
    /**
     * 특정 geohash가 포함된 모든 캐시 키 찾기
     * @return 영향 받는 캐시 키 목록
     */
    fun findAffectedKeys(
        geohash: String, 
        allKeys: Collection<String>
    ): List<String> {
        return allKeys.filter { key ->
            key.startsWith("${geohash}_") || key.contains("_${geohash}_")
        }
    }
    
    /**
     * 캐시 키로부터 geohash 정보 추출
     */
    fun extractGeohashFromKey(key: String): String? {
        return key.split("_").firstOrNull()
    }
} 