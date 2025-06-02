package com.parker.hotkey.domain.util

import com.parker.hotkey.domain.constants.CoroutineConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * 코루틴 작업을 관리하는 유틸리티 클래스
 * 각 키별로 작업을 관리하고, 필요 시 이전 작업을 취소합니다.
 * 디바운싱 기능을 제공합니다.
 *
 * @param T 작업 식별을 위한 키 타입
 */
class JobManager<T : Any> {
    private val jobs = ConcurrentHashMap<T, Job>()
    
    /**
     * 기존 작업을 취소하고 새 작업을 시작합니다.
     * 
     * @param scope 코루틴 스코프
     * @param key 작업 식별 키
     * @param debounceTime 디바운스 시간 (밀리초)
     * @param onCancel 작업 취소 시 호출될 콜백 (선택사항)
     * @param block 실행할 코루틴 블록
     * @return 생성된 Job 객체
     */
    fun launch(
        scope: CoroutineScope,
        key: T,
        debounceTime: Long = CoroutineConstants.DEFAULT_DEBOUNCE_TIME,
        onCancel: (suspend () -> Unit)? = null,
        block: suspend () -> Unit
    ): Job {
        // 기존 작업이 있으면 취소
        cancelJob(key)
        
        return scope.launch {
            try {
                // 디바운싱
                if (debounceTime > 0) {
                    delay(debounceTime)
                }
                
                // 작업 실행
                Timber.d("[$key] 작업 시작")
                block()
                Timber.d("[$key] 작업 완료")
            } catch (e: CancellationException) {
                // 취소는 DEBUG 레벨로 로깅
                Timber.d("[$key] 작업이 취소되었습니다")
                onCancel?.invoke()
            } catch (e: Exception) {
                // 일반 예외는 ERROR 레벨로 로깅
                Timber.e(e, "[$key] 작업 실행 중 오류 발생")
                throw e
            } finally {
                // 작업 완료 시 맵에서 제거
                jobs.remove(key)
                Timber.d("[$key] 작업 리소스 정리 완료")
            }
        }.also {
            jobs[key] = it
        }
    }
    
    /**
     * 특정 키에 해당하는 작업을 취소합니다.
     * 
     * @param key 취소할 작업의 키
     * @return 작업이 취소되었는지 여부
     */
    fun cancelJob(key: T): Boolean {
        return jobs[key]?.let { job ->
            if (job.isActive) {
                Timber.d("[$key] 기존 작업 취소")
                job.cancel()
                true
            } else {
                Timber.d("[$key] 이미 완료된 작업")
                jobs.remove(key)
                false
            }
        } ?: false
    }
    
    /**
     * 특정 키에 해당하는 작업이 활성화되어 있는지 확인합니다.
     * 
     * @param key 확인할 작업의 키
     * @return 작업이 활성화되어 있는지 여부
     */
    fun isJobActive(key: T): Boolean {
        return jobs[key]?.isActive ?: false
    }
    
    /**
     * 모든 작업을 취소합니다.
     */
    fun cancelAll() {
        Timber.d("모든 작업 취소 (총 ${jobs.size}개)")
        jobs.forEach { (key, job) ->
            if (job.isActive) {
                Timber.d("[$key] 작업 취소")
                job.cancel()
            }
        }
        jobs.clear()
    }
} 