package com.parker.hotkey.data.cache

import android.content.Context
import com.parker.hotkey.domain.manager.MarkerCacheKeyManager
import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.repository.MarkerQueryOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 마커 데이터를 위한 디스크 캐시 구현체
 * 애플리케이션 내부 저장소를 활용하여 마커 데이터를 JSON 형태로 저장
 */
@Singleton
class MarkerDiskCacheImpl @Inject constructor(
    private val context: Context,
    private val keyManager: MarkerCacheKeyManager,
    private val json: Json
) : MarkerDiskCache {
    
    // 캐시 기본 설정
    companion object {
        const val CACHE_DIR_NAME = "marker_cache"
        const val DEFAULT_CACHE_TTL = 24 * 60 * 60 * 1000L // 24시간
        const val MAX_CACHE_SIZE = 50 // 최대 캐시 항목 수
    }
    
    // 캐시 통계
    private val cacheHits = AtomicInteger(0)
    private val cacheMisses = AtomicInteger(0)
    
    // 캐시 접근 빈도 (LFU용)
    private val accessFrequency = ConcurrentHashMap<String, Int>()
    
    // 캐시 디렉토리 초기화
    private val cacheDir: File by lazy {
        File(context.cacheDir, CACHE_DIR_NAME).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    /**
     * 캐시 키로 파일명 생성
     */
    private fun getCacheFile(cacheKey: String): File {
        return File(cacheDir, "${cacheKey.hashCode()}.json")
    }
    
    /**
     * 디스크 캐시에서 마커 데이터 조회
     */
    override suspend fun getFromCache(
        geohash: String,
        neighbors: List<String>,
        options: MarkerQueryOptions
    ): List<Marker>? = withContext(Dispatchers.IO) {
        try {
            val cacheKey = keyManager.createCacheKey(geohash, neighbors, options.zoom ?: 15.0)
            val cacheFile = getCacheFile(cacheKey)
            
            if (!cacheFile.exists() || !isEntryValid(cacheFile)) {
                cacheMisses.incrementAndGet()
                Timber.d("디스크 캐시 미스: $cacheKey")
                return@withContext null
            }
            
            // 액세스 카운터 증가 (LFU)
            incrementAccessCount(cacheKey)
            
            // 캐시 파일 읽기
            val cachedData = cacheFile.readText()
            val cachedMarkerData = json.decodeFromString<CachedMarkerDataDto>(cachedData)
            
            // 유효성 검사
            if (!isCacheDataValid(cachedMarkerData)) {
                cacheMisses.incrementAndGet()
                Timber.d("디스크 캐시 만료: $cacheKey (${cachedMarkerData.timestamp})")
                cacheFile.delete()
                return@withContext null
            }
            
            cacheHits.incrementAndGet()
            Timber.d("디스크 캐시 히트: $cacheKey (${cachedMarkerData.markers.size}개 마커)")
            
            // DTO에서 도메인 모델로 변환
            cachedMarkerData.markers.map { it.toDomain() }
        } catch (e: Exception) {
            Timber.e(e, "디스크 캐시 조회 중 오류 발생")
            null
        }
    }
    
    /**
     * 디스크 캐시에 마커 데이터 저장
     */
    override suspend fun saveToCache(
        geohash: String,
        neighbors: List<String>,
        options: MarkerQueryOptions,
        markers: List<Marker>
    ) = withContext(Dispatchers.IO) {
        try {
            // 캐시가 가득찬 경우 정리
            ensureCacheCapacity()
            
            val cacheKey = keyManager.createCacheKey(geohash, neighbors, options.zoom ?: 15.0)
            val cacheFile = getCacheFile(cacheKey)
            
            // 저장할 데이터 생성
            val cachedData = CachedMarkerDataDto(
                markers = markers.map { MarkerDTO.fromDomain(it) },
                timestamp = System.currentTimeMillis(),
                source = "DISK"
            )
            
            // JSON 직렬화 및 파일 저장
            val jsonData = json.encodeToString(cachedData)
            cacheFile.writeText(jsonData)
            
            // 액세스 카운트 초기화
            accessFrequency[cacheKey] = 1
            
            Timber.d("디스크 캐시 저장 완료: $cacheKey (${markers.size}개 마커)")
        } catch (e: Exception) {
            Timber.e(e, "디스크 캐시 저장 중 오류 발생")
        }
    }
    
    /**
     * 특정 지역의 캐시 데이터 무효화
     */
    override suspend fun invalidateArea(geohash: String) = withContext(Dispatchers.IO) {
        try {
            val filesToDelete = cacheDir.listFiles { file ->
                val cacheKey = file.nameWithoutExtension
                keyManager.findAffectedKeys(geohash, listOf(cacheKey)).isNotEmpty()
            } ?: emptyArray()
            
            var deletedCount = 0
            filesToDelete.forEach { file ->
                if (file.delete()) {
                    deletedCount++
                }
            }
            
            Timber.d("디스크 캐시 영역 무효화: geohash=$geohash, 삭제된 캐시=$deletedCount")
        } catch (e: Exception) {
            Timber.e(e, "디스크 캐시 영역 무효화 중 오류 발생")
        }
    }
    
    /**
     * 모든 캐시 데이터 초기화
     */
    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        try {
            val files = cacheDir.listFiles() ?: emptyArray()
            var deletedCount = 0
            
            files.forEach { file ->
                if (file.delete()) {
                    deletedCount++
                }
            }
            
            // 액세스 카운트 초기화
            accessFrequency.clear()
            
            Timber.d("디스크 캐시 전체 초기화: $deletedCount 개 항목 삭제됨")
        } catch (e: Exception) {
            Timber.e(e, "디스크 캐시 전체 초기화 중 오류 발생")
        }
    }
    
    /**
     * 캐시 엔트리 유효성 검사
     */
    override suspend fun isValid(
        geohash: String,
        neighbors: List<String>,
        options: MarkerQueryOptions
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val cacheKey = keyManager.createCacheKey(geohash, neighbors, options.zoom ?: 15.0)
            val cacheFile = getCacheFile(cacheKey)
            
            if (!cacheFile.exists()) {
                return@withContext false
            }
            
            isEntryValid(cacheFile)
        } catch (e: Exception) {
            Timber.e(e, "캐시 유효성 검사 중 오류 발생")
            false
        }
    }
    
    /**
     * 캐시 파일 유효성 검사
     */
    private fun isEntryValid(cacheFile: File): Boolean {
        if (!cacheFile.exists()) return false
        
        try {
            val cachedData = cacheFile.readText()
            val cachedMarkerData = json.decodeFromString<CachedMarkerDataDto>(cachedData)
            return isCacheDataValid(cachedMarkerData)
        } catch (e: Exception) {
            Timber.e(e, "캐시 파일 유효성 검사 중 오류 발생")
            return false
        }
    }
    
    /**
     * 캐시 데이터 유효성 검사 (TTL 기반)
     */
    private fun isCacheDataValid(cachedData: CachedMarkerDataDto): Boolean {
        val currentTime = System.currentTimeMillis()
        return currentTime - cachedData.timestamp < DEFAULT_CACHE_TTL
    }
    
    /**
     * 캐시 통계 정보 조회
     */
    override suspend fun getCacheStats(): Map<String, Any> = withContext(Dispatchers.IO) {
        try {
            val files = cacheDir.listFiles() ?: emptyArray()
            val totalSize = files.sumOf { it.length() }
            val hitRate = if (cacheHits.get() + cacheMisses.get() > 0) {
                cacheHits.get() * 100 / (cacheHits.get() + cacheMisses.get())
            } else 0
            
            mapOf(
                "entryCount" to files.size,
                "totalSizeBytes" to totalSize,
                "hitRate" to hitRate,
                "hits" to cacheHits.get(),
                "misses" to cacheMisses.get()
            )
        } catch (e: Exception) {
            Timber.e(e, "캐시 통계 정보 조회 중 오류 발생")
            emptyMap<String, Any>()
        }
    }
    
    /**
     * 액세스 카운트 증가 (LFU 구현용)
     */
    private fun incrementAccessCount(cacheKey: String) {
        accessFrequency.compute(cacheKey) { _, count ->
            (count ?: 0) + 1
        }
    }
    
    /**
     * 캐시 용량 관리 (LFU 전략 적용)
     * 접근 빈도가 낮은 항목부터 제거
     */
    private suspend fun ensureCacheCapacity() = withContext(Dispatchers.IO) {
        try {
            val files = cacheDir.listFiles() ?: emptyArray()
            if (files.size < MAX_CACHE_SIZE) return@withContext
            
            // 접근 빈도 기준으로 정렬하여 적게 액세스된 항목부터 삭제
            val sortedFiles = files.sortedBy { file ->
                accessFrequency[file.nameWithoutExtension] ?: 0
            }
            
            // 20%의 캐시 항목 제거
            val removeCount = (MAX_CACHE_SIZE * 0.2).toInt().coerceAtLeast(1)
            sortedFiles.take(removeCount).forEach { file ->
                val key = file.nameWithoutExtension
                file.delete()
                accessFrequency.remove(key)
                Timber.d("LFU 캐시 정리: $key 제거됨 (접근 빈도: ${accessFrequency[key] ?: 0})")
            }
        } catch (e: Exception) {
            Timber.e(e, "캐시 용량 관리 중 오류 발생")
        }
    }
    
    /**
     * 캐시된 마커 데이터 DTO (직렬화용)
     */
    @kotlinx.serialization.Serializable
    data class CachedMarkerDataDto(
        val markers: List<MarkerDTO>,
        val timestamp: Long,
        val source: String
    )
    
    /**
     * 직렬화를 위한 Marker DTO
     */
    @kotlinx.serialization.Serializable
    data class MarkerDTO(
        val id: String,
        val userId: String,
        val modifiedAt: Long,
        val latitude: Double,
        val longitude: Double,
        val geohash: String,
        val memos: List<String> = emptyList() // 간소화된 메모 표현
    ) {
        // Marker 도메인 모델로 변환
        fun toDomain(): Marker {
            return Marker(
                id = id,
                userId = userId,
                modifiedAt = modifiedAt,
                latitude = latitude,
                longitude = longitude,
                geohash = geohash,
                position = com.naver.maps.geometry.LatLng(latitude, longitude),
                memos = emptyList() // 필요한 경우 메모 변환 로직 추가
            )
        }
        
        companion object {
            // Marker 도메인 모델에서 DTO 생성
            fun fromDomain(marker: Marker): MarkerDTO {
                return MarkerDTO(
                    id = marker.id,
                    userId = marker.userId,
                    modifiedAt = marker.modifiedAt,
                    latitude = marker.latitude,
                    longitude = marker.longitude,
                    geohash = marker.geohash,
                    memos = marker.memos.map { it.id } // 메모 ID만 저장
                )
            }
        }
    }
} 