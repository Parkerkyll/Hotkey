package com.parker.hotkey.data.cache

import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.repository.MarkerQueryOptions
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 마커 캐시 어댑터 팩토리
 * 요구사항에 따라 적절한 캐시 전략을 생성합니다.
 */
@Singleton
class MarkerCacheAdapterFactory @Inject constructor(
    private val memoryCacheAdapter: MemoryCacheAdapter,
    private val diskCacheAdapter: DiskCacheAdapter
) {
    /**
     * 요청된 전략 유형에 따라 캐시 어댑터 생성
     * 
     * @param strategy 캐시 전략 유형
     * @return 요청된 전략에 맞는 캐시 어댑터
     */
    fun createAdapter(strategy: CacheStrategy): MarkerCacheAdapter {
        return when (strategy) {
            CacheStrategy.MEMORY_ONLY -> memoryCacheAdapter
            CacheStrategy.DISK_ONLY -> diskCacheAdapter
            CacheStrategy.MULTI_LEVEL -> createMultiLevelAdapter()
            CacheStrategy.NONE -> createNoOpAdapter()
        }
    }
    
    /**
     * 다중 레벨 캐시 어댑터 생성 (메모리 + 디스크)
     * 
     * @return 다중 레벨 캐시 어댑터
     */
    private fun createMultiLevelAdapter(): MarkerCacheAdapter {
        return MultiLevelCacheAdapter(
            listOf(memoryCacheAdapter, diskCacheAdapter)
        )
    }
    
    /**
     * 더미 캐시 어댑터 생성 (실제 캐싱 수행 안함)
     * 
     * @return 더미 캐시 어댑터
     */
    private fun createNoOpAdapter(): MarkerCacheAdapter {
        return NoOpCacheAdapter()
    }
    
    /**
     * 캐시 전략 열거형
     */
    enum class CacheStrategy {
        MEMORY_ONLY,  // 메모리 캐시만 사용
        DISK_ONLY,    // 디스크 캐시만 사용
        MULTI_LEVEL,  // 메모리 + 디스크 캐시 사용
        NONE          // 캐싱 안함
    }
}

/**
 * 다중 레벨 캐시 어댑터 (메모리+디스크 등 여러 캐시를 계층적으로 사용)
 */
class MultiLevelCacheAdapter(
    private val adapters: List<MarkerCacheAdapter>
) : MarkerCacheAdapter {
    
    override suspend fun getMarkersFromCache(
        geohash: String, 
        neighbors: List<String>, 
        options: MarkerQueryOptions
    ): List<Marker>? {
        // 첫 번째 캐시에서 찾으면 반환
        for (adapter in adapters) {
            val markers = adapter.getMarkersFromCache(geohash, neighbors, options)
            if (markers != null) {
                Timber.d("다중 레벨 캐시: ${adapter.getName()}에서 히트")
                
                // 첫 번째 캐시가 아닌 다른 캐시에서 히트한 경우, 
                // 첫 번째 캐시에도 데이터를 저장하여 다음 접근 시 빠르게 조회되도록 함
                val firstAdapter = adapters.firstOrNull()
                if (firstAdapter != null && firstAdapter !== adapter) {
                    firstAdapter.cacheMarkers(geohash, neighbors, options, markers)
                }
                
                return markers
            }
        }
        
        Timber.d("다중 레벨 캐시: 모든 레벨에서 미스")
        return null
    }
    
    override suspend fun cacheMarkers(
        geohash: String, 
        neighbors: List<String>, 
        options: MarkerQueryOptions, 
        markers: List<Marker>
    ) {
        // 모든 캐시 레벨에 저장
        for (adapter in adapters) {
            adapter.cacheMarkers(geohash, neighbors, options, markers)
        }
        Timber.d("다중 레벨 캐시: ${markers.size}개 마커를 모든 레벨에 캐싱")
    }
    
    override suspend fun invalidateArea(geohash: String) {
        // 모든 캐시 레벨에서 영역 무효화
        for (adapter in adapters) {
            adapter.invalidateArea(geohash)
        }
        Timber.d("다중 레벨 캐시: $geohash 영역을 모든 레벨에서 무효화")
    }
    
    override suspend fun clearAll() {
        // 모든 캐시 레벨 초기화
        for (adapter in adapters) {
            adapter.clearAll()
        }
        Timber.d("다중 레벨 캐시: 모든 레벨 초기화")
    }
    
    override suspend fun getCacheStats(): Map<String, Any> {
        // 모든 캐시 레벨의 통계 정보 취합
        val result = mutableMapOf<String, Any>()
        adapters.forEach { adapter ->
            result["${adapter.getName()}_stats"] = adapter.getCacheStats()
        }
        return result
    }
    
    override fun getName(): String = "MULTI_LEVEL_CACHE"
}

/**
 * 캐싱 기능이 없는 더미 어댑터 (캐싱을 사용하지 않는 경우)
 */
class NoOpCacheAdapter : MarkerCacheAdapter {
    
    override suspend fun getMarkersFromCache(
        geohash: String, 
        neighbors: List<String>, 
        options: MarkerQueryOptions
    ): List<Marker>? = null
    
    override suspend fun cacheMarkers(
        geohash: String, 
        neighbors: List<String>, 
        options: MarkerQueryOptions, 
        markers: List<Marker>
    ) {
        // 아무 작업도 수행하지 않음
    }
    
    override suspend fun invalidateArea(geohash: String) {
        // 아무 작업도 수행하지 않음
    }
    
    override suspend fun clearAll() {
        // 아무 작업도 수행하지 않음
    }
    
    override suspend fun getCacheStats(): Map<String, Any> {
        return mapOf("info" to "캐싱 사용 안함")
    }
    
    override fun getName(): String = "NO_OP_CACHE"
} 