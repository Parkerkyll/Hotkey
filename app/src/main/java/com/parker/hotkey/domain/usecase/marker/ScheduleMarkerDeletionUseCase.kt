package com.parker.hotkey.domain.usecase.marker

import androidx.work.*
import com.parker.hotkey.presentation.map.EmptyMarkerDeleteWorker
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ScheduleMarkerDeletionUseCase @Inject constructor(
    private val workManager: WorkManager
) {
    companion object {
        private const val MARKER_DELETE_DELAY = 15L  // 메모 없는 마커 자동 삭제 대기 시간 (15초)
    }

    operator fun invoke(markerId: String) {
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
} 