package com.parker.hotkey.presentation.map.markers

import android.content.Context
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.overlay.Marker
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MarkerPoolTest {

    private lateinit var markerPool: MarkerPool
    private lateinit var mockContext: Context
    private lateinit var mockMarkerListenerManager: MarkerListenerManager

    @Before
    fun setUp() {
        // 모의 컨텍스트 생성
        mockContext = mock()
        
        // 모의 마커 리스너 매니저 생성
        mockMarkerListenerManager = mock()
        
        // MarkerPool 초기화
        markerPool = MarkerPool(mockContext, mockMarkerListenerManager)
    }

    @Test
    fun `마커를 얻으면 새 마커 생성 또는 재사용된 마커를 반환한다`() {
        // 존재하지 않는 ID로 마커 가져오기 - 새 마커 생성 필요
        val marker1 = markerPool.getMarker("id1")
        
        // 가져온 마커가 null이 아닌지 확인
        assertNotNull(marker1)
        
        // 마커 반환
        markerPool.releaseMarker("id1")
        
        // 동일한 ID로 마커 다시 가져오기 - 재사용 예상
        val marker2 = markerPool.getMarker("id1")
        
        // 가져온 마커가 null이 아닌지 확인
        assertNotNull(marker2)
    }

    @Test
    fun `모든 마커 반환 시 풀을 비운다`() {
        // 여러 마커 얻기
        markerPool.getMarker("id1")
        markerPool.getMarker("id2")
        markerPool.getMarker("id3")
        
        // 모든 마커 반환
        markerPool.releaseAllMarkers()
        
        // 모든 마커가 해제되었는지 확인
        // 활성 마커 수가 0이어야 함
        assertEquals(0, markerPool.getInUseMarkerCount())
    }
    
    @Test
    fun `마커 풀 크기 확인`() {
        // 초기 풀 크기 확인
        assertEquals(0, markerPool.getInUseMarkerCount())
        
        // 마커 3개 가져오기
        markerPool.getMarker("id1")
        markerPool.getMarker("id2")
        markerPool.getMarker("id3")
        
        // 활성 마커 수 확인
        assertEquals(3, markerPool.getInUseMarkerCount())
        
        // 마커 1개 반환
        markerPool.releaseMarker("id2")
        
        // 활성 마커 수 확인
        assertEquals(2, markerPool.getInUseMarkerCount())
    }
} 