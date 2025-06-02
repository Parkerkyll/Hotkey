package com.parker.hotkey.domain.usecase.marker

import com.parker.hotkey.data.remote.sync.util.SyncException
import com.parker.hotkey.domain.repository.MarkerRepository
import com.parker.hotkey.domain.repository.MemoRepository
import com.parker.hotkey.domain.repository.SyncRepository
import com.parker.hotkey.domain.manager.MarkerStateAdapter
import com.parker.hotkey.domain.model.MarkerState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber
import javax.inject.Singleton

/**
 * 마커 삭제 유즈케이스
 * 마커의 상태에 따라 적절한 삭제 전략을 선택합니다.
 * 임시 마커는 API 호출 없이 로컬에서만 삭제하고,
 * 영구 마커는 서버 동기화를 시도합니다.
 */
@Singleton
class DeleteMarkerWithValidationUseCase constructor(
    private val markerRepository: MarkerRepository,
    private val memoRepository: MemoRepository,
    private val syncRepository: SyncRepository,
    private val markerStateAdapter: MarkerStateAdapter
) {
    suspend operator fun invoke(markerId: String): Result<Unit> {
        return try {
            Timber.d("DeleteMarkerWithValidationUseCase: 마커 삭제 시작 - ID=$markerId")
            
            // 마커 상태 확인
            val state = markerStateAdapter.getMarkerState(markerId)
            Timber.d("마커 상태 확인: ID=$markerId, state=$state")
            
            // 마커 존재 여부 확인
            val marker = markerRepository.getById(markerId)
            if (marker == null) {
                Timber.w("DeleteMarkerWithValidationUseCase: 마커가 이미 삭제되었거나 존재하지 않음 - ID=$markerId")
                return Result.success(Unit)
            }

            // 상태에 따른 삭제 전략 선택
            when (state) {
                MarkerState.TEMPORARY -> {
                    // 임시 마커는 로컬에서만 삭제 (API 호출 없음)
                    Timber.d("DeleteMarkerWithValidationUseCase: 임시 마커 로컬 삭제 - ID=$markerId")
                    deleteLocalOnly(markerId)
                }
                MarkerState.PERSISTED -> {
                    // 영구 마커는 서버 동기화 시도
                    try {
                        val syncSuccess = syncRepository.deleteMarker(markerId)
                        if (!syncSuccess) {
                            Timber.w("DeleteMarkerWithValidationUseCase: 서버 마커 삭제 실패 - ID=$markerId. 로컬만 삭제를 진행합니다.")
                            // 서버 삭제 실패 시에도 로컬 삭제 진행
                            deleteLocalOnly(markerId)
                        } else {
                            Timber.d("DeleteMarkerWithValidationUseCase: 서버 마커 삭제 성공 - ID=$markerId")
                            // 서버 삭제 성공 시 로컬 DB 삭제는 SyncRepository에서 이미 처리됨
                        }
                    } catch (e: Exception) {
                        // SyncException.NotFoundError나 SyncException.MarkerNotFoundError 예외는 정상 처리로 간주
                        if (e is SyncException.NotFoundError || e is SyncException.MarkerNotFoundError) {
                            Timber.d("DeleteMarkerWithValidationUseCase: 서버에 마커가 이미 존재하지 않음 - ID=$markerId")
                            // 로컬에서만 삭제 진행
                            deleteLocalOnly(markerId)
                        } else {
                            // 기타 예외는 로그만 남기고 로컬 삭제 진행
                            Timber.e(e, "DeleteMarkerWithValidationUseCase: 서버 마커 삭제 중 예외 발생 - ID=$markerId")
                            deleteLocalOnly(markerId)
                        }
                    }
                }
                MarkerState.DELETED -> {
                    // 이미 삭제된 마커
                    Timber.d("DeleteMarkerWithValidationUseCase: 이미 삭제된 마커 - ID=$markerId")
                    // 로컬에서도 확실히 삭제
                    deleteLocalOnly(markerId)
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "DeleteMarkerWithValidationUseCase: 마커 삭제 중 예외 발생 - ID=$markerId")
            Result.failure(e)
        }
    }
    
    /**
     * 로컬 DB에서만 마커를 삭제
     */
    private suspend fun deleteLocalOnly(markerId: String) {
        try {
            Timber.d("DeleteMarkerWithValidationUseCase: 로컬에서만 마커 삭제 시작 - ID=$markerId")
            // 로컬 DB에서 마커 및 연관 메모 삭제 (CASCADE)
            markerRepository.delete(markerId)
            Timber.d("DeleteMarkerWithValidationUseCase: 로컬 마커 삭제 완료 - ID=$markerId")
        } catch (e: Exception) {
            Timber.e(e, "DeleteMarkerWithValidationUseCase: 로컬 마커 삭제 중 오류 발생 - ID=$markerId")
        }
    }
}