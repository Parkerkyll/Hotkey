package com.parker.hotkey.presentation.map.processor

import com.naver.maps.geometry.LatLng
import com.parker.hotkey.domain.manager.EditModeState
import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.model.Memo
import com.parker.hotkey.presentation.map.MapError
import com.parker.hotkey.presentation.state.MapState
import timber.log.Timber
import javax.inject.Inject
import com.parker.hotkey.domain.constants.GeohashConstants

/**
 * MapStateProcessor - MapViewModel의 상태 처리 로직을 분리한 클래스
 * Flow 결합 로직을 단순화하고 상태 처리의 일관성을 제공합니다.
 */
class MapStateProcessor @Inject constructor() {

    /**
     * 여러 소스에서 데이터를 결합하여 통합된 MapState를 생성합니다.
     * 
     * @param markers 현재 마커 목록
     * @param selectedMarkerId 선택된 마커 ID (없을 수 있음)
     * @param memos 현재 메모 목록 (선택된 마커의 메모들)
     * @param location 현재 위치 (없을 수 있음)
     * @param editModeState 편집 모드 상태
     * @param error 오류 (없을 수 있음)
     * @param markerToDeleteId 삭제 중인 마커 ID (없을 수 있음)
     * @param currentZoomLevel 현재 줌 레벨 (성공 상태 전환 시 유지해야 함)
     * @return 통합된 MapState
     */
    fun process(
        markers: List<Marker>,
        selectedMarkerId: String?,
        memos: List<Memo>,
        location: LatLng?,
        editModeState: EditModeState,
        error: Throwable?,
        markerToDeleteId: String?,
        currentZoomLevel: Double? = null
    ): MapState {
        // 상세 로깅 추가
        Timber.d("MapStateProcessor.process 호출: 마커=${markers.size}개, 위치=${location?.toString() ?: "null"}")
        
        // 마커 geohash 정보 분석
        if (markers.isNotEmpty()) {
            val geohashGroups = markers.groupBy { it.geohash }
            Timber.d("마커 geohash 그룹: ${geohashGroups.keys.joinToString(", ")}")
            
            geohashGroups.forEach { (geohash, markersInGeohash) ->
                Timber.d("geohash=$geohash markers: ${markersInGeohash.size}")
            }
            
            // 첫 5개 마커 상세 정보
            markers.take(5).forEach { marker ->
                Timber.d("마커 상세: id=${marker.id}, geohash=${marker.geohash}, lat=${marker.latitude}, lng=${marker.longitude}")
            }
        } else {
            Timber.w("process 메소드에 전달된 마커가 없음")
        }
        
        // 현재 위치의 geohash 계산 (주변 마커와 비교하기 위함)
        location?.let {
            try {
                val geohash = com.parker.hotkey.util.GeoHashUtil.encode(it.latitude, it.longitude, GeohashConstants.GEOHASH_PRECISION)
                Timber.d("현재 위치 geohash: $geohash")
                
                // 이 geohash에 해당하는 마커 찾기
                val markersInCurrentGeohash = markers.filter { marker -> marker.geohash == geohash }
                Timber.d("현재 위치 geohash($geohash)에 있는 마커: ${markersInCurrentGeohash.size}개")
            } catch (e: Exception) {
                Timber.e(e, "위치에서 geohash 계산 중 오류")
            }
        }
        
        // 오류가 있으면 Error 상태 반환
        if (error != null) {
            Timber.e("오류 상태: ${error.message}")
            return MapState.Error(MapError.fromException(error))
        }
        
        // 필수 데이터가 없으면 Loading 상태 반환
        if (markers.isEmpty() && location == null) {
            Timber.d("로딩 상태: 마커와 위치 데이터 없음")
            return MapState.Loading
        }
        
        // 데이터가 있으면 Success 상태 반환
        val selectedMarker = selectedMarkerId?.let { id -> 
            markers.find { marker -> marker.id == id } 
        }
        
        Timber.d("성공 상태: 마커 ${markers.size}개, 선택된 마커: ${selectedMarker?.id}, 메모 ${memos.size}개")
        
        return MapState.Success(
            markers = markers,
            selectedMarker = selectedMarker,
            selectedMarkerMemos = memos,
            currentLocation = location ?: LatLng(0.0, 0.0),
            editMode = editModeState.isEditMode,
            editModeTimeRemaining = editModeState.remainingTimeMs,
            zoomLevel = currentZoomLevel ?: 15.0,
            markerToDeleteId = markerToDeleteId
        )
    }
} 