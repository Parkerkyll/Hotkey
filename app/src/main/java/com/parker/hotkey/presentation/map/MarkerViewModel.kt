package com.parker.hotkey.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.naver.maps.geometry.LatLng
import com.parker.hotkey.data.mapper.toDomain
import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.repository.MarkerRepository
import com.parker.hotkey.util.GeohashUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class MarkerViewModel @Inject constructor(
    private val markerRepository: MarkerRepository,
    private val workManager: WorkManager
) : ViewModel() {

    companion object {
        private const val MARKER_DELETE_DELAY = 15L  // 메모 없는 마커 자동 삭제 대기 시간 (15초)
    }

    private val _markerState = MutableStateFlow(MarkerState())
    val markerState: StateFlow<MarkerState> = _markerState.asStateFlow()

    fun onMapClicked(coord: LatLng) {
        Timber.d("Map clicked at: lat=${coord.latitude}, lng=${coord.longitude}")
        createMarker(coord)
    }

    private fun createMarker(position: LatLng) {
        viewModelScope.launch {
            try {
                val geohash = GeohashUtil.encode(position)
                val marker = markerRepository.createMarker(
                    latitude = position.latitude,
                    longitude = position.longitude,
                    geohash = geohash
                )
                
                _markerState.value = _markerState.value.copy(
                    lastCreatedMarkerId = marker.id
                )
                
                scheduleMarkerDeletion(marker.id)
                loadMarkersInVisibleRegion(position)
            } catch (e: Exception) {
                Timber.e(e, "마커 생성 중 오류 발생")
                _markerState.value = _markerState.value.copy(error = "마커 생성 중 오류가 발생했습니다.")
            }
        }
    }

    private fun scheduleMarkerDeletion(markerId: String) {
        workManager.cancelAllWorkByTag(markerId)

        val deleteWorkRequest = OneTimeWorkRequestBuilder<EmptyMarkerDeleteWorker>()
            .setInputData(workDataOf(EmptyMarkerDeleteWorker.KEY_MARKER_ID to markerId))
            .setInitialDelay(MARKER_DELETE_DELAY, TimeUnit.SECONDS)
            .addTag(markerId)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                10000L,
                TimeUnit.MILLISECONDS
            )
            .build()

        workManager.enqueue(deleteWorkRequest)
        Timber.d("마커 자동 삭제 작업 예약됨: $markerId")
    }

    fun loadMarkersInVisibleRegion(center: LatLng) {
        viewModelScope.launch {
            try {
                _markerState.value = _markerState.value.copy(isLoading = true, lastKnownPosition = center)
                
                val geohash = GeohashUtil.encode(center)
                val markers = markerRepository.getMarkersByGeohash(geohash).first().map { it.toDomain() }
                
                _markerState.value = _markerState.value.copy(
                    markers = markers,
                    isLoading = false
                )
            } catch (e: Exception) {
                Timber.e(e, "마커 로딩 중 오류 발생")
                _markerState.value = _markerState.value.copy(
                    isLoading = false,
                    error = "마커 로딩 중 오류가 발생했습니다."
                )
            }
        }
    }

    fun clearLastCreatedMarkerId() {
        _markerState.value = _markerState.value.copy(
            lastCreatedMarkerId = null
        )
    }

    suspend fun getAllMarkers(): List<Marker> {
        return markerRepository.getAllMarkers().first().map { it.toDomain() }
    }

    suspend fun deleteMarker(markerId: String) {
        markerRepository.deleteMarker(markerId)
        _markerState.value = _markerState.value.copy(
            markers = _markerState.value.markers.filter { it.id != markerId }
        )
    }

    data class MarkerState(
        val markers: List<Marker> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
        val lastCreatedMarkerId: String? = null,
        val lastKnownPosition: LatLng = LatLng(37.5666102, 126.9783881)
    )
} 