package com.parker.hotkey.domain.usecase.marker

import com.parker.hotkey.domain.repository.MarkerRepository
import com.parker.hotkey.domain.repository.MemoRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber
import javax.inject.Inject

class DeleteMarkerWithValidationUseCase(
    private val markerRepository: MarkerRepository,
    private val memoRepository: MemoRepository
) {
    suspend operator fun invoke(markerId: String): Result<Unit> {
        return try {
            // 1. 마커 존재 여부 확인
            val marker = markerRepository.getMarkerById(markerId)
                ?: return Result.failure(IllegalStateException("마커가 존재하지 않습니다: $markerId"))

            // 2. 마커 삭제 전에 연관된 메모들도 모두 삭제
            val memos = memoRepository.getMemosByMarkerId(markerId).first()
            for (memo in memos) {
                memoRepository.deleteMemo(memo.id, markerId)
            }

            // 3. 마커 삭제
            markerRepository.deleteMarker(markerId)
            Timber.d("마커와 관련 메모 삭제 완료: $markerId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "마커 삭제 중 오류 발생: $markerId")
            Result.failure(e)
        }
    }
} 