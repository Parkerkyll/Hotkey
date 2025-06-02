package com.parker.hotkey.presentation.map.markers

import com.parker.hotkey.domain.manager.MarkerManager
import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.model.state.MarkerState
import com.parker.hotkey.presentation.state.MapState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

/**
 * 마커 상태 처리를 담당하는 인터페이스
 */
interface MarkerStateProcessor {
    /**
     * 마커 목록 상태를 UI 상태로 변환
     */
    fun processMarkerState(): Flow<MarkerState>
    
    /**
     * 마커 상태를 기반으로 맵 UI 상태 생성
     */
    fun createMapState(markerState: MarkerState): MapState
    
    /**
     * 기존 API와의 호환성을 위한 메서드
     */
    fun createMapState(markers: List<Marker>, selectedMarkerId: String?): MapState
    
    /**
     * 마커 삭제 상태 처리
     */
    fun processMarkerDeletionState(markerToDeleteId: String?): MapState?
}

/**
 * 마커 상태 처리를 담당하는 구현 클래스
 * MapViewModel에서 마커 관련 상태 처리 로직을 분리합니다.
 */
class MarkerStateProcessorImpl @Inject constructor(
    private val markerManager: MarkerManager
) : MarkerStateProcessor {
    /**
     * 마커 목록 상태를 UI 상태로 변환
     * @return 마커 목록이 비어있는지 여부에 따른 상태 Flow
     */
    override fun processMarkerState(): Flow<MarkerState> {
        return markerManager.markers.map { markers ->
            MarkerState(
                markers = markers,
                selectedId = markerManager.selectedMarkerId.value
            )
        }
    }
    
    /**
     * 마커 상태를 기반으로 맵 UI 상태 생성
     * @param markerState 마커 상태
     * @return 적절한 MapState
     */
    override fun createMapState(markerState: MarkerState): MapState {
        return when {
            markerState.isEmpty -> {
                Timber.d("마커가 없음 - 초기 상태 반환")
                MapState.Initial
            }
            markerState.selectedId != null -> {
                val selectedMarker = markerState.selectedMarker
                if (selectedMarker != null) {
                    Timber.d("선택된 마커 있음 - 성공 상태 반환: ${selectedMarker.id}")
                    MapState.Success(
                        markers = markerState.markers,
                        selectedMarker = selectedMarker
                    )
                } else {
                    Timber.w("선택된 마커 ID가 있지만 마커를 찾을 수 없음: ${markerState.selectedId}")
                    MapState.Success(markers = markerState.markers)
                }
            }
            else -> {
                Timber.d("마커 있음, 선택 없음 - 성공 상태 반환")
                MapState.Success(markers = markerState.markers)
            }
        }
    }
    
    /**
     * 기존 API와의 호환성을 위한 메서드
     * @param markers 마커 목록
     * @param selectedMarkerId 선택된 마커 ID (없을 수 있음)
     * @return 적절한 MapState
     */
    override fun createMapState(markers: List<Marker>, selectedMarkerId: String?): MapState {
        val markerState = MarkerState(
            markers = markers,
            selectedId = selectedMarkerId
        )
        return createMapState(markerState)
    }
    
    /**
     * 마커 삭제 상태 처리
     * @param markerToDeleteId 삭제 중인 마커 ID
     * @return 마커 삭제 상태를 반영한 MapState
     */
    override fun processMarkerDeletionState(markerToDeleteId: String?): MapState? {
        return if (markerToDeleteId != null) {
            Timber.d("마커 삭제 상태 처리: $markerToDeleteId")
            MapState.Loading
        } else {
            null
        }
    }
} 