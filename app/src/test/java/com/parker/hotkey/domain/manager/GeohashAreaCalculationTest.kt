package com.parker.hotkey.domain.manager

import com.parker.hotkey.domain.model.Location
import com.parker.hotkey.domain.constants.GeohashConstants
import com.parker.hotkey.util.GeoHashUtil
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * geohash6 영역 계산 로직 테스트
 * 
 * 이 테스트는 지도 영역과 관련된 geohash6 계산 로직을 검증합니다.
 * 특히 마커가 영역 밖에 표시되는 문제를 해결하기 위해 영역 계산이 정확한지 확인합니다.
 */
class GeohashAreaCalculationTest {
    
    private lateinit var testLocation: Location
    
    @Before
    fun setup() {
        // 테스트 위치: 서울 시청 부근
        testLocation = Location(37.5666102, 126.9783881)
    }
    
    /**
     * geohash6 영역 계산 정확성 테스트
     * - 정밀도가 정확히 6자리여야 함
     * - MapConstants에 정의된 정밀도와 일치해야 함
     */
    @Test
    fun `geohash6 영역 계산이 정확해야 한다`() {
        // Given: MapConstants에 정의된 정밀도
        val expectedPrecision = GeohashConstants.GEOHASH_PRECISION
        
        // When: 위치에서 geohash 생성
        val geohash = GeoHashUtil.encode(testLocation.latitude, testLocation.longitude, expectedPrecision)
        
        // Then: 정밀도가 정확히 6이어야 함
        assertEquals(expectedPrecision, geohash.length)
        assertEquals(6, geohash.length)
        assertTrue(geohash.matches(Regex("[0-9a-z]+")))
    }
    
    /**
     * 영역 크기 계산 테스트
     * - geohash6 영역이 MapConstants.GEOHASH_RADIUS에 근접해야 함
     */
    @Test
    fun `geohash6 영역 크기가 예상 반경에 근접해야 한다`() {
        // Given: geohash6
        val geohash = GeoHashUtil.encode(testLocation.latitude, testLocation.longitude, 6)
        
        // When: 영역 계산
        val bounds = GeoHashUtil.decodeBounds(geohash)
        
        // 영역의 대략적인 너비와 높이 계산 (km)
        val widthKm = calculateDistance(
            bounds.southLatitude, bounds.westLongitude,
            bounds.southLatitude, bounds.eastLongitude
        )
        val heightKm = calculateDistance(
            bounds.southLatitude, bounds.westLongitude,
            bounds.northLatitude, bounds.westLongitude
        )
        
        // 대각선 거리로 근사적인 반경 계산
        val diagonalKm = calculateDistance(
            bounds.southLatitude, bounds.westLongitude,
            bounds.northLatitude, bounds.eastLongitude
        )
        val approximateRadius = diagonalKm / 2
        
        // Then: 근사적인 반경이 MapConstants.GEOHASH_RADIUS에 근접하는지 검증
        // 1.2km를 미터로 변환해서 비교
        val expectedRadiusKm = GeohashConstants.GEOHASH_RADIUS_M / 1000.0
        
        println("Geohash6 영역 정보:")
        println("- 너비: $widthKm km")
        println("- 높이: $heightKm km")
        println("- 대각선: $diagonalKm km")
        println("- 근사 반경: $approximateRadius km")
        println("- 기대 반경: $expectedRadiusKm km")
        
        // 오차 범위를 30%로 설정 (geohash 영역은 정확한 원이 아님)
        val errorMargin = 0.3
        assertTrue(
            approximateRadius > expectedRadiusKm * (1 - errorMargin) && 
            approximateRadius < expectedRadiusKm * (1 + errorMargin),
            "geohash6 반경($approximateRadius km)이 예상 반경($expectedRadiusKm km)과 크게 다릅니다"
        )
    }
    
    /**
     * 두 지점 사이의 거리를 킬로미터 단위로 계산 (Haversine 공식)
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // 지구 반경 (km)
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        
        return r * c
    }
} 