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
import org.mockito.kotlin.any
import java.util.UUID

@ExperimentalCoroutinesApi
class DiskCacheAdapterTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var adapter: DiskCacheAdapter
    private lateinit var diskCache: MarkerDiskCache

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
        diskCache = mock()
        adapter = DiskCacheAdapter(diskCache)
    }

    @Test
    fun `getMarkersFromCache should return markers from disk cache when available`() = runTest {
        // Given: 디스크 캐시에 데이터가 있는 상황
        whenever(diskCache.getFromCache(any(), any(), any())).thenReturn(testMarkers)

        // When: 캐시에서 데이터를 가져오려고 시도
        val result = adapter.getMarkersFromCache(testGeohash, testNeighbors, testOptions)

        // Then: 마커 목록을 반환해야 함 (캐시 히트)
        assert(result == testMarkers)
        verify(diskCache).getFromCache(testGeohash, testNeighbors, testOptions)
    }

    @Test
    fun `getMarkersFromCache should return null when disk cache misses`() = runTest {
        // Given: 디스크 캐시에 데이터가 없는 상황
        whenever(diskCache.getFromCache(any(), any(), any())).thenReturn(null)

        // When: 캐시에서 데이터를 가져오려고 시도
        val result = adapter.getMarkersFromCache(testGeohash, testNeighbors, testOptions)

        // Then: null을 반환해야 함 (캐시 미스)
        assert(result == null)
        verify(diskCache).getFromCache(testGeohash, testNeighbors, testOptions)
    }

    @Test
    fun `cacheMarkers should save markers to disk cache`() = runTest {
        // When: 캐시에 마커를 저장
        adapter.cacheMarkers(testGeohash, testNeighbors, testOptions, testMarkers)

        // Then: diskCache.saveToCache가 호출되어야 함
        verify(diskCache).saveToCache(testGeohash, testNeighbors, testOptions, testMarkers)
    }

    @Test
    fun `invalidateArea should remove area from disk cache`() = runTest {
        // When: 특정 지역 캐시 무효화
        adapter.invalidateArea(testGeohash)

        // Then: diskCache.invalidateArea가 호출되어야 함
        verify(diskCache).invalidateArea(testGeohash)
    }

    @Test
    fun `clearAll should clear all disk cache entries`() = runTest {
        // When: 모든 캐시 초기화
        adapter.clearAll()

        // Then: diskCache.clearAll이 호출되어야 함
        verify(diskCache).clearAll()
    }

    @Test
    fun `getCacheStats should return stats from disk cache`() = runTest {
        // Given: diskCache가 통계 정보를 반환하는 상황
        val expectedStats = mapOf(
            "entryCount" to 5,
            "totalSizeBytes" to 1024L,
            "hitRate" to 80
        )
        whenever(diskCache.getCacheStats()).thenReturn(expectedStats)

        // When: 캐시 통계 요청
        val result = adapter.getCacheStats()

        // Then: 통계 정보가 포함된 Map을 반환해야 함
        assert(result == expectedStats)
        verify(diskCache).getCacheStats()
    }

    @Test
    fun `getName should return DISK_CACHE`() {
        // When & Then: 이름은 항상 DISK_CACHE여야 함
        assert(adapter.getName() == "DISK_CACHE")
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