package com.parker.hotkey.domain.model

import com.naver.maps.geometry.LatLng
import com.parker.hotkey.util.calculateDistanceTo
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 위치 기반 엔티티를 위한 인터페이스
 */
interface LocationBased {
    val latitude: Double
    val longitude: Double
    val geohash: String
    
    /**
     * 주어진 중심점으로부터 특정 반경 내에 있는지 확인
     */
    fun isWithinRange(center: LatLng, radiusMeters: Double): Boolean {
        return distanceTo(center.latitude, center.longitude) <= radiusMeters
    }
    
    /**
     * 다른 위치까지의 거리 계산 (미터 단위)
     */
    fun distanceTo(other: LocationBased): Double {
        return distanceTo(other.latitude, other.longitude)
    }
    
    /**
     * 특정 위도/경도까지의 거리 계산 (미터 단위)
     * Haversine 공식 사용
     */
    fun distanceTo(targetLat: Double, targetLon: Double): Double {
        val thisLatLng = LatLng(latitude, longitude)
        val targetLatLng = LatLng(targetLat, targetLon)
        return thisLatLng.calculateDistanceTo(targetLatLng)
    }
    
    companion object {
        private const val EARTH_RADIUS = 6371e3 // 지구 반경 (미터)
        
        /**
         * 두 지점 간의 거리를 계산 (미터 단위)
         * calculateDistanceTo 함수를 사용
         */
        fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val source = LatLng(lat1, lon1)
            val target = LatLng(lat2, lon2)
            return source.calculateDistanceTo(target)
        }
        
        /**
         * 두 지점 간의 거리를 계산 (미터 단위)
         */
        fun distanceTo(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            return distanceBetween(lat1, lon1, lat2, lon2)
        }
    }
} 