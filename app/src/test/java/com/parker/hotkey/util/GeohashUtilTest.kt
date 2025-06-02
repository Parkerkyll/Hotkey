package com.parker.hotkey.util

import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * GeoHashUtil 클래스의 geohash6 기능 테스트
 * 
 * 이 테스트는 geohash6 인코딩의 정확성과 이웃 계산, 영역 크기 등을 검증합니다.
 * geohash6는 약 1.2km 영역을 커버하는 정밀도로, 마커 영역 계산에 사용됩니다.
 */
class GeohashUtilTest {
    
    /*
    @Test
    fun `좌표로 geohash 생성이 정상적으로 되어야 한다`() {
        // Given
        val lat = 37.5666102
        val lon = 126.9783881
        
        // When
        val geohash = GeoHashUtil.encode(lat, lon)
        
        // Then
        assertEquals(7, geohash.length) // 기본 정밀도 7
        assertTrue(geohash.matches(Regex("[0-9a-z]+"))) // BASE32 문자만 포함
    }
    */
    
    /**
     * geohash6 인코딩 정확성 테스트
     * 정밀도가 정확히 6자리여야 하고, 예상 출력값과 일치해야 합니다.
     */
    @Test
    fun `geohash6 정밀도로 인코딩이 정확하게 되어야 한다`() {
        // Given
        val lat = 37.5666102
        val lon = 126.9783881
        val precision = 6
        
        // When
        val geohash = GeoHashUtil.encode(lat, lon, precision)
        
        // Then
        assertEquals(precision, geohash.length)
        assertEquals("wydm9g", geohash) // 예상되는 geohash6 값
        assertTrue(geohash.matches(Regex("[0-9a-z]+")))
    }
    
    /*
    @Test
    fun `LatLng로 geohash 생성이 정상적으로 되어야 한다`() {
        // Given
        val position = LatLng(37.5666102, 126.9783881)
        
        // When
        val geohash = GeoHashUtil.encode(position)
        
        // Then
        assertEquals(7, geohash.length)
        assertTrue(geohash.matches(Regex("[0-9a-z]+")))
    }
    
    @Test
    fun `geohash로 영역 계산이 정상적으로 되어야 한다`() {
        // Given
        val position = LatLng(37.5666102, 126.9783881)
        val geohash = GeoHashUtil.encode(position)
        
        // When
        val bounds = GeoHashUtil.decodeBounds(geohash)
        
        // Then
        assertTrue(bounds.contains(position))
    }
    */
    
    /**
     * geohash6 영역 크기 테스트
     * geohash6는 약 1.2km 영역을 커버해야 합니다.
     */
    @Test
    fun `geohash6 영역 크기가 약 1km 내외여야 한다`() {
        // Given
        val geohash6 = "wydm9g" // 서울 시청 부근
        
        // When
        val bounds = GeoHashUtil.decodeBounds(geohash6)
        
        // Then
        val widthKm = LocationUtil.calculateDistanceInKm(
            bounds.southLatitude, bounds.westLongitude,
            bounds.southLatitude, bounds.eastLongitude
        )
        val heightKm = LocationUtil.calculateDistanceInKm(
            bounds.southLatitude, bounds.westLongitude,
            bounds.northLatitude, bounds.westLongitude
        )
        
        // 대략적으로 1km 내외인지 검증
        println("Geohash6 너비: $widthKm km, 높이: $heightKm km")
        assertTrue(widthKm in 0.5..1.5)
        assertTrue(heightKm in 0.5..1.5)
    }
    
    /**
     * geohash6 이웃 계산 테스트
     * 정확히 8개의 이웃 geohash가 계산되어야 합니다.
     */
    @Test
    fun `이웃한 geohash6들이 정확히 8개여야 한다`() {
        // Given
        val geohash6 = "wydm9g"
        
        // When
        val neighbors = GeoHashUtil.getNeighbors(geohash6)
        
        // Then
        assertEquals(8, neighbors.size) // 8방향
        neighbors.forEach { neighbor ->
            assertEquals(6, neighbor.length) // 정밀도 6 유지
            assertTrue(neighbor.matches(Regex("[0-9a-z]+")))
        }
        
        // 예상되는 이웃 geohash6 (시계 방향으로: 북, 북동, 동, 남동, 남, 남서, 서, 북서)
        val expectedNeighbors = listOf(
            "wydm9u", "wydm9v", "wydm9t", 
            "wydm9s", "wydm9e", "wydm9d", 
            "wydm9f", "wydm9g"
        )
        
        // 모든 이웃 geohash가 정확히 맞는지 확인 (순서는 중요하지 않음)
        assertEquals(expectedNeighbors.toSet(), neighbors.toSet())
    }
    
    /*
    /**
     * geohash6 영역이 서로 겹치지 않고 정확히 구분되는지 테스트
     */
    @Test
    fun `이웃한 geohash6들의 영역이 겹치지 않아야 한다`() {
        // Given
        val geohash6 = "wydm9g"
        val neighbors = GeoHashUtil.getNeighbors(geohash6)
        
        // When
        val centralBounds = GeoHashUtil.decodeBounds(geohash6)
        val neighborBounds = neighbors.map { GeoHashUtil.decodeBounds(it) }
        
        // Then
        // 각 이웃의 경계가 중앙 geohash와 인접하되 겹치지 않는지 확인
        neighborBounds.forEach { bounds ->
            // 약간의 수치 오차를 허용하기 위해 정확한 비교 대신 근사적 확인
            val isOverlapping = bounds.southwest.latitude < centralBounds.northeast.latitude &&
                                bounds.northeast.latitude > centralBounds.southwest.latitude &&
                                bounds.southwest.longitude < centralBounds.northeast.longitude &&
                                bounds.northeast.longitude > centralBounds.southwest.longitude
            
            assertTrue(isOverlapping, "이웃 영역이 중앙 geohash와 완전히 분리되어 있습니다")
        }
    }
    */
} 