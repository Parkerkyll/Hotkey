package com.parker.hotkey.data.cache

import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.repository.MarkerQueryOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 디스크 캐시 어댑터 - 디스크 캐시를 MarkerCacheAdapter 형태로 래핑합니다.
 */
@Singleton
class DiskCacheAdapter @Inject constructor(
    private val diskCache: MarkerDiskCache
) : MarkerCacheAdapter {
    
    override suspend fun getMarkersFromCache(
        geohash: String, 
        neighbors: List<String>, 
        options: MarkerQueryOptions
    ): List<Marker>? = withContext(Dispatchers.IO) {
        val markers = diskCache.getFromCache(geohash, neighbors, options)
        if (markers != null) {
            Timber.d("디스크 캐시 어댑터: 캐시 히트 - ${markers.size}개 마커")
        } else {
            Timber.d("디스크 캐시 어댑터: 캐시 미스")
        }
        markers
    }
    
    override suspend fun cacheMarkers(
        geohash: String, 
        neighbors: List<String>, 
        options: MarkerQueryOptions, 
        markers: List<Marker>
    ) = withContext(Dispatchers.IO) {
        diskCache.saveToCache(geohash, neighbors, options, markers)
        Timber.d("디스크 캐시 어댑터: ${markers.size}개 마커 캐싱")
    }
    
    override suspend fun invalidateArea(geohash: String) = withContext(Dispatchers.IO) {
        diskCache.invalidateArea(geohash)
        Timber.d("디스크 캐시 어댑터: $geohash 영역 무효화")
    }
    
    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        diskCache.clearAll()
        Timber.d("디스크 캐시 어댑터: 전체 캐시 초기화")
    }
    
    override suspend fun getCacheStats(): Map<String, Any> = withContext(Dispatchers.IO) {
        diskCache.getCacheStats()
    }
    
    override fun getName(): String = "DISK_CACHE"
} 