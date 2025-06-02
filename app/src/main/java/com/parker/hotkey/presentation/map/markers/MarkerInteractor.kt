package com.parker.hotkey.presentation.map.markers

import com.naver.maps.geometry.LatLng
import com.parker.hotkey.domain.manager.MarkerManager
import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.repository.AuthRepository
import com.parker.hotkey.domain.usecase.marker.CreateMarkerUseCase
import com.parker.hotkey.domain.usecase.marker.DeleteMarkerWithValidationUseCase
import timber.log.Timber
import javax.inject.Inject

/**
 * 마커 관련 비즈니스 로직을 담당하는 인터페이스
 */
interface MarkerInteractor {
    /**
     * 특정 위치에 마커 생성
     */
    suspend fun createMarker(userId: String?, location: LatLng): Result<Marker>
    
    /**
     * 마커 생성 후 선택 및 메모장 표시
     */
    suspend fun createMarkerAndOpenMemo(userId: String?, location: LatLng): Result<Marker>
    
    /**
     * 마커 삭제
     */
    suspend fun deleteMarker(markerId: String): Result<Unit>
    
    /**
     * 마커 바로 생성 및 메모장 표시
     * 위치 선택 없이 현재 위치에 바로 마커 생성
     */
    suspend fun createMarkerAtCurrentLocation(currentLocation: LatLng?): Result<Marker>
}

/**
 * 마커 관련 비즈니스 로직을 담당하는 클래스
 * 마커 생성, 삭제 등 핵심 기능을 처리합니다.
 */
class MarkerInteractorImpl @Inject constructor(
    private val markerManager: MarkerManager,
    private val authRepository: AuthRepository,
    private val createMarkerUseCase: CreateMarkerUseCase,
    private val deleteMarkerWithValidationUseCase: DeleteMarkerWithValidationUseCase
) : MarkerInteractor {
    /**
     * 특정 위치에 마커 생성
     * @param userId 사용자 ID (없을 경우 현재 인증된 사용자 ID 사용)
     * @param location 마커 위치
     * @return 생성된 마커 또는 에러 결과
     */
    override suspend fun createMarker(userId: String?, location: LatLng): Result<Marker> {
        val actualUserId = userId ?: authRepository.getUserId()
        
        if (actualUserId.isBlank()) {
            Timber.e("마커 생성 실패: 사용자 ID가 없음")
            return Result.failure(IllegalStateException("사용자 ID가 필요합니다."))
        }
        
        return try {
            Timber.d("마커 생성 시작: 위치 = $location, 사용자 = $actualUserId")
            markerManager.createMarker(actualUserId, location)
        } catch (e: Exception) {
            Timber.e(e, "마커 생성 중 예외 발생")
            Result.failure(e)
        }
    }
    
    /**
     * 마커 생성 후 선택 및 메모장 표시
     */
    override suspend fun createMarkerAndOpenMemo(userId: String?, location: LatLng): Result<Marker> {
        val actualUserId = userId ?: authRepository.getUserId()
        
        return try {
            Timber.d("마커 생성 및 메모장 열기 시작: 위치 = $location")
            markerManager.createMarkerAndOpenMemo(actualUserId, location)
        } catch (e: Exception) {
            Timber.e(e, "마커 생성 및 메모장 열기 중 예외 발생")
            Result.failure(e)
        }
    }
    
    /**
     * 마커 삭제
     * @param markerId 삭제할 마커 ID
     * @return 성공 또는 실패 결과
     */
    override suspend fun deleteMarker(markerId: String): Result<Unit> {
        return try {
            Timber.d("마커 삭제 시작: $markerId")
            
            // UI 최적화를 위한 낙관적 업데이트
            markerManager.forceRemoveMarkerFromList(markerId)
            
            val result = deleteMarkerWithValidationUseCase(markerId)
            
            result.onSuccess {
                Timber.d("마커 삭제 성공: $markerId")
                
                // 선택 상태 초기화 (필요한 경우)
                if (markerManager.selectedMarkerId.value == markerId) {
                    markerManager.clearSelectedMarker()
                }
            }.onFailure { error ->
                Timber.e(error, "마커 삭제 실패: $markerId")
            }
            
            result
        } catch (e: Exception) {
            Timber.e(e, "마커 삭제 중 예외 발생: $markerId")
            Result.failure(e)
        }
    }
    
    /**
     * 마커 바로 생성 및 메모장 표시
     * 위치 선택 없이 현재 위치에 바로 마커 생성
     */
    override suspend fun createMarkerAtCurrentLocation(currentLocation: LatLng?): Result<Marker> {
        if (currentLocation == null) {
            Timber.e("현재 위치를 알 수 없어 마커를 생성할 수 없습니다.")
            return Result.failure(IllegalStateException("현재 위치를 알 수 없습니다."))
        }
        
        val userId = authRepository.getUserId()
        
        return try {
            Timber.d("현재 위치에 마커 생성: $currentLocation")
            markerManager.createMarkerAndOpenMemo(userId, currentLocation)
        } catch (e: Exception) {
            Timber.e(e, "현재 위치에 마커 생성 중 예외 발생")
            Result.failure(e)
        }
    }
} 