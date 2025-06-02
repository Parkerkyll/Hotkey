/*
package com.parker.hotkey.presentation.map.processor

import com.naver.maps.geometry.LatLng
import com.parker.hotkey.domain.manager.EditModeState
import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.model.Memo
import com.parker.hotkey.presentation.map.MapError
import com.parker.hotkey.presentation.state.MapState
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.mock
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(MockitoJUnitRunner::class)
class MapStateProcessorTest {

    private lateinit var mapStateProcessor: MapStateProcessor

    @Before
    fun setup() {
        mapStateProcessor = MapStateProcessor()
    }

    @Test
    fun `process should return Error state when error is provided`() {
        // Given
        val error = IOException("테스트 오류")
        
        // When
        val result = mapStateProcessor.process(
            markers = emptyList(),
            selectedMarkerId = null,
            memos = emptyList(),
            location = LatLng(0.0, 0.0),
            editModeState = EditModeState(),
            error = error,
            markerToDeleteId = null
        )
        
        // Then
        assertTrue(result is MapState.Error)
        assertEquals(MapError.UNKNOWN.code, (result as MapState.Error).error.code)
    }

    @Test
    fun `process should return Loading state when markers are empty and location is null`() {
        // Given
        val markers = emptyList<Marker>()
        val location = null
        
        // When
        val result = mapStateProcessor.process(
            markers = markers,
            selectedMarkerId = null,
            memos = emptyList(),
            location = location,
            editModeState = EditModeState(),
            error = null,
            markerToDeleteId = null
        )
        
        // Then
        assertTrue(result is MapState.Loading)
    }

    @Test
    fun `process should return Success state with correct data`() {
        // Given
        val markers = listOf(
            Marker(id = "marker1", latitude = 37.5, longitude = 127.0, userId = "user1", createdAt = 123456789L),
            Marker(id = "marker2", latitude = 37.6, longitude = 127.1, userId = "user1", createdAt = 123456790L)
        )
        val selectedMarkerId = "marker1"
        val memos = listOf(
            Memo(id = "memo1", markerId = "marker1", content = "테스트 메모", userId = "user1", createdAt = 123456789L)
        )
        val location = LatLng(37.5, 127.0)
        val editModeState = EditModeState(isEditMode = true, remainingTimeMs = 30000L)
        val markerToDeleteId = "marker2"
        val currentZoomLevel = 16.0
        
        // When
        val result = mapStateProcessor.process(
            markers = markers,
            selectedMarkerId = selectedMarkerId,
            memos = memos,
            location = location,
            editModeState = editModeState,
            error = null,
            markerToDeleteId = markerToDeleteId,
            currentZoomLevel = currentZoomLevel
        )
        
        // Then
        assertTrue(result is MapState.Success)
        with(result as MapState.Success) {
            assertEquals(markers, this.markers)
            assertEquals(markers[0], this.selectedMarker)
            assertEquals(memos, this.selectedMarkerMemos)
            assertEquals(location, this.currentLocation)
            assertEquals(editModeState.isEditMode, this.editMode)
            assertEquals(editModeState.remainingTimeMs, this.editModeTimeRemaining)
            assertEquals(markerToDeleteId, this.markerToDeleteId)
            assertEquals(currentZoomLevel, this.zoomLevel)
        }
    }

    @Test
    fun `process should use default zoom level when currentZoomLevel is null`() {
        // Given
        val markers = listOf(
            Marker(id = "marker1", latitude = 37.5, longitude = 127.0, userId = "user1", createdAt = 123456789L)
        )
        val location = LatLng(37.5, 127.0)
        
        // When
        val result = mapStateProcessor.process(
            markers = markers,
            selectedMarkerId = null,
            memos = emptyList(),
            location = location,
            editModeState = EditModeState(),
            error = null,
            markerToDeleteId = null,
            currentZoomLevel = null
        )
        
        // Then
        assertTrue(result is MapState.Success)
        assertEquals(15.0, (result as MapState.Success).zoomLevel)
    }
}
*/ 