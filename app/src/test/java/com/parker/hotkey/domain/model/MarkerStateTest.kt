package com.parker.hotkey.domain.model

import com.naver.maps.geometry.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class MarkerStateTest {
    
    @Test
    fun `마커 생성 시 기본 상태가 TEMPORARY로 설정되는지 확인`() {
        // Given
        val marker = Marker(
            id = "test-id-1",
            userId = "user-1",
            latitude = 37.123,
            longitude = 127.123,
            geohash = "abc123"
        )
        
        // Then
        assertEquals(MarkerState.TEMPORARY, marker.state)
        assertFalse(marker.isPersisted)
        assertEquals(null, marker.lastSyncedAt)
    }
    
    @Test
    fun `markAsPersisted 호출 시 상태가 PERSISTED로 변경되는지 확인`() {
        // Given
        val marker = Marker(
            id = "test-id-1",
            userId = "user-1",
            latitude = 37.123,
            longitude = 127.123,
            geohash = "abc123"
        )
        
        // When
        val persistedMarker = marker.markAsPersisted()
        
        // Then
        assertEquals(MarkerState.PERSISTED, persistedMarker.state)
        assertTrue(persistedMarker.isPersisted)
        assertNotNull(persistedMarker.lastSyncedAt)
    }
    
    @Test
    fun `markAsDeleted 호출 시 상태가 DELETED로 변경되는지 확인`() {
        // Given
        val marker = Marker(
            id = "test-id-1",
            userId = "user-1",
            latitude = 37.123,
            longitude = 127.123,
            geohash = "abc123"
        )
        
        // When
        val deletedMarker = marker.markAsDeleted()
        
        // Then
        assertEquals(MarkerState.DELETED, deletedMarker.state)
        assertFalse(deletedMarker.isPersisted)
    }
    
    @Test
    fun `markAsSynced 호출 시 상태가 PERSISTED로 변경되는지 확인`() {
        // Given
        val marker = Marker(
            id = "test-id-1",
            userId = "user-1",
            latitude = 37.123,
            longitude = 127.123,
            geohash = "abc123"
        )
        
        // When
        val syncedMarker = marker.markAsSynced(100L)
        
        // Then
        assertEquals(MarkerState.PERSISTED, syncedMarker.state)
        assertTrue(syncedMarker.isPersisted)
        assertNotNull(syncedMarker.lastSyncedAt)
    }
} 