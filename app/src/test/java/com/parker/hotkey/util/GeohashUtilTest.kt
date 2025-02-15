package com.parker.hotkey.util

import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeohashUtilTest {
    
    @Test
    fun `좌표로 geohash 생성이 정상적으로 되어야 한다`() {
        // Given
        val lat = 37.5666102
        val lon = 126.9783881
        
        // When
        val geohash = GeohashUtil.encode(lat, lon)
        
        // Then
        assertEquals(7, geohash.length) // 기본 정밀도 7
        assertTrue(geohash.matches(Regex("[0-9a-z]+"))) // BASE32 문자만 포함
    }
    
    @Test
    fun `LatLng로 geohash 생성이 정상적으로 되어야 한다`() {
        // Given
        val position = LatLng(37.5666102, 126.9783881)
        
        // When
        val geohash = GeohashUtil.encode(position)
        
        // Then
        assertEquals(7, geohash.length)
        assertTrue(geohash.matches(Regex("[0-9a-z]+")))
    }
    
    @Test
    fun `geohash로 영역 계산이 정상적으로 되어야 한다`() {
        // Given
        val position = LatLng(37.5666102, 126.9783881)
        val geohash = GeohashUtil.encode(position)
        
        // When
        val bounds = GeohashUtil.decodeBounds(geohash)
        
        // Then
        assertTrue(bounds.contains(position))
    }
    
    @Test
    fun `영역 내의 모든 geohash가 정상적으로 계산되어야 한다`() {
        // Given
        val bounds = LatLngBounds(
            LatLng(37.56, 126.97),
            LatLng(37.57, 126.98)
        )
        
        // When
        val geohashes = GeohashUtil.getGeohashesInBounds(bounds)
        
        // Then
        assertTrue(geohashes.isNotEmpty())
        geohashes.forEach { geohash ->
            assertEquals(7, geohash.length)
            assertTrue(geohash.matches(Regex("[0-9a-z]+")))
        }
    }
    
    @Test
    fun `이웃한 geohash들이 정상적으로 계산되어야 한다`() {
        // Given
        val position = LatLng(37.5666102, 126.9783881)
        val geohash = GeohashUtil.encode(position)
        
        // When
        val neighbors = GeohashUtil.getNeighbors(geohash)
        
        // Then
        assertEquals(8, neighbors.size) // 8방향
        neighbors.forEach { neighbor ->
            assertEquals(7, neighbor.length)
            assertTrue(neighbor.matches(Regex("[0-9a-z]+")))
        }
    }
} 