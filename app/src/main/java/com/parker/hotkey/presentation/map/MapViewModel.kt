package com.parker.hotkey.presentation.map

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.model.Memo
import com.parker.hotkey.domain.repository.MarkerRepository
import com.parker.hotkey.domain.repository.MemoRepository
import com.parker.hotkey.domain.usecase.marker.DeleteMarkerWithValidationUseCase
import com.parker.hotkey.util.GeohashUtil
import com.parker.hotkey.data.mapper.toDomain
import com.parker.hotkey.data.mapper.toEntity
import com.parker.hotkey.domain.manager.MarkerDeletionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import com.parker.hotkey.domain.model.Location
import com.parker.hotkey.domain.repository.LocationManager

@HiltViewModel
class MapViewModel @Inject constructor(
    private val markerRepository: MarkerRepository,
    private val memoRepository: MemoRepository,
    private val deleteMarkerWithValidationUseCase: DeleteMarkerWithValidationUseCase,
    private val markerDeletionManager: MarkerDeletionManager,
    @ApplicationContext private val context: Context,
    private val locationManager: LocationManager
) : ViewModel() {

    companion object {
        private const val MARKER_DELETE_DELAY = 15L  // 메모 없는 마커 자동 삭제 대기 시간 (15초)
        private const val DEFAULT_ZOOM = 15.0
    }

    private var currentLocationGeohash: String? = null
    private var currentLocationNeighbors: List<String> = emptyList()

    private val _mapState = MutableStateFlow<MapState>(MapState.Initial)
    val mapState: StateFlow<MapState> = _mapState.asStateFlow()

    init {
        EventBus.getDefault().register(this)
        viewModelScope.launch {
            observeLocationAndMarkers()
        }
    }

    override fun onCleared() {
        super.onCleared()
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMarkerDeleted(event: MarkerDeletedEvent) {
        val currentState = _mapState.value
        if (currentState is MapState.Success) {
            _mapState.value = currentState.copy(
                markers = currentState.markers.filter { it.id != event.markerId }
            )
            Timber.d("UI에서 마커 제거됨: ${event.markerId}")
        }
    }

    fun onCameraPositionChanged(center: LatLng, zoom: Double) {
        val currentState = _mapState.value
        if (currentState is MapState.Success) {
            _mapState.value = currentState.copy(
                currentLocation = center,
                currentZoom = zoom
            )
        }
    }

    // Marker 관련 함수들
    fun onMapClicked(coord: LatLng) {
        viewModelScope.launch {
            try {
                val geohash = GeohashUtil.encode(coord)
                val marker = markerRepository.createMarker(
                    latitude = coord.latitude,
                    longitude = coord.longitude,
                    geohash = geohash
                )
                
                val currentState = _mapState.value
                if (currentState is MapState.Success) {
                    val updatedMarkers = currentState.markers.toMutableList()
                    updatedMarkers.add(marker.toDomain())
                    _mapState.value = currentState.copy(
                        markers = updatedMarkers,
                        currentZoom = MapConfigDelegate.MARKER_CREATION_ZOOM
                    )
                }

                // 빈 마커 자동 삭제 예약
                markerDeletionManager.scheduleMarkerDeletion(marker.id)
                
            } catch (e: Exception) {
                _mapState.value = MapState.Error(MapError.fromException(e))
            }
        }
    }

    fun deleteMarker(markerId: String) {
        viewModelScope.launch {
            try {
                deleteMarkerWithValidationUseCase(markerId).fold(
                    onSuccess = {
                        val currentState = _mapState.value
                        if (currentState is MapState.Success) {
                            val updatedMarkers = currentState.markers.filter { it.id != markerId }
                            _mapState.value = currentState.copy(
                                markers = updatedMarkers,
                                selectedMarkerMemos = emptyList()
                            )
                            // EventBus를 통해 마커 삭제 이벤트 발생
                            EventBus.getDefault().post(MarkerDeletedEvent(markerId))
                        }
                        Timber.d("마커 삭제 완료: $markerId")
                    },
                    onFailure = { error ->
                        when (error) {
                            is IllegalStateException -> {
                                Timber.d(error.message)
                            }
                            else -> {
                                Timber.e(error, "마커 삭제 중 오류 발생: $markerId")
                                _mapState.value = MapState.Error(MapError.fromException(Exception(error.message, error)))
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "마커 삭제 중 예기치 못한 오류 발생: $markerId")
                _mapState.value = MapState.Error(MapError.fromException(e))
            }
        }
    }

    // Memo 관련 함수들
    fun loadMemos(markerId: String) {
        viewModelScope.launch {
            try {
                val currentState = _mapState.value
                if (currentState is MapState.Success) {
                    val memos = memoRepository.getMemosByMarkerId(markerId).first().map { it.toDomain() }
                    _mapState.value = currentState.copy(
                        selectedMarkerMemos = memos
                    )
                }
            } catch (e: Exception) {
                _mapState.value = MapState.Error(MapError.fromException(e))
            }
        }
    }

    fun addMemo(markerId: String, content: String) {
        viewModelScope.launch {
            try {
                memoRepository.createMemo(markerId, content)
                loadMemos(markerId)
                
                // 메모가 추가되면 자동 삭제 취소
                markerDeletionManager.cancelScheduledDeletion(markerId)
            } catch (e: Exception) {
                _mapState.value = MapState.Error(MapError.fromException(e))
            }
        }
    }

    fun deleteMemo(memo: Memo) {
        viewModelScope.launch {
            try {
                memoRepository.deleteMemo(memo.id, memo.markerId)
                loadMemos(memo.markerId)
                
                // 메모가 모두 삭제되었는지 확인
                val memoCount = memoRepository.getMemoCount(memo.markerId)
                if (memoCount == 0) {
                    // 마지막 메모가 삭제된 경우 마커 자동 삭제 예약
                    markerDeletionManager.scheduleMarkerDeletion(memo.markerId)
                    Timber.d("마커 자동 삭제 작업 예약됨: ${memo.markerId}")
                }
            } catch (e: Exception) {
                _mapState.value = MapState.Error(MapError.fromException(e))
            }
        }
    }

    private fun observeLocationAndMarkers() {
        viewModelScope.launch {
            try {
                // 초기 상태를 Initial로 설정
                _mapState.value = MapState.Initial
                
                // 위치 권한이 없는 경우 Initial 상태 유지
                if (!locationManager.hasLocationPermission()) {
                    Timber.d("위치 권한 없음 - 초기 상태로 전환")
                    _mapState.value = MapState.Initial
                    return@launch
                }

                Timber.d("위치 업데이트 Flow 시작")
                locationManager.getLocationUpdates()
                    .catch { e ->
                        Timber.e(e, "위치 업데이트 에러")
                        when (e) {
                            is SecurityException -> {
                                if (_mapState.value !is MapState.Success) {
                                    _mapState.value = MapState.Initial
                                    Timber.d("위치 권한 없음 - 초기 상태로 전환")
                                }
                            }
                            else -> {
                                _mapState.value = MapState.Error(MapError.LocationError("위치 정보를 가져오는데 실패했습니다."))
                            }
                        }
                    }
                    .collect { location ->
                        try {
                            Timber.d("새로운 위치 수신: lat=${location.latitude}, lng=${location.longitude}")
                            
                            // Loading 상태로 전환
                            if (_mapState.value !is MapState.Success) {
                                Timber.d("Loading 상태로 전환")
                                _mapState.value = MapState.Loading
                            }

                            // 현재 위치 업데이트
                            val currentState = _mapState.value
                            if (currentState is MapState.Success) {
                                _mapState.value = currentState.copy(
                                    currentLocation = LatLng(location.latitude, location.longitude)
                                )
                                Timber.d("현재 위치 업데이트 완료")
                            }
                            
                            updateCurrentGeohash(location)
                            loadMarkersInCurrentRange()
                        } catch (e: Exception) {
                            Timber.e(e, "위치 데이터 처리 중 에러")
                            _mapState.value = MapState.Error(MapError.LocationError("위치 정보 처리 중 오류가 발생했습니다."))
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "위치 업데이트 초기화 에러")
                when (e) {
                    is SecurityException -> {
                        _mapState.value = MapState.Initial
                        Timber.d("위치 권한 없음 - 초기 상태로 전환")
                    }
                    else -> {
                        _mapState.value = MapState.Error(MapError.LocationError("위치 서비스를 시작할 수 없습니다."))
                    }
                }
            }
        }
    }

    private fun updateCurrentGeohash(location: Location) {
        try {
            val newGeohash = GeohashUtil.encode(location.latitude, location.longitude, 6)
            val newNeighbors = GeohashUtil.getNeighbors(newGeohash)
            
            // geohash가 변경된 경우에만 업데이트
            if (newGeohash != currentLocationGeohash) {
                Timber.d("Geohash 업데이트: $newGeohash")
                Timber.d("이웃 Geohash: ${newNeighbors.joinToString()}")
                currentLocationGeohash = newGeohash
                currentLocationNeighbors = newNeighbors
            }
        } catch (e: Exception) {
            Timber.e(e, "Geohash 업데이트 에러")
            throw e
        }
    }

    private fun loadMarkersInCurrentRange() {
        viewModelScope.launch {
            try {
                _mapState.value = MapState.Loading
                currentLocationGeohash?.let { geohash ->
                    markerRepository.getMarkersInGeohashRange(geohash, currentLocationNeighbors)
                        .catch { e ->
                            Timber.e(e, "마커 데이터 로딩 에러")
                            _mapState.value = MapState.Error(MapError.UnknownError("마커 데이터를 불러오는데 실패했습니다."))
                        }
                        .collect { markerEntities ->
                            val domainMarkers = markerEntities.map { it.toDomain() }
                            Timber.d("실제 로드된 마커 데이터: ${domainMarkers.size}개")
                            if (domainMarkers.isNotEmpty()) {
                                Timber.d("마커 데이터 상세: ${domainMarkers.joinToString { it.id }}")
                            }
                            _mapState.value = MapState.Success(
                                markers = domainMarkers,
                                selectedMarkerMemos = (_mapState.value as? MapState.Success)?.selectedMarkerMemos ?: emptyList(),
                                currentLocation = (_mapState.value as? MapState.Success)?.currentLocation ?: LatLng(0.0, 0.0),
                                currentZoom = (_mapState.value as? MapState.Success)?.currentZoom ?: DEFAULT_ZOOM
                            )
                        }
                }
            } catch (e: Exception) {
                Timber.e(e, "마커 데이터 로딩 실패")
                _mapState.value = MapState.Error(MapError.UnknownError("마커 데이터를 불러오는데 실패했습니다."))
            }
        }
    }
} 