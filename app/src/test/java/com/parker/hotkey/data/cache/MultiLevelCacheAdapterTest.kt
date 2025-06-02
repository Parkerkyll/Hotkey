package com.parker.hotkey.data.cache

import com.parker.hotkey.MainCoroutineRule
import com.parker.hotkey.domain.model.LastSync
import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.repository.MarkerQueryOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.never
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import java.util.UUID

@ExperimentalCoroutinesApi
class MultiLevelCacheAdapterTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var memoryAdapter: MarkerCacheAdapter
    private lateinit var diskAdapter: MarkerCacheAdapter
    private lateinit var multiLevelAdapter: MultiLevelCacheAdapter

    private val testGeohash = "wxyz12"
    private val testNeighbors = listOf("wxyz11", "wxyz13")
    private val testOptions = MarkerQueryOptions(zoom = 15.0)
    private val testMarkers = listOf(
        createTestMarker(testGeohash),
        createTestMarker(testGeohash),
        createTestMarker(testNeighbors[0])
    )

    @Before
    fun setup() {
        memoryAdapter = mock()
        diskAdapter = mock()
        
        // 기본 응답 설정
        whenever(memoryAdapter.getName()).thenReturn("MEMORY_CACHE")
        whenever(diskAdapter.getName()).thenReturn("DISK_CACHE")
        
        multiLevelAdapter = MultiLevelCacheAdapter(listOf(memoryAdapter, diskAdapter))
    }

    @Test
    fun `getMarkersFromCache should return markers from memory cache when available`() = runTest {
        // Given: 메모리 캐시에 데이터가 있는 상황
        whenever(memoryAdapter.getMarkersFromCache(any(), any(), any())).thenReturn(testMarkers)

        // When: 캐시에서 데이터를 가져오려고 시도
        val result = multiLevelAdapter.getMarkersFromCache(testGeohash, testNeighbors, testOptions)

        // Then: 메모리 캐시의 결과를 반환하고, 디스크 캐시는 조회하지 않아야 함
        assert(result == testMarkers)
        verify(memoryAdapter).getMarkersFromCache(testGeohash, testNeighbors, testOptions)
        verify(diskAdapter, never()).getMarkersFromCache(any(), any(), any())
    }

    @Test
    fun `getMarkersFromCache should check disk cache when memory cache misses`() = runTest {
        // Given: 메모리 캐시는 미스, 디스크 캐시는 히트인 상황
        whenever(memoryAdapter.getMarkersFromCache(any(), any(), any())).thenReturn(null)
        whenever(diskAdapter.getMarkersFromCache(any(), any(), any())).thenReturn(testMarkers)

        // When: 캐시에서 데이터를 가져오려고 시도
        val result = multiLevelAdapter.getMarkersFromCache(testGeohash, testNeighbors, testOptions)

        // Then: 디스크 캐시의 결과를 반환해야 함
        assert(result == testMarkers)
        verify(memoryAdapter).getMarkersFromCache(testGeohash, testNeighbors, testOptions)
        verify(diskAdapter).getMarkersFromCache(testGeohash, testNeighbors, testOptions)
    }

    @Test
    fun `getMarkersFromCache should populate first level cache on hit in second level`() = runTest {
        // Given: 메모리 캐시는 미스, 디스크 캐시는 히트인 상황
        whenever(memoryAdapter.getMarkersFromCache(any(), any(), any())).thenReturn(null)
        whenever(diskAdapter.getMarkersFromCache(any(), any(), any())).thenReturn(testMarkers)

        // When: 캐시에서 데이터를 가져오려고 시도
        val result = multiLevelAdapter.getMarkersFromCache(testGeohash, testNeighbors, testOptions)

        // Then: 디스크 캐시의 결과를 반환하고, 메모리 캐시에도 저장해야 함
        assert(result == testMarkers)
        verify(memoryAdapter).cacheMarkers(testGeohash, testNeighbors, testOptions, testMarkers)
    }

    @Test
    fun `getMarkersFromCache should return null when all caches miss`() = runTest {
        // Given: 모든 캐시가 미스인 상황
        whenever(memoryAdapter.getMarkersFromCache(any(), any(), any())).thenReturn(null)
        whenever(diskAdapter.getMarkersFromCache(any(), any(), any())).thenReturn(null)

        // When: 캐시에서 데이터를 가져오려고 시도
        val result = multiLevelAdapter.getMarkersFromCache(testGeohash, testNeighbors, testOptions)

        // Then: null을 반환해야 함 (캐시 미스)
        assert(result == null)
        verify(memoryAdapter).getMarkersFromCache(testGeohash, testNeighbors, testOptions)
        verify(diskAdapter).getMarkersFromCache(testGeohash, testNeighbors, testOptions)
    }

    @Test
    fun `cacheMarkers should save markers to all cache levels`() = runTest {
        // When: 캐시에 마커를 저장
        multiLevelAdapter.cacheMarkers(testGeohash, testNeighbors, testOptions, testMarkers)

        // Then: 모든 캐시 레벨에 저장해야 함
        verify(memoryAdapter).cacheMarkers(testGeohash, testNeighbors, testOptions, testMarkers)
        verify(diskAdapter).cacheMarkers(testGeohash, testNeighbors, testOptions, testMarkers)
    }

    @Test
    fun `invalidateArea should remove area from all cache levels`() = runTest {
        // When: 특정 지역 캐시 무효화
        multiLevelAdapter.invalidateArea(testGeohash)

        // Then: 모든 캐시 레벨에서 무효화해야 함
        verify(memoryAdapter).invalidateArea(testGeohash)
        verify(diskAdapter).invalidateArea(testGeohash)
    }

    @Test
    fun `clearAll should clear all cache levels`() = runTest {
        // When: 모든 캐시 초기화
        multiLevelAdapter.clearAll()

        // Then: 모든 캐시 레벨을 초기화해야 함
        verify(memoryAdapter).clearAll()
        verify(diskAdapter).clearAll()
    }

    @Test
    fun `getCacheStats should return stats from all cache levels`() = runTest {
        // Given: 각 캐시가 통계 정보를 반환하는 상황
        val memoryStats = mapOf("hits" to 10, "misses" to 5)
        val diskStats = mapOf("entryCount" to 20, "sizeBytes" to 1024L)
        
        whenever(memoryAdapter.getCacheStats()).thenReturn(memoryStats)
        whenever(diskAdapter.getCacheStats()).thenReturn(diskStats)

        // When: 캐시 통계 요청
        val result = multiLevelAdapter.getCacheStats()

        // Then: 모든 캐시의 통계 정보가 포함된 Map을 반환해야 함
        assert(result["MEMORY_CACHE_stats"] == memoryStats)
        assert(result["DISK_CACHE_stats"] == diskStats)
        verify(memoryAdapter).getCacheStats()
        verify(diskAdapter).getCacheStats()
    }

    @Test
    fun `getName should return MULTI_LEVEL_CACHE`() {
        // When & Then: 이름은 항상 MULTI_LEVEL_CACHE여야 함
        assert(multiLevelAdapter.getName() == "MULTI_LEVEL_CACHE")
    }

    private fun createTestMarker(geohash: String) = Marker(
        id = UUID.randomUUID().toString(),
        userId = "test_user",
        latitude = 37.5,
        longitude = 127.0,
        geohash = geohash,
        lastSync = LastSync.createInitial(),
        modifiedAt = System.currentTimeMillis()
    )
} 