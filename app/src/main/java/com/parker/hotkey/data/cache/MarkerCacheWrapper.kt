package com.parker.hotkey.data.cache

import com.parker.hotkey.domain.manager.MarkerMemoryCache
import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.repository.MarkerQueryOptions
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 마커 메모리 캐시를 래핑하여 표준화된 인터페이스를 제공하는 클래스
 * 캐시 어댑터 패턴 적용을 위한 첫 단계
 */
@Singleton
class MarkerCacheWrapper @Inject constructor(
    private val memoryCache: MarkerMemoryCache
) {
    // 캐시 설정 값
    companion object {
        const val DEFAULT_CACHE_TTL = 5 * 60 * 1000L // 5분
    }

    /**
     * 메모리 캐시에서 마커 데이터 조회
     * 
     * @param geohash 기준 지역 geohash
     * @param neighbors 이웃 지역 geohash 목록
     * @param options 쿼리 옵션
     * @return 캐시된 마커 목록 또는 null (캐시 미스)
     */
    fun getFromCache(
        geohash: String,
        neighbors: List<String>,
        options: MarkerQueryOptions
    ): List<Marker>? {
        return try {
            memoryCache.get(geohash, neighbors, options.zoom ?: 15.0)
        } catch (e: Exception) {
            Timber.e(e, "메모리 캐시 조회 중 오류 발생")
            null
        }
    }

    /**
     * 메모리 캐시에 마커 데이터 저장
     * 
     * @param geohash 기준 지역 geohash
     * @param neighbors 이웃 지역 geohash 목록
     * @param options 쿼리 옵션
     * @param markers 캐시할 마커 목록
     */
    fun saveToCache(
        geohash: String,
        neighbors: List<String>,
        options: MarkerQueryOptions,
        markers: List<Marker>
    ) {
        try {
            memoryCache.put(geohash, neighbors, options.zoom ?: 15.0, markers)
        } catch (e: Exception) {
            Timber.e(e, "메모리 캐시 저장 중 오류 발생")
        }
    }

    /**
     * 특정 지역 관련 캐시 데이터 무효화
     * 
     * @param geohash 무효화할 지역 geohash
     */
    fun invalidateArea(geohash: String) {
        try {
            memoryCache.invalidateArea(geohash)
        } catch (e: Exception) {
            Timber.e(e, "캐시 영역 무효화 중 오류 발생")
        }
    }

    /**
     * 전체 캐시 데이터 초기화
     */
    fun clearAll() {
        try {
            memoryCache.clearAll()
        } catch (e: Exception) {
            Timber.e(e, "캐시 전체 초기화 중 오류 발생")
        }
    }

    /**
     * 캐시 통계 정보 조회
     * 
     * @return 캐시 상태 통계 요약
     */
    fun getCacheStats(): String {
        return try {
            memoryCache.getStats()
        } catch (e: Exception) {
            "캐시 통계 조회 중 오류 발생: ${e.message}"
        }
    }
} 