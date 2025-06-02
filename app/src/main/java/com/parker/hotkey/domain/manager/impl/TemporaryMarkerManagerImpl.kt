package com.parker.hotkey.domain.manager.impl

import com.naver.maps.geometry.LatLng
import com.parker.hotkey.domain.manager.MarkerManager
import com.parker.hotkey.domain.manager.TemporaryMarkerEvent
import com.parker.hotkey.domain.manager.TemporaryMarkerManager
import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.repository.MarkerRepository
import com.parker.hotkey.domain.usecase.UploadChangesUseCase
import com.parker.hotkey.util.calculateDistanceTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import com.parker.hotkey.domain.manager.BaseManager
import com.parker.hotkey.di.qualifier.ApplicationScope
import dagger.Lazy

/**
 * 마커 근접성 검사 결과를 나타내는 sealed 클래스
 */
sealed class MarkerProximityResult {
    /**
     * 생성 가능한 상태 (근처에 마커 없음)
     */
    object CanCreate : MarkerProximityResult()
    
    /**
     * 근처에 마커가 있는 상태
     * @param nearbyMarkers 근처 마커 목록
     * @param minDistance 가장 가까운 마커까지의 거리 (미터)
     */
    data class NearbyMarkerExists(val nearbyMarkers: List<Marker>, val minDistance: Double) : MarkerProximityResult()
}

/**
 * 임시 마커 관리자 구현체
 * 임시 마커의 생성, 삭제, 영구 저장 기능을 담당합니다.
 */
