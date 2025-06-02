package com.parker.hotkey.domain.model.state

import com.parker.hotkey.domain.model.Marker

/**
 * 마커 관련 상태를 관리하는 데이터 클래스
 * 
 * Phase 5 최적화:
 * - equals/hashCode 최적화
 * - 자주 사용되는 연산의 캐싱 및 메서드화
 * - 불필요한 객체 생성 방지
 */
data class MarkerState(
    val markers: List<Marker> = emptyList(),
    override val selectedId: String? = null,
    val temporaryMarkers: Set<String> = emptySet(),
    override val isLoading: Boolean = false,
    override val error: String? = null
) : BaseState, SelectableState {
    
    /**
     * 캐시 키 계산 (상태 변화 감지에 사용)
     */
    private val cacheKey: Int by lazy {
        // 캐시 키는 상대적으로 변화가 적은 필드만 사용하여 계산
        var result = selectedId?.hashCode() ?: 0
        result = 31 * result + isLoading.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        result
    }
    
    /**
     * 기본 equals 구현 최적화
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MarkerState) return false
        
        // 캐시 키 비교로 빠른 불일치 감지
        if (cacheKey != other.cacheKey) return false
        
        // 컬렉션 비교는 비용이 크므로 마지막에 수행
        if (markers != other.markers) return false
        if (temporaryMarkers != other.temporaryMarkers) return false
        
        return true
    }
    
    /**
     * 기본 hashCode 구현 최적화
     */
    override fun hashCode(): Int {
        var result = cacheKey
        result = 31 * result + markers.hashCode()
        result = 31 * result + temporaryMarkers.hashCode()
        return result
    }
    
    /**
     * 마커 ID로 마커를 찾습니다.
     * 자주 사용되는 연산이므로 메서드로 제공합니다.
     */
    fun findMarkerById(markerId: String): Marker? {
        return markers.find { it.id == markerId }
    }
    
    /**
     * 특정 지역의 마커 목록을 반환합니다.
     * 자주 사용되는 연산이므로 메서드로 제공합니다.
     * 
     * @param geohash6 GeoHash6 지역 코드
     * @return 해당 지역의 마커 목록
     */
    fun getMarkersByGeohash6(geohash6: String): List<Marker> {
        return markers.filter { it.geohash == geohash6 }
    }
    
    /**
     * 임시 마커인지 확인합니다.
     * 
     * @param markerId 마커 ID
     * @return 임시 마커 여부
     */
    fun isTemporaryMarker(markerId: String): Boolean {
        return temporaryMarkers.contains(markerId)
    }
    
    /**
     * 마커가 비어있는지 확인합니다.
     */
    val isEmpty: Boolean
        get() = markers.isEmpty()
    
    /**
     * 선택된 마커를 반환합니다.
     * 자주 사용되는 연산이므로 프로퍼티로 제공합니다.
     */
    val selectedMarker: Marker?
        get() = selectedId?.let { id -> findMarkerById(id) }
    
    companion object {
        /**
         * 빈 상태 (싱글톤 인스턴스로 제공)
         */
        val EMPTY = MarkerState()
    }
} 