package com.parker.hotkey.domain.manager

import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.work.BackoffPolicy
import com.parker.hotkey.presentation.map.EmptyMarkerDeleteWorker
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarkerDeletionManager @Inject constructor(
    private val workManager: WorkManager
) {
    companion object {
        const val MARKER_DELETE_DELAY = 15L  // 메모 없는 마커 자동 삭제 대기 시간 (15초)
    }

    fun scheduleMarkerDeletion(markerId: String) {
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

    fun cancelScheduledDeletion(markerId: String) {
        workManager.cancelAllWorkByTag(markerId)
        Timber.d("마커 자동 삭제 작업 취소됨: $markerId")
    }
} 