package com.parker.hotkey.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.BackoffPolicy
import com.parker.hotkey.data.remote.util.ApiPriority
import com.parker.hotkey.data.remote.util.ApiRequestManager
import com.parker.hotkey.domain.repository.SyncRepository
import com.parker.hotkey.util.GeoHashUtil
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * ApiRequestManager와 WorkManager를 연동하는 작업자 클래스
 */
@HiltWorker
class ApiSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val apiRequestManager: ApiRequestManager,
    private val syncRepository: SyncRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val geohash = inputData.getString(KEY_GEOHASH) ?: return Result.failure()
        val isPostUpdate = inputData.getBoolean(KEY_IS_POST_UPDATE, false)
        
        Timber.d("WorkManager: 데이터 동기화 작업 시작 - geohash: $geohash, 앱 업데이트 후 실행: $isPostUpdate")
        
        // 요청 키 생성 (일관된 방식으로)
        val requestKey = "initialData:$geohash"
        
        return try {
            // ApiRequestManager를 통해 작업 실행 (백그라운드 우선순위로)
            apiRequestManager.executeRequest(
                requestKey = requestKey,
                priority = ApiPriority.BACKGROUND_SYNC
            ) {
                Timber.d("WorkManager: API 호출 실행 - geohash: $geohash")
                syncRepository.loadInitialData(geohash)
            }
            
            Timber.d("WorkManager: 데이터 동기화 작업 완료 - geohash: $geohash")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "WorkManager: 작업 실행 실패 - geohash: $geohash")
            if (runAttemptCount < MAX_RETRY_COUNT) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_GEOHASH = "KEY_GEOHASH"
        const val KEY_IS_POST_UPDATE = "KEY_IS_POST_UPDATE"
        private const val MAX_RETRY_COUNT = 3
        
        /**
         * 단일 지역에 대한 동기화 작업 예약
         */
        fun schedule(context: Context, geohash: String): Operation {
            val data = workDataOf(KEY_GEOHASH to geohash)
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
                
            val request = OneTimeWorkRequestBuilder<ApiSyncWorker>()
                .setConstraints(constraints)
                .setInputData(data)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()
                
            return WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "sync_$geohash",
                    ExistingWorkPolicy.KEEP,
                    request
                )
        }
        
        /**
         * 여러 지역에 대한 백그라운드 동기화 예약
         */
        fun scheduleMultiRegionSync(context: Context, geohashes: List<String>) {
            geohashes.forEach { geohash ->
                schedule(context, geohash)
            }
        }
        
        /**
         * 앱 업데이트 후 필요한 동기화 작업 예약
         */
        fun schedulePostUpdateSync(context: Context, currentGeohash: String) {
            val data = workDataOf(
                KEY_GEOHASH to currentGeohash,
                KEY_IS_POST_UPDATE to true
            )
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
                
            val request = OneTimeWorkRequestBuilder<ApiSyncWorker>()
                .setConstraints(constraints)
                .setInputData(data)
                .build()
                
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "post_update_sync",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
        }
        
        /**
         * 주기적인 백그라운드 동기화 작업 설정
         */
        fun setupPeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
                
            val request = PeriodicWorkRequestBuilder<ApiSyncWorker>(
                repeatInterval = 6,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()
                
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    "periodic_sync",
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }
} 