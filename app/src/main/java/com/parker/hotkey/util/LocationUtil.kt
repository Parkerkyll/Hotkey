package com.parker.hotkey.util

import com.naver.maps.geometry.LatLng
import kotlin.math.*

/**
 * 위치 관련 유틸리티 함수 모음
 */
object LocationUtil {
    /**
     * 두 위도/경도 좌표 사이의 거리를 미터 단위로 계산합니다.
     * Haversine 공식을 사용하여 지구의 곡률을 고려한 거리 계산
     * 
     * @param lat1 첫 번째 위치의 위도
     * @param lon1 첫 번째 위치의 경도
     * @param lat2 두 번째 위치의 위도
     * @param lon2 두 번째 위치의 경도
     * @return 두 위치 간의 거리 (미터 단위)
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // 지구의 반경 (미터)

        val lat1Rad = toRadians(lat1)
        val lat2Rad = toRadians(lat2)
        val deltaLat = toRadians(lat2 - lat1)
        val deltaLon = toRadians(lon2 - lon1)

        val a = sin(deltaLat / 2).pow(2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLon / 2).pow(2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    /**
     * 킬로미터 단위로 거리 계산 (테스트 코드용)
     */
    fun calculateDistanceInKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return calculateDistance(lat1, lon1, lat2, lon2) / 1000.0
    }

    /**
     * 두 LatLng 위치 사이의 거리를 미터 단위로 계산합니다.
     * 
     * @param first 첫 번째 위치
     * @param second 두 번째 위치
     * @return 두 위치 간의 거리 (미터 단위)
     */
    fun calculateDistance(first: LatLng, second: LatLng): Double {
        return calculateDistance(first.latitude, first.longitude, second.latitude, second.longitude)
    }

    /**
     * 도(degree)를 라디안(radian)으로 변환하는 함수
     */
    private fun toRadians(degrees: Double): Double = degrees * PI / 180.0
}

/**
 * 두 LatLng 위치 사이의 거리를 미터 단위로 계산합니다.
 * Haversine 공식을 사용하여 지구의 곡률을 고려한 거리 계산
 * 
 * @param other 거리를 계산할 다른 위치
 * @return 두 위치 간의 거리 (미터 단위)
 */
fun LatLng.calculateDistanceTo(other: LatLng): Double {
    return LocationUtil.calculateDistance(this, other)
} 