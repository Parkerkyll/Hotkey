package com.parker.hotkey.data.repository

import com.parker.hotkey.data.cache.MarkerCacheAdapter
import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.repository.MarkerQueryOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import com.parker.hotkey.di.DefaultCacheAdapter
import javax.inject.Named
import java.util.concurrent.atomic.AtomicLong

/**
 * 마커 레포지토리를 위한 캐시 어댑터
 * 캐시 시스템과 레포지토리 계층 간의 통합을 담당
 */
@Singleton
class MarkerRepositoryCacheAdapter @Inject constructor(
    @DefaultCacheAdapter private val cacheAdapter: MarkerCacheAdapter
) {
    private val lastCacheRefreshTime = AtomicLong(0L)
    private val CACHE_REFRESH_INTERVAL = 5 * 60 * 1000L // 5분
    
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
    ): List<Marker>? = withContext(Dispatchers.IO) {
        try {
            val markers = cacheAdapter.getMarkersFromCache(geohash, neighbors, options)
            
            if (markers != null) {
                Timber.d("레포지토리 캐시 어댑터: 캐시 히트 - ${markers.size}개 마커")
            } else {
                Timber.d("레포지토리 캐시 어댑터: 캐시 미스")
            }
            
            markers
        } catch (e: Exception) {
            Timber.e(e, "캐시에서 마커 조회 중 오류 발생")
            null
        }
    }
    
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
    ) = withContext(Dispatchers.IO) {
        try {
            cacheAdapter.cacheMarkers(geohash, neighbors, options, markers)
            lastCacheRefreshTime.set(System.currentTimeMillis())
            Timber.d("레포지토리 캐시 어댑터: ${markers.size}개 마커 캐싱")
        } catch (e: Exception) {
            Timber.e(e, "마커 캐싱 중 오류 발생")
        }
    }
    
    /**
     * 캐시 갱신 필요 여부 확인
     * 마지막 갱신 시간을 기준으로 일정 간격을 초과하면 true 반환
     * 
     * @return 캐시 갱신 필요 여부
     */
    fun needsCacheRefresh(): Boolean {
        val now = System.currentTimeMillis()
        val lastRefresh = lastCacheRefreshTime.get()
        return now - lastRefresh > CACHE_REFRESH_INTERVAL
    }
    
    /**
     * 특정 지역의 캐시 무효화
     * 지역 데이터가 변경되었을 때 호출
     * 
     * @param geohash 기준 지역 geohash
     */
    suspend fun invalidateArea(geohash: String) = withContext(Dispatchers.IO) {
        try {
            cacheAdapter.invalidateArea(geohash)
            Timber.d("레포지토리 캐시 어댑터: $geohash 영역 무효화")
        } catch (e: Exception) {
            Timber.e(e, "캐시 영역 무효화 중 오류 발생")
        }
    }
    
    /**
     * 모든 캐시 초기화
     */
    suspend fun clearAllCache() = withContext(Dispatchers.IO) {
        try {
            cacheAdapter.clearAll()
            lastCacheRefreshTime.set(0L)
            Timber.d("레포지토리 캐시 어댑터: 모든 캐시 초기화")
        } catch (e: Exception) {
            Timber.e(e, "모든 캐시 초기화 중 오류 발생")
        }
    }
    
    /**
     * 캐시 통계 정보 조회
     * 
     * @return 캐시 통계 정보
     */
    suspend fun getCacheStats(): Map<String, Any> = withContext(Dispatchers.IO) {
        try {
            val basicStats = cacheAdapter.getCacheStats()
            val lastRefreshTime = lastCacheRefreshTime.get()
            
            // 기본 통계에 마지막 갱신 시간 등 추가 정보 포함
            val enhancedStats = basicStats.toMutableMap().apply {
                this["lastRefreshTime"] = lastRefreshTime
                this["sinceLastRefreshMs"] = System.currentTimeMillis() - lastRefreshTime
                this["needsRefresh"] = needsCacheRefresh()
                this["adapterType"] = cacheAdapter.getName()
            }
            
            enhancedStats
        } catch (e: Exception) {
            Timber.e(e, "캐시 통계 조회 중 오류 발생")
            mapOf("error" to e.message.toString())
        }
    }
} 