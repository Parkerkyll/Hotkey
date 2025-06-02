package com.parker.hotkey.presentation.map

/*
import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.usecase.marker.CreateMarkerUseCase
import com.parker.hotkey.domain.usecase.marker.DeleteMarkerWithValidationUseCase
import com.parker.hotkey.domain.usecase.marker.GetMarkersByGeohashUseCase
import com.parker.hotkey.domain.usecase.memo.CreateMemoUseCase
import com.parker.hotkey.domain.usecase.memo.DeleteMemoUseCase
import com.parker.hotkey.domain.usecase.memo.GetMemosByMarkerIdUseCase
import com.parker.hotkey.domain.repository.AuthRepository
import com.parker.hotkey.util.GeohashUtil
import com.parker.hotkey.domain.manager.EditModeManager
import com.parker.hotkey.util.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class MapViewModelTest {
    private lateinit var viewModel: MapViewModel
    private lateinit var getMarkersByGeohashUseCase: GetMarkersByGeohashUseCase
    private lateinit var createMarkerUseCase: CreateMarkerUseCase
    private lateinit var deleteMarkerWithValidationUseCase: DeleteMarkerWithValidationUseCase
    private lateinit var createMemoUseCase: CreateMemoUseCase
    private lateinit var deleteMemoUseCase: DeleteMemoUseCase
    private lateinit var getMemosByMarkerIdUseCase: GetMemosByMarkerIdUseCase
    private lateinit var authRepository: AuthRepository
    private lateinit var editModeManager: EditModeManager
    private lateinit var locationManager: LocationManager
    private val testDispatcher = StandardTestDispatcher()
    
    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getMarkersByGeohashUseCase = mock()
        createMarkerUseCase = mock()
        deleteMarkerWithValidationUseCase = mock()
        createMemoUseCase = mock()
        deleteMemoUseCase = mock()
        getMemosByMarkerIdUseCase = mock()
        authRepository = mock()
        editModeManager = mock()
        locationManager = mock()
        
        viewModel = MapViewModel(
            getMarkersByGeohashUseCase,
            createMarkerUseCase,
            deleteMarkerWithValidationUseCase,
            authRepository,
            editModeManager,
            mock(),
            locationManager,
            createMemoUseCase,
            deleteMemoUseCase,
            getMemosByMarkerIdUseCase
        )
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `updateCameraPosition should update camera position and zoom level`() = runTest {
        // Given
        val latitude = 37.5665
        val longitude = 126.9780
        val zoom = 15f
        
        // When
        viewModel.updateCameraPosition(latitude, longitude, zoom)
        
        // Then
        assertEquals(viewModel.cameraPosition.value, LatLng(latitude, longitude))
        assertEquals(viewModel.zoomLevel.value, zoom)
    }
    
    @Test
    fun `loadMarkers should load markers for current geohash and neighbors`() = runTest {
        // Given
        val latitude = 37.5665
        val longitude = 126.9780
        val geohash = GeohashUtil.encode(latitude, longitude, 6)
        val neighbors = GeohashUtil.getNeighbors(geohash)
        
        val marker = Marker(
            id = "1",
            geohash = geohash,
            latitude = latitude,
            longitude = longitude,
            title = "Test Marker",
            description = "Test Description",
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis()
        )
        
        val markersList = listOf(marker)
        
        whenever(GeohashUtil.encode(latitude, longitude, 6)).thenReturn(geohash)
        whenever(GeohashUtil.getNeighbors(geohash)).thenReturn(neighbors)
        whenever(getMarkersByGeohashUseCase(geohash, neighbors)).thenReturn(Result.success(markersList))
        
        // When
        viewModel.loadMarkers(latitude, longitude)
        
        // Then
        assertNotNull(viewModel.markers.value)
        assertEquals(markersList, viewModel.markers.value)
    }
}
*/ 