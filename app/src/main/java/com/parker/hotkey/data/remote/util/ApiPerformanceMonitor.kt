package com.parker.hotkey.data.remote.util

import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * API 성능 모니터링 유틸리티
 * API 호출 횟수와 응답 시간을 추적하고 분석 리포트를 생성합니다.
 */
@Singleton
class ApiPerformanceMonitor @Inject constructor() {
    private val apiCallCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val apiCallDurations = ConcurrentHashMap<String, MutableList<Long>>()
    private val apiErrorCounts = ConcurrentHashMap<String, AtomicInteger>()

    /**
     * API 호출 결과를 기록합니다.
     * @param key API 요청 키
     * @param durationMs 소요 시간(밀리초)
     * @param isError 오류 발생 여부
     */
    fun recordApiCall(key: String, durationMs: Long, isError: Boolean = false) {
        // 호출 횟수 증가
        apiCallCounts.computeIfAbsent(key) { AtomicInteger(0) }.incrementAndGet()
        
        // 응답 시간 기록
        apiCallDurations.computeIfAbsent(key) { mutableListOf() }.add(durationMs)
        
        // 오류 발생 시 오류 카운트 증가
        if (isError) {
            apiErrorCounts.computeIfAbsent(key) { AtomicInteger(0) }.incrementAndGet()
        }
        
        // 성능 통계 로깅
        Timber.tag("API_PERF").d("[$key] 소요시간: ${durationMs}ms, 오류: $isError")
    }
    
    /**
     * 특정 API에 대한 성능 지표를 반환합니다.
     * @param key API 요청 키
     * @return 해당 API의 성능 지표 (null인 경우 기록이 없음)
     */
    fun getMetrics(key: String): ApiMetrics? {
        val count = apiCallCounts[key]?.get() ?: return null
        val durations = apiCallDurations[key] ?: return null
        val errorCount = apiErrorCounts[key]?.get() ?: 0
        
        if (durations.isEmpty()) return null
        
        val avgDuration = durations.average()
        val minDuration = durations.minOrNull() ?: 0
        val maxDuration = durations.maxOrNull() ?: 0
        
        return ApiMetrics(
            key = key,
            callCount = count,
            avgDurationMs = avgDuration.toLong(),
            minDurationMs = minDuration,
            maxDurationMs = maxDuration,
            errorCount = errorCount,
            successRate = if (count > 0) (count - errorCount) * 100.0 / count else 0.0
        )
    }
    
    /**
     * 모든 API 성능 지표 보고서를 생성합니다.
     * @return 성능 보고서 문자열
     */
    fun generateReport(): String {
        return buildString {
            appendLine("=== API 성능 보고서 ===")
            appendLine("총 모니터링된 API 종류: ${apiCallCounts.size}개")
            appendLine()
            
            val sortedKeys = apiCallCounts.keys.sorted()
            for (key in sortedKeys) {
                getMetrics(key)?.let { metrics ->
                    appendLine("[$key]")
                    appendLine("  - 호출 횟수: ${metrics.callCount}회")
                    appendLine("  - 평균 응답시간: ${metrics.avgDurationMs}ms")
                    appendLine("  - 최소/최대: ${metrics.minDurationMs}ms / ${metrics.maxDurationMs}ms")
                    appendLine("  - 오류 횟수: ${metrics.errorCount}회")
                    appendLine("  - 성공률: ${String.format("%.1f", metrics.successRate)}%")
                    appendLine()
                }
            }
        }
    }
    
    /**
     * 모니터링 데이터를 초기화합니다.
     */
    fun reset() {
        apiCallCounts.clear()
        apiCallDurations.clear()
        apiErrorCounts.clear()
        Timber.tag("API_PERF").d("성능 모니터링 데이터 초기화됨")
    }
}

/**
 * API 성능 지표를 담는 데이터 클래스
 */
data class ApiMetrics(
    val key: String,
    val callCount: Int,
    val avgDurationMs: Long,
    val minDurationMs: Long,
    val maxDurationMs: Long,
    val errorCount: Int,
    val successRate: Double
) 