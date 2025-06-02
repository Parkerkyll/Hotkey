package com.parker.hotkey.domain.manager

import com.parker.hotkey.domain.model.CachedMarkerData
import com.parker.hotkey.domain.model.Marker
import timber.log.Timber
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 마커 데이터를 위한 메모리 캐시 관리자
 */
@Singleton
class MarkerMemoryCache @Inject constructor(
    private val keyManager: MarkerCacheKeyManager
) {
    // 캐시 설정
    private val MAX_ENTRIES = 50                     // 최대 캐시 항목 수
    private val CACHE_EXPIRY_TIME = 5 * 60 * 1000L   // 캐시 유효 시간 (5분)
    
    // LRU 캐시 (스레드 안전성 보장)
    private val cache = Collections.synchronizedMap(
        object : LinkedHashMap<String, CachedMarkerData>(MAX_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedMarkerData>): Boolean {
                return size > MAX_ENTRIES
            }
        }
    )
    
    // 캐시 통계
    private val cacheHits = AtomicInteger(0)
    private val cacheMisses = AtomicInteger(0)
    
    /**
     * 캐시에서 마커 데이터 가져오기
     * @return 캐시된 마커 데이터 또는 null (캐시 미스)
     */
    fun get(geohash: String, neighbors: List<String>, zoom: Double): List<Marker>? {
        val key = keyManager.createCacheKey(geohash, neighbors, zoom)
        val cachedData = cache[key]
        
        if (cachedData != null && cachedData.isValid(CACHE_EXPIRY_TIME)) {
            cacheHits.incrementAndGet()
            Timber.d("마커 메모리 캐시 히트: $key (${cachedData.markers.size}개)")
            return cachedData.markers
        }
        
        cacheMisses.incrementAndGet()
        Timber.d("마커 메모리 캐시 미스: $key")
        return null
    }
    
    /**
     * 캐시에 마커 데이터 저장
     */
    fun put(geohash: String, neighbors: List<String>, zoom: Double, markers: List<Marker>) {
        val key = keyManager.createCacheKey(geohash, neighbors, zoom)
        val cachedData = CachedMarkerData(
            markers = markers,
            timestamp = System.currentTimeMillis(),
            source = CachedMarkerData.CacheSource.MEMORY
        )
        
        cache[key] = cachedData
        Timber.d("마커 메모리 캐시 저장: $key (${markers.size}개)")
    }
    
    /**
     * 특정 지역 관련 캐시 항목 무효화
     */
    fun invalidateArea(geohash: String) {
        val keysToRemove = keyManager.findAffectedKeys(geohash, cache.keys)
        keysToRemove.forEach { key ->
            cache.remove(key)
            Timber.d("마커 메모리 캐시 무효화: $key")
        }
    }
    
    /**
     * 모든 캐시 항목 삭제
     */
    fun clearAll() {
        val count = cache.size
        cache.clear()
        Timber.d("마커 메모리 캐시 전체 삭제: ${count}개 항목")
    }
    
    /**
     * 캐시 통계 반환
     */
    fun getStats(): String {
        val hitRate = if (cacheHits.get() + cacheMisses.get() > 0) {
            cacheHits.get() * 100 / (cacheHits.get() + cacheMisses.get())
        } else 0
        
        return "캐시 항목: ${cache.size}개, 적중률: ${hitRate}% (${cacheHits.get()}/${cacheHits.get() + cacheMisses.get()})"
    }
} 