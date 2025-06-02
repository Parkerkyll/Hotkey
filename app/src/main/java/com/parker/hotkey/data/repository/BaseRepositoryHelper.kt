package com.parker.hotkey.data.repository

import com.parker.hotkey.domain.model.Synchronizable
import com.parker.hotkey.domain.model.LastSync
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

/**
 * Repository의 공통 로직을 추상화한 헬퍼 클래스
 */
abstract class BaseRepositoryHelper {
    /**
     * 작업 내용을 로그로 기록
     *
     * @param operation 작업 유형
     * @param details 작업 상세 정보
     */
    protected fun logOperation(operation: String, details: String) {
        Timber.d("$operation: $details")
    }
    
    /**
     * 오류를 로그로 기록
     *
     * @param e 발생한 예외
     * @param operation 작업 유형
     * @param details 작업 상세 정보
     */
    protected fun logError(e: Exception, operation: String, details: String) {
        Timber.e(e, "$operation 실패: $details")
    }
} 