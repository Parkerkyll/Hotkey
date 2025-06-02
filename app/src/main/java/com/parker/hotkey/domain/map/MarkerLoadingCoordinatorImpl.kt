package com.parker.hotkey.domain.map

import com.parker.hotkey.domain.repository.MarkerRepository
import com.parker.hotkey.presentation.map.markers.MarkerUIDelegate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.parker.hotkey.domain.repository.MarkerQueryOptions
import com.parker.hotkey.domain.constants.GeohashConstants
import com.parker.hotkey.domain.util.AppStateManager

/**
 * 마커 로딩 작업을 조율하는 코디네이터 구현체
 * 모든 마커 로딩 요청을 일원화하여 중복 로딩을 방지합니다.
 */
@Singleton
class MarkerLoadingCoordinatorImpl @Inject constructor(
    private val markerRepository: MarkerRepository,
    private val markerUIDelegate: MarkerUIDelegate,
    private val appStateManager: AppStateManager
) : MarkerLoadingCoordinator {

    private val loadingMutex = Mutex()
    private val lastLoadTime = AtomicLong(0L)
    private val MIN_LOADING_INTERVAL = 500L // 최소 로딩 간격 (밀리초)

    /**
     * 지정된 지역의 마커를 로딩합니다.
     */
    override suspend fun loadMarkers(geohash: String, neighbors: List<String>, zoom: Double, forceRefresh: Boolean): Boolean {
        // 네비게이션 복귀 상태인 경우 API 호출을 방지
        if (appStateManager.isNavigationReturn()) {
            Timber.d("네비게이션 복귀 상태에서 API 호출 방지: geohash=$geohash")
            return false
        }
        
        Timber.d("마커 로딩 요청: geohash=$geohash, neighbors=${neighbors.size}개, zoom=$zoom, forceRefresh=$forceRefresh")
        
        // 중복 작업 방지 로직
        if (!forceRefresh) {
            // 최소 로딩 간격 확인
            val now = System.currentTimeMillis()
            if (now - lastLoadTime.get() < MIN_LOADING_INTERVAL) {
                Timber.d("마커 로딩 간격 제한으로 스킵: ${now - lastLoadTime.get()}ms (최소: ${MIN_LOADING_INTERVAL}ms)")
                return false
            }
        }
        
        // 마커 로딩 작업 실행
        return loadingMutex.withLock {
            try {
                lastLoadTime.set(System.currentTimeMillis())
                Timber.d("마커 로딩 시작: $geohash, ${neighbors.size}개 이웃 지역")
                val startTime = System.currentTimeMillis()
                
                // 줌 레벨에 기반한 정밀도 계산
                val precision = calculatePrecisionFromZoom(zoom) 
                
                // 마커 쿼리 옵션 생성
                val options = MarkerQueryOptions(
                    precision = precision,
                    limit = 200 // 기본 제한값
                )
                
                // Repository에서 마커 조회
                val markers = markerRepository.getMarkersSync(geohash, neighbors, options)
                
                // UI 스레드에서 마커 업데이트 (메인 스레드에서 UI 작업 필요)
                withContext(Dispatchers.Main) {
                    markerUIDelegate.updateMarkers(markers)
                }
                
                val duration = System.currentTimeMillis() - startTime
                Timber.d("마커 로딩 완료: ${markers.size}개 마커, ${duration}ms 소요")
                true
            } catch (e: Exception) {
                Timber.e(e, "마커 로딩 중 오류 발생")
                false
            }
        }
    }
    
    /**
     * 줌 레벨에 따른 적절한 지오해시 정밀도 계산
     * 더 멀리 볼수록(낮은 줌 레벨) 낮은 정밀도 사용
     */
    private fun calculatePrecisionFromZoom(zoom: Double): Int {
        return when {
            zoom >= 16.0 -> 8  // 매우 가까이 (상세 정밀도)
            zoom >= 13.0 -> 7  // 가까이
            zoom >= 10.0 -> 6  // 중간 거리
            zoom >= 8.0 -> 5   // 도시 레벨
            zoom >= 5.0 -> 4   // 지역 레벨
            else -> 3          // 국가 레벨
        }
    }
    
    /**
     * UI에 표시된 마커를 새로고침합니다.
     */
    override suspend fun refreshMarkersUI() {
        Timber.d("마커 UI 새로고침 요청")
        try {
            withContext(Dispatchers.Main) {
                markerUIDelegate.refreshMarkersUI()
                Timber.d("마커 UI 새로고침 완료")
            }
        } catch (e: Exception) {
            Timber.e(e, "마커 UI 새로고침 중 오류 발생")
        }
    }
    
    /**
     * 앱 최초 시작 시 초기 마커 로딩
     */
    override suspend fun initialLoadMarkers(geohash: String, neighbors: List<String>, zoom: Double) {
        // 네비게이션 복귀 상태 확인
        if (appStateManager.isNavigationReturn()) {
            Timber.d("네비게이션 복귀 상태에서 초기 마커 로딩 요청 무시")
            return
        }
        
        Timber.d("앱 최초 시작 시 마커 로딩 요청: geohash=$geohash, neighbors=${neighbors.size}개")
        loadMarkers(geohash, neighbors, zoom, forceRefresh = true)
    }
    
    /**
     * 백그라운드에서 포그라운드로 전환 시 마커 새로고침
     */
    override suspend fun foregroundRefreshMarkers(geohash: String, neighbors: List<String>, zoom: Double) {
        // 네비게이션 복귀 상태 확인
        if (appStateManager.isNavigationReturn()) {
            Timber.d("네비게이션 복귀 상태에서 포그라운드 마커 새로고침 요청 무시")
            
            // UI만 새로고침 (API 호출 없음)
            withContext(Dispatchers.Main) {
                markerUIDelegate.refreshMarkersUI()
            }
            return
        }
        
        Timber.d("포그라운드 전환 시 마커 새로고침 요청: geohash=$geohash, neighbors=${neighbors.size}개")
        
        // UI 새로고침 제거 (MapFragment에서 이미 markerUIDelegate.handleForegroundTransition() 호출함)
        // refreshMarkersUI() <- 이 호출 제거
        
        // 필요시 데이터 갱신 (최소 10초 이상 지났을 때만)
        if (System.currentTimeMillis() - lastLoadTime.get() > 10000) {
            // 데이터는 조용히 백그라운드에서 갱신 (forceRefresh=false로 변경)
            loadMarkers(geohash, neighbors, zoom, forceRefresh = false)
        } else {
            Timber.d("최근에 마커 로딩했으므로 포그라운드 전환 시 데이터 새로고침 스킵")
        }
    }
    
    /**
     * 새로운 지역 방문 시 마커 로딩
     */
    override suspend fun loadMarkersForNewArea(geohash: String, neighbors: List<String>, zoom: Double) {
        // 네비게이션 복귀 상태 확인
        if (appStateManager.isNavigationReturn()) {
            Timber.d("네비게이션 복귀 상태에서 새 지역 마커 로딩 요청 무시")
            return
        }
        
        Timber.d("새로운 지역 방문 시 마커 로딩 요청: geohash=$geohash, neighbors=${neighbors.size}개")
        loadMarkers(geohash, neighbors, zoom)
    }
} 