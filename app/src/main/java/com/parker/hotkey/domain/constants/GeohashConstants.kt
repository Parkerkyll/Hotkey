package com.parker.hotkey.domain.constants

/**
 * Geohash 관련 상수들을 관리하는 객체
 *
 * 주요 기능:
 * - Geohash 정밀도 및 크기 관련 상수 관리
 * - Geohash 계산 관련 상수 관리
 */
object GeohashConstants {
    // Geohash 정밀도 및 크기 관련 상수
    /** 
     * Geohash 정밀도 (6)
     * geohash6는 약 1.2km 정도의 정확도를 제공합니다.
     */
    const val GEOHASH_PRECISION = 6
    
    /** Geohash6 반경 (미터) - 약 1.2km */
    const val GEOHASH_RADIUS_M = 1200.0
    
    // Geohash 계산 관련 상수
    /** 지구 반경 (미터) - 거리 계산에 사용 */
    const val EARTH_RADIUS_M = 6371e3
    
    /** Geohash 영역 로딩을 위한 주변 영역 크기 */
    const val GEOHASH_LOAD_RADIUS = 3 // 중심 포함 3*3 영역 로드
} 