@Singleton
class TemporaryMarkerManagerImpl @Inject constructor(
    private val markerManager: Lazy<MarkerManager>,
    private val markerRepository: MarkerRepository,
    @ApplicationScope coroutineScope: CoroutineScope,
    private val uploadChangesUseCase: UploadChangesUseCase
) : BaseManager<TemporaryMarkerEvent>(coroutineScope), TemporaryMarkerManager {
    
    // 근접 마커 검사를 위한 거리 기준 (미터)
    private val NEARBY_MARKER_THRESHOLD_METERS = 20.0
    
    // 임시 마커 ID 집합
    private val _temporaryMarkers = MutableStateFlow<Set<String>>(emptySet())
    override val temporaryMarkers: StateFlow<Set<String>> = _temporaryMarkers.asStateFlow()
    
    /**
     * 주변에 마커가 있는지 검사
     * 
     * @param latLng 새 마커 위치
     * @return 검사 결과
     */
    private fun checkNearbyMarkers(latLng: LatLng): MarkerProximityResult {
        val currentMarkers = markerManager.get().markers.value
        
        // 20미터 이내의 마커를 찾음
        val nearbyMarkers = currentMarkers.filter { marker ->
            val markerLatLng = LatLng(marker.latitude, marker.longitude)
            val distance = latLng.calculateDistanceTo(markerLatLng)
            distance <= NEARBY_MARKER_THRESHOLD_METERS
        }
        
        return if (nearbyMarkers.isEmpty()) {
            MarkerProximityResult.CanCreate
        } else {
            // 가장 가까운 마커까지의 거리 계산
            val minDistance = nearbyMarkers.minOfOrNull { marker ->
                val markerLatLng = LatLng(marker.latitude, marker.longitude)
                latLng.calculateDistanceTo(markerLatLng)
            } ?: NEARBY_MARKER_THRESHOLD_METERS
            
            MarkerProximityResult.NearbyMarkerExists(nearbyMarkers, minDistance)
        }
    }
    
    /**
     * 임시 마커 생성
     * 
     * @param userId 사용자 ID
     * @param latLng 위치 좌표
     * @return 생성된 마커
     */
    override suspend fun createTemporaryMarker(userId: String, latLng: LatLng): Marker {
        // 기존 MarkerManager를 사용하여 마커 생성
        val result = markerManager.get().createMarker(userId, latLng)
        
        if (result.isSuccess) {
            val marker = result.getOrThrow()
            
            // 임시 마커로 표시 - 원자적 연산으로 처리
            _temporaryMarkers.value = _temporaryMarkers.value + marker.id
            Timber.d("임시 마커 생성됨: ${marker.id}")
            
            // 중요: 새로운 임시 마커는 깨끗한 상태로 시작 (이전 마커 정보 섞임 방지)
            // 마커가 초기 상태임을 보장하기 위해 명시적으로 기본 속성 확인
            val cleanMarker = marker.copy(
                // 메모가 있다면 제거 (임시 마커는 빈 상태로 시작)
                // 마커 자체에는 메모 정보가 없지만, 관련 상태가 정리되었음을 보장
            )
            
            // 마커 정보가 즉시 사용 가능하도록 명시적으로 마커 관리자 상태 업데이트
            // 동기화 문제 해결을 위해 기존 마커 목록을 유지하면서 새 마커 추가
            val currentMarkers = markerManager.get().markers.value
            val updatedMarkers = currentMarkers.toMutableList()
            
            // 중복 방지를 위해 기존 ID가 있는지 확인
            val existingIndex = updatedMarkers.indexOfFirst { it.id == marker.id }
            if (existingIndex >= 0) {
                // 기존 마커가 있으면 대체 (새로운 임시 마커로)
                updatedMarkers[existingIndex] = cleanMarker
            } else {
                // 없으면 추가
                updatedMarkers.add(cleanMarker)
            }
            
            // 마커 목록 업데이트
            markerManager.get().updateMarkers(updatedMarkers)
            Timber.d("MarkerManager 상태도 즉시 업데이트됨: ${marker.id}")
            
            // 이벤트 발행
            coroutineScope.launch {
                bufferOrEmitEvent(TemporaryMarkerEvent.MarkerCreated(cleanMarker))
            }
            return cleanMarker
        } else {
            Timber.e(result.exceptionOrNull(), "임시 마커 생성 실패")
            throw result.exceptionOrNull() ?: IllegalStateException("알 수 없는 오류")
        }
    }
    
    /**
     * 임시 마커를 영구 마커로 변환
     * 
     * @param markerId 마커 ID
     */
    override fun makeMarkerPermanent(markerId: String) {
        if (!isTemporaryMarker(markerId)) {
            Timber.d("이미 영구 마커이거나 존재하지 않는 마커: $markerId")
            return
        }
        
        // 임시 마커 목록에서 제거 - DB 저장 전에 수행하여 중복 호출 방지
        _temporaryMarkers.value = _temporaryMarkers.value - markerId
        Timber.d("마커가 임시 목록에서 제거됨: $markerId")
        
        // 마커 DB에 영구 저장 및 서버 업로드
        coroutineScope.launch {
            try {
                // 마커 객체 가져오기
                val marker = markerManager.get().getMarkerById(markerId)
                
                if (marker != null) {
                    // 1. 마커를 DB에 영구 저장
                    markerRepository.insert(marker)
                    Timber.d("마커가 DB에 영구 저장됨: $markerId")
                    
                    // 2. 마커를 서버에 업로드 (동기적으로 처리)
                    val updatedMarker = uploadChangesUseCase.uploadMarker(marker)
                    if (updatedMarker != null) {
                        Timber.d("마커가 서버에 업로드 성공: $markerId")
                        // 업데이트된 마커 정보로 로컬 DB 갱신
                        markerRepository.update(updatedMarker)
                        
                        // 이벤트 발행
                        coroutineScope.launch {
                            bufferOrEmitEvent(TemporaryMarkerEvent.MarkerMadePermanent(markerId))
                        }
                    } else {
                        Timber.w("마커 서버 업로드 실패. 나중에 자동 동기화될 예정: $markerId")
                        // 서버 업로드 실패해도 이벤트는 발행
                        coroutineScope.launch {
                            bufferOrEmitEvent(TemporaryMarkerEvent.MarkerMadePermanent(markerId))
                        }
                    }
                } else {
                    Timber.e("영구 저장할 마커를 찾을 수 없음: $markerId")
                    // 목록에서 제거했던 마커를 다시 임시 목록에 추가
                    _temporaryMarkers.value = _temporaryMarkers.value + markerId
                }
            } catch (e: Exception) {
                Timber.e(e, "마커 영구 저장 및 서버 업로드 중 오류 발생: $markerId")
                // 오류 발생 시 이벤트 발행 생략 - 사용자에게 오류 알림 필요
            }
        }
    }
    
    /**
     * 임시 마커 삭제
     * 
     * @param markerId 마커 ID
     * @return 삭제 결과
     */
    override suspend fun deleteTemporaryMarker(markerId: String): Result<Unit> {
        if (!isTemporaryMarker(markerId)) {
            Timber.d("임시 마커가 아닌 마커 삭제 시도: $markerId")
            return Result.failure(IllegalArgumentException("임시 마커가 아닙니다"))
        }
        
        // 마커 삭제 수행
        val result = markerManager.get().deleteMarker(markerId)
        
        if (result.isSuccess) {
            // 임시 마커 목록에서 제거
            _temporaryMarkers.value = _temporaryMarkers.value - markerId
            
            // 이벤트 발행
            coroutineScope.launch {
                bufferOrEmitEvent(TemporaryMarkerEvent.MarkerDeleted(markerId))
            }
        }
        
        return result
    }
    
    /**
     * 임시 마커 목록에서 제거 (상태 관리용)
     * 
     * @param markerId 마커 ID
     */
    override fun removeTemporaryMarker(markerId: String) {
        if (isTemporaryMarker(markerId)) {
            _temporaryMarkers.value = _temporaryMarkers.value - markerId
            Timber.d("임시 마커 목록에서 제거됨: $markerId")
        }
    }
    
    /**
     * 마커가 임시인지 확인
     * 
     * @param markerId 마커 ID
     * @return 임시 마커 여부
     */
    override fun isTemporaryMarker(markerId: String): Boolean {
        return _temporaryMarkers.value.contains(markerId)
    }
    
    /**
     * 초기화
     */
    override fun initialize() {
        // 공통 초기화 로직 사용
        initializeCommon("TemporaryMarkerManager") {
            // 앱 재시작 시 임시 마커 정보가 유지되는 것을 원치 않으므로 초기화 시 별도 저장 로직은 구현하지 않음
        }
    }

    /**
     * 오류 이벤트 변환 구현
     */
    override fun createErrorEvent(throwable: Throwable, message: String): TemporaryMarkerEvent {
        return TemporaryMarkerEvent.Error(message, throwable)
    }
} 