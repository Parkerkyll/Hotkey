package com.parker.hotkey.presentation.map.markers

import android.content.Context
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.overlay.Marker
import com.parker.hotkey.domain.constants.MarkerConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.cancel
import kotlin.math.max

/**
 * 마커 객체 풀링을 관리하는 클래스
 * 마커 객체의 재사용을 통해 메모리 사용량과 GC 부담을 줄입니다.
 */
@Singleton
class MarkerPool @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val markerListenerManager: MarkerListenerManager // 마커 리스너 관리자 주입
) {
    
    // 사용 가능한 마커 객체 풀 - 스레드 안전한 컬렉션으로 변경
    private val pool = ConcurrentLinkedQueue<Marker>()
    
    // 현재 사용 중인 마커 (id -> Marker) - 스레드 안전한 맵으로 변경
    private val inUseMarkers = ConcurrentHashMap<String, Marker>()
    
    // 풀의 초기 크기
    private val initialPoolSize = 200
    
    // 풀이 부족할 때 한 번에 추가할 마커 수
    private val expandSize = 50
    
    // 통계 추적 변수
    private var totalCreated = 0
    private var totalRecycled = 0
    private var reusedCount = 0
    
    init {
        Timber.d("마커 풀 초기화: $initialPoolSize 개의 마커를 미리 생성합니다")
        expandPool(initialPoolSize)
    }
    
    /**
     * 풀 크기를 확장합니다.
     * @param count 추가할 마커 수
     */
    private fun expandPool(count: Int) {
        Timber.d("마커 풀 확장: $count 개 추가")
        for (i in 0 until count) {
            val marker = createNewMarker()
            pool.add(marker)
            totalCreated++
        }
        Timber.d("마커 풀 크기: ${pool.size}개, 총 생성: $totalCreated")
    }
    
    /**
     * 새로운 마커 객체를 생성합니다.
     * @return 생성된 마커 객체
     */
    private fun createNewMarker(): Marker {
        return Marker().apply {
            width = MarkerConstants.MARKER_WIDTH
            height = MarkerConstants.MARKER_HEIGHT
            alpha = MarkerConstants.MARKER_ALPHA_DEFAULT
            // 맵에는 아직 추가하지 않음 (map = null)
            // 초기화 시점에 불필요한 리소스 생성 방지
            isHideCollidedCaptions = true
            isHideCollidedMarkers = false
            captionTextSize = 0f
        }
    }
    
    /**
     * 풀에서 마커를 가져옵니다. 이미 동일 ID로 사용 중인 마커가 있으면 해당 마커를 반환합니다.
     * @param id 마커 식별자
     * @return 마커 객체
     */
    fun getMarker(id: String): Marker {
        // 이미 사용 중인 마커가 있으면 반환
        inUseMarkers[id]?.let { 
            Timber.v("마커 $id 는 이미 사용 중, 기존 마커 반환")
            return it 
        }
        
        // 풀이 비어있으면 확장
        if (pool.isEmpty()) {
            expandPool(expandSize)
        }
        
        // 풀에서 마커 가져오기
        val marker = pool.poll() ?: createNewMarker().also {
            totalCreated++
            Timber.d("풀이 비어 있어 새 마커 생성됨, 총 생성: $totalCreated")
        }
        
        // 마커 초기화 - 재사용 전에 항상 모든 속성 초기화
        resetMarker(marker)
        
        // 사용 중 마커로 등록
        inUseMarkers[id] = marker
        reusedCount++
        
        return marker
    }
    
    /**
     * 마커 초기화 (재사용 준비)
     * 모든 속성을 초기 상태로 설정합니다.
     */
    private fun resetMarker(marker: Marker) {
        // 먼저 명시적으로 태그 제거 (중요 - 이전 데이터 완전 삭제)
        val previousTag = marker.tag
        marker.tag = null
        
        marker.apply {
            map = null
            isVisible = false
            alpha = MarkerConstants.MARKER_ALPHA_DEFAULT
            position = LatLng(0.0, 0.0)
            width = MarkerConstants.MARKER_WIDTH
            height = MarkerConstants.MARKER_HEIGHT
            captionText = ""
            zIndex = 0
            isHideCollidedCaptions = true
            isHideCollidedMarkers = false
            onClickListener = null
        }
        
        // 디버깅: 이전 태그가 있었다면 로그 출력 (메모 정보 섞임 방지 확인용)
        if (previousTag != null) {
            Timber.v("마커 재사용 시 이전 태그 정리됨: ${previousTag::class.simpleName}")
        }
    }
    
    /**
     * 사용 완료된 마커를 풀에 반환합니다.
     * 이미 풀에 반환된 마커는 무시됩니다.
     * 
     * @param id 마커 식별자
     * @return 성공적으로 반환되었는지 여부
     */
    fun releaseMarker(id: String): Boolean {
        try {
            // 이미 풀에 반환된 마커인지 확인
            inUseMarkers.remove(id)?.let { marker ->
                // 마커 리스너 정리 먼저 수행 (마커 상태 변경 전)
                markerListenerManager.removeListener(id, marker)
                
                // resetMarker를 사용하여 마커 상태 완전 초기화
                resetMarker(marker)
                
                // 풀에 반환
                pool.add(marker)
                totalRecycled++
                Timber.d("마커 $id 풀에 반환됨 (총 풀 크기: ${pool.size}, 재활용: $totalRecycled)")
                return true
            }
            
            return false
        } catch (e: Exception) {
            Timber.e(e, "마커 $id 반환 중 오류 발생")
            return false
        }
    }
    
    /**
     * 모든 마커를 풀로 반환합니다.
     */
    fun releaseAllMarkers() {
        try {
            val markerCount = inUseMarkers.size
            var successCount = 0
            
            // 모든 마커 ID 복사 (ConcurrentModificationException 방지)
            val idsToRelease = inUseMarkers.keys.toList()

            idsToRelease.forEach { id ->
                // inUseMarkers에서 제거하고 해당 마커 가져오기
                inUseMarkers.remove(id)?.let { marker ->
                    try {
                        // 마커 리스너 정리 먼저 수행
                        markerListenerManager.removeListener(id, marker)
                        
                        // resetMarker를 사용하여 마커 상태 완전 초기화
                        resetMarker(marker)
                        
                        // 풀에 반환
                        pool.add(marker)
                        totalRecycled++
                        successCount++
                    } catch (e: Exception) {
                        Timber.e(e, "마커 $id 반환 중 오류 발생 (releaseAllMarkers)")
                    }
                }
            }
            
            // 사용 중 마커 맵 최종 초기화 (혹시 모를 잔여 데이터 제거)
            // inUseMarkers.clear() // 위에서 remove로 처리했으므로 필요 없을 수 있음, 확인 필요

            Timber.d("전체 마커 ${markerCount} 개 중 ${successCount} 개 풀에 반환 완료 (풀 크기: ${pool.size}, 재활용: $totalRecycled)")
            
            // 풀 크기가 너무 크면 일부 정리
            if (pool.size > initialPoolSize * 2) {
                trimPool(0.5f) // trimPool 내부 로직은 그대로 둔다고 가정
            }
        } catch (e: Exception) {
            Timber.e(e, "모든 마커 반환 중 오류 발생")
        }
    }
    
    /**
     * 메모리 부족 시 풀 정리
     * 미사용 마커 일부를 메모리에서 해제합니다.
     * 
     * @param retainPercent 유지할 마커 비율 (0.0 ~ 1.0)
     * @return 정리된 마커 수
     */
    fun trimPool(retainPercent: Float): Int {
        val currentPoolSize = pool.size
        // 최소 보존 크기: 초기 풀 크기의 절반 또는 10 중 큰 값
        val minRetainSize = max(initialPoolSize / 2, 10)
        val targetSize = max((currentPoolSize * retainPercent).toInt(), minRetainSize)
        
        if (currentPoolSize <= targetSize) {
            Timber.d("풀 정리 불필요: 현재 크기($currentPoolSize) <= 목표 크기($targetSize)")
            return 0
        }
        
        val markersToRemove = currentPoolSize - targetSize
        
        try {
            val removedMarkers = mutableListOf<Marker>()
            
            // 삭제할 마커 수만큼 풀에서 제거
            repeat(markersToRemove) {
                pool.poll()?.let { marker ->
                    // 마커의 모든 참조 정리
                    marker.map = null
                    marker.onClickListener = null
                    marker.tag = null
                    removedMarkers.add(marker)
                }
            }
            
            Timber.d("마커 풀 정리 완료: ${removedMarkers.size} 제거됨, 남은 크기: ${pool.size}")
            
            // 메모리 해제 힌트
            if (removedMarkers.size > 50) {
                Timber.d("대량 마커 해제")
            }
            
            return removedMarkers.size
        } catch (e: Exception) {
            Timber.e(e, "마커 풀 정리 중 오류 발생")
            return 0
        }
    }
    
    /**
     * 현재 사용 중인 마커 수를 반환합니다.
     * @return 사용 중인 마커 수
     */
    fun getInUseMarkerCount(): Int {
        return inUseMarkers.size
    }
    
    /**
     * 마커 풀 상태를 로그로 출력합니다.
     */
    fun logStatus() {
        Timber.d("==== 마커 풀 상태 ====")
        Timber.d("풀 크기: ${pool.size}개")
        Timber.d("사용 중인 마커: ${inUseMarkers.size}개")
        Timber.d("총 생성된 마커: ${totalCreated}개")
        Timber.d("총 재활용된 마커: ${totalRecycled}개")
        Timber.d("재사용률: ${if (totalCreated > 0) (reusedCount.toFloat() / totalCreated) * 100 else 0}%")
        Timber.d("===================")
    }
    
    /**
     * 객체 소멸 시 자원 정리
     */
    fun cleanup() {
        try {
            Timber.d("마커 풀 자원 정리 시작")
            
            // 모든 사용 중인 마커 정리
            inUseMarkers.forEach { (id, marker) ->
                try {
                    marker.map = null
                    marker.onClickListener = null
                    marker.tag = null
                } catch (e: Exception) {
                    Timber.e(e, "마커 $id 정리 중 오류 발생")
                }
            }
            inUseMarkers.clear()
            
            // 풀에 있는 마커 정리
            var count = 0
            while (pool.isNotEmpty() && count < 1000) { // 무한 루프 방지
                pool.poll()?.let { marker ->
                    try {
                        marker.map = null
                        marker.onClickListener = null
                        marker.tag = null
                    } catch (e: Exception) {
                        Timber.e(e, "풀 마커 정리 중 오류")
                    }
                    count++
                }
            }
            pool.clear() // 혹시 남아있을 경우를 대비해 완전 정리
            
            // 통계 변수 초기화
            totalCreated = 0
            totalRecycled = 0
            reusedCount = 0
            
            Timber.d("마커 풀 자원 정리 완료: 총 ${count} 개 마커 정리됨")
            
            Timber.d("마커 풀 자원 정리 종료")
        } catch (e: Exception) {
            Timber.e(e, "마커 풀 자원 정리 중 오류 발생")
        }
    }
    
    /**
     * 메모리 부족 상황에 대응하기 위한 긴급 정리
     * 시스템 메모리가 부족할 때 호출
     */
    fun emergencyCleanup() {
        try {
            Timber.w("마커 풀 긴급 정리 시작")
            
            // 1. 풀의 크기를 최소화 (초기 풀 크기의 25%만 유지)
            val currentPoolSize = pool.size
            val targetSize = max(initialPoolSize / 4, 10)
            
            if (currentPoolSize > targetSize) {
                val markersToRemove = currentPoolSize - targetSize
                repeat(markersToRemove) {
                    pool.poll()?.apply {
                        map = null
                        onClickListener = null
                        tag = null
                    }
                }
                Timber.d("풀 긴급 정리: ${markersToRemove} 개 마커 제거됨")
            }
            
            // 2. 사용 중인 마커 중 숨겨진 마커 수집 (비표시 마커)
            val hiddenMarkers = inUseMarkers.filterValues { it.map == null }
            
            // 3. 숨겨진 마커 중 절반을 풀로 반환하여 재사용 가능하게 함
            val markersToRelease = hiddenMarkers.keys.take(hiddenMarkers.size / 2)
            
            markersToRelease.forEach { id ->
                releaseMarker(id)
            }
            
            Timber.d("숨겨진 마커 ${markersToRelease.size}개를 긴급 반환")
            
            Timber.w("마커 풀 긴급 정리 완료")
        } catch (e: Exception) {
            Timber.e(e, "마커 풀 긴급 정리 중 오류 발생")
        }
    }
    
    /**
     * 마커 풀 상태를 분석하여 최적화 제안
     * @return 최적화 가능 여부
     */
    fun analyzePoolUsage(): Boolean {
        try {
            val inUseCount = inUseMarkers.size
            val poolSize = pool.size
            val totalMarkers = inUseCount + poolSize
            
            // 재사용률 계산
            val reuseRate = if (totalCreated > 0) reusedCount.toFloat() / totalCreated else 0f
            
            // 풀 사용률 계산
            val poolUsageRate = if (totalMarkers > 0) inUseCount.toFloat() / totalMarkers else 0f
            
            Timber.d("마커 풀 분석:")
            Timber.d("- 총 마커: ${totalMarkers} 개 (사용 중: $inUseCount, 풀: $poolSize)")
            Timber.d("- 재사용률: ${reuseRate * 100}%")
            Timber.d("- 풀 사용률: ${poolUsageRate * 100}%")
            
            // 최적화 필요 조건: 
            // 1. 풀이 너무 크거나 (초기 크기의 3배 이상)
            // 2. 사용률이 30% 미만이거나
            // 3. 재사용률이 50% 미만인 경우
            val needsOptimization = poolSize > initialPoolSize * 3 || 
                                   poolUsageRate < 0.3f || 
                                   reuseRate < 0.5f
            
            if (needsOptimization) {
                Timber.d("마커 풀 최적화가 권장됩니다")
            }
            
            return needsOptimization
        } catch (e: Exception) {
            Timber.e(e, "마커 풀 분석 중 오류 발생")
            return false
        }
    }
    
    /**
     * 마커 풀 사용 통계 제공
     * @return 풀 사용 통계 정보
     */
    fun getPoolStatistics(): Map<String, Any> {
        return mapOf(
            "poolSize" to pool.size,
            "inUseMarkers" to inUseMarkers.size,
            "totalCreated" to totalCreated,
            "totalRecycled" to totalRecycled,
            "reuseRate" to if (totalCreated > 0) (reusedCount.toFloat() / totalCreated) * 100 else 0f,
            "memoryEfficiency" to if (totalCreated > 0) (totalRecycled.toFloat() / totalCreated) * 100 else 0f
        )
    }
} 