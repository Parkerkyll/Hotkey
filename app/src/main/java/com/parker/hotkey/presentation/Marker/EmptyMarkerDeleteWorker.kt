package com.parker.hotkey.presentation.map

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.parker.hotkey.domain.repository.MarkerRepository
import com.parker.hotkey.domain.repository.MemoRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import org.greenrobot.eventbus.EventBus
import timber.log.Timber

@HiltWorker
class EmptyMarkerDeleteWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val markerRepository: MarkerRepository,
    private val memoRepository: MemoRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_MARKER_ID = "markerId"
    }

    override suspend fun doWork(): Result {
        val markerId = inputData.getString(KEY_MARKER_ID)
            ?: return Result.failure()

        return try {
            Timber.d("마커 자동 삭제 작업 시작: $markerId")
            
            // 1. 마커가 존재하는지 확인
            val marker = markerRepository.getById(markerId)
            if (marker == null) {
                Timber.d("마커가 이미 삭제됨: $markerId")
                return Result.success()
            }

            // 2. 메모 개수 확인
            val memoCount = memoRepository.getMemoCount(markerId)
            if (memoCount > 0) {
                Timber.d("메모가 있어 마커 삭제 취소: $markerId (메모 개수: $memoCount)")
                return Result.success()
            }

            // 3. 마커 삭제
            markerRepository.delete(markerId)
            Timber.d("빈 마커 자동 삭제 완료: $markerId")

            // 4. UI 업데이트를 위한 이벤트 발생
            EventBus.getDefault().post(MarkerDeletedEvent(markerId))

            Result.success(workDataOf("deleted_marker_id" to markerId))
        } catch (e: Exception) {
            Timber.e(e, "마커 자동 삭제 중 오류 발생: $markerId")
            Result.retry()
        }
    }
}

data class MarkerDeletedEvent(val markerId: String) 