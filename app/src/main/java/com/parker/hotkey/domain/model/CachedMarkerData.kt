package com.parker.hotkey.domain.model

/**
 * 캐시된 마커 데이터를 담는 클래스
 */
data class CachedMarkerData(
    val markers: List<Marker>,           // 마커 목록
    val timestamp: Long,                 // 캐싱 시간
    val source: CacheSource,             // 데이터 소스 (DB, 서버 등)
    val metaData: Map<String, Any> = emptyMap()  // 추가 메타데이터
) {
    // 캐시가 유효한지 확인
    fun isValid(maxAgeMs: Long): Boolean {
        return System.currentTimeMillis() - timestamp < maxAgeMs
    }
    
    // 마커 데이터에 대한 간단한 통계 제공
    fun getStatistics(): String {
        val markerCount = markers.size
        val ageSeconds = (System.currentTimeMillis() - timestamp) / 1000
        return "마커 ${markerCount}개, ${ageSeconds}초 전 캐싱됨, 소스: $source"
    }
    
    // 캐시 데이터 갱신
    fun update(newMarkers: List<Marker>): CachedMarkerData {
        return copy(
            markers = newMarkers,
            timestamp = System.currentTimeMillis()
        )
    }
    
    // 데이터 소스 열거형
    enum class CacheSource {
        DATABASE, SERVER, MEMORY
    }
} 