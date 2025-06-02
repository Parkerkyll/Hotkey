package com.parker.hotkey.presentation.map.markers

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.Overlay
import com.parker.hotkey.domain.model.Marker as DomainMarker
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import com.parker.hotkey.domain.constants.MarkerConstants.MARKER_ALPHA_DEFAULT
import com.parker.hotkey.domain.constants.MarkerConstants.MARKER_WIDTH
import com.parker.hotkey.domain.constants.MarkerConstants.MARKER_HEIGHT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.parker.hotkey.util.GeoHashUtil
import com.parker.hotkey.domain.constants.GeohashConstants
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlin.math.abs
import java.lang.ref.WeakReference
import com.naver.maps.map.MapView
import com.parker.hotkey.util.LifecycleAware

/**
 * 마커 UI 관리를 담당하는 클래스입니다.
 * NaverMap API를 사용하여 마커를 생성, 업데이트, 삭제하고 시각적 속성을 관리합니다.
 */
@Singleton
class MarkerUIDelegate @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val markerPool: MarkerPool, // 마커 풀 주입
    private val markerListenerManager: MarkerListenerManager // 마커 리스너 관리자 주입
) : LifecycleAware {
    // NaverMap을 약한 참조로 변경
    private var weakNaverMap: WeakReference<NaverMap>? = null
    
    // MapView 약한 참조 추가
    private var weakMapView: WeakReference<MapView>? = null
    
    private val markers = ConcurrentHashMap<String, Marker>()
    // 도메인 마커 데이터 맵 추가 - 마커 ID를 키로, 도메인 마커 객체를 값으로 저장
    private val markerDataMap = ConcurrentHashMap<String, DomainMarker>()
    private var onMarkerClickListener: ((DomainMarker) -> Boolean)? = null
    private var pendingMarkers = mutableListOf<DomainMarker>()
    private var isInitialized = false
    private val pendingRemovalMarkerIds = mutableListOf<String>()
    
    // 내부 작업용 스코프는 SupervisorJob 사용
    private val delegateScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 프래그먼트 생명주기와 연동하기 위한 임시 스코프
    private var fragmentScope: CoroutineScope? = null
    
    // 활성화 상태 추적
    private var isActive = false
    
    // NaverMap 획득 메서드
    private fun getNaverMap(): NaverMap? {
        return weakNaverMap?.get()
    }
    
    // MapView 획득 메서드 추가
    private fun getMapView(): MapView? {
        return weakMapView?.get()
    }

    /**
     * MapView 설정 (약한 참조)
     */
    fun setMapView(mapView: MapView) {
        Timber.d("MapView 약한 참조 설정")
        this.weakMapView = WeakReference(mapView)
    }

    /**
     * 프래그먼트 생명주기 스코프 설정
     */
    fun setLifecycleScope(scope: CoroutineScope) {
        Timber.d("생명주기 스코프가 설정됨")
        fragmentScope = scope
    }
    
    /**
     * 컴포넌트가 시작될 때 호출됩니다.
     * 마커 UI 관련 리소스를 초기화하고 작업을 재개합니다.
     */
    override fun onStart() {
        Timber.d("MarkerUIDelegate onStart - 활성화됨")
        isActive = true
        
        // 지연된 작업 처리
        processPendingOperations()
    }
    
    /**
     * 컴포넌트가 중지될 때 호출됩니다.
     * 마커 UI 관련 리소스를 최소화하고 작업을 중단합니다.
     */
    override fun onStop() {
        Timber.d("MarkerUIDelegate onStop - 비활성화됨")
        isActive = false
        
        // 현재 진행 중인 비필수 작업 취소
        delegateScope.coroutineContext.cancelChildren()
    }
    
    /**
     * 컴포넌트가 파괴될 때 호출됩니다.
     * 모든 마커 UI 관련 리소스를 정리합니다.
     */
    override fun onDestroy() {
        Timber.d("MarkerUIDelegate onDestroy - 리소스 정리")
        cleanup()
    }
    
    /**
     * 보류 중인 마커 작업을 처리합니다.
     */
    private fun processPendingOperations() {
        if (!isActive || !isInitialized) return
        
        // NaverMap이 설정되면 대기 중인 마커들을 처리
        if (pendingMarkers.isNotEmpty() && getNaverMap() != null) {
            Timber.d("대기 중인 마커 처리 시작: ${pendingMarkers.size}개")
            // 깜박임 방지를 위해 차분 업데이트 메서드 사용
            updateMarkersWithDiff(pendingMarkers)
            pendingMarkers.clear()
        }
        
        // 대기 중인 마커 삭제 요청 처리
        if (pendingRemovalMarkerIds.isNotEmpty() && getNaverMap() != null) {
            Timber.d("대기 중인 마커 삭제 요청 처리: ${pendingRemovalMarkerIds.size}개")
            pendingRemovalMarkerIds.forEach { id ->
                removeMarker(id)
            }
            pendingRemovalMarkerIds.clear()
        }
    }
    
    /**
     * 리소스 정리
     */
    fun cleanup() {
        Timber.d("MarkerUIDelegate 리소스 정리 중")
        delegateScope.coroutineContext.cancelChildren()
        // 약한 참조화를 위해 리스너 제거
        onMarkerClickListener = null
        // NaverMap 참조 제거
        weakNaverMap = null
        // MapView 참조 제거
        weakMapView = null
        // 프래그먼트 스코프 제거
        fragmentScope = null
        // 기타 정리 작업
        markerDataMap.clear()
        
        // 활성화 상태 변경
        isActive = false
    }

    /**
     * NaverMap 객체를 설정하고 초기화합니다.
     * 초기화 후 대기 중인 마커들을 처리합니다.
     */
    fun setNaverMap(map: NaverMap) {
        Timber.d("NaverMap 설정 시작")
        weakNaverMap = WeakReference(map)
        isInitialized = true
        Timber.d("NaverMap 설정됨 - 초기화 완료")
        
        // NaverMap이 설정되면 대기 중인 마커들을 처리
        if (pendingMarkers.isNotEmpty()) {
            Timber.d("대기 중인 마커 처리 시작: ${pendingMarkers.size}개")
            // 깜박임 방지를 위해 차분 업데이트 메서드 사용
            updateMarkersWithDiff(pendingMarkers)
            pendingMarkers.clear()
        }
        
        // 대기 중인 마커 삭제 요청 처리
        if (pendingRemovalMarkerIds.isNotEmpty()) {
            Timber.d("대기 중인 마커 삭제 요청 처리: ${pendingRemovalMarkerIds.size}개")
            pendingRemovalMarkerIds.forEach { id ->
                removeMarker(id)
            }
            pendingRemovalMarkerIds.clear()
        }
    }

    /**
     * 마커 클릭 리스너를 설정합니다.
     */
    fun setOnMarkerClickListener(listener: (DomainMarker) -> Boolean) {
        this.onMarkerClickListener = listener
    }

    /**
     * 마커들을 업데이트합니다. (차분 업데이트 메서드로 리다이렉트)
     */
    fun updateMarkers(domainMarkers: List<DomainMarker>) {
        // 차분 업데이트 메서드로 리다이렉트
        updateMarkersWithDiff(domainMarkers)
    }
    
    /**
     * 마커들을 최적화된 방식으로 업데이트합니다.
     * 기존 호환성을 위해 유지하되, 내부적으로는 차분 업데이트 방식 사용
     */
    fun updateMarkersOptimized(domainMarkers: List<DomainMarker>) {
        // 차분 업데이트로 리다이렉트하여 깜박임 방지
        Timber.d("updateMarkersOptimized가 호출됨 - 내부적으로 차분 업데이트 메서드로 리다이렉트")
        updateMarkersWithDiff(domainMarkers)
    }
    
    /**
     * 모든 마커에 클릭 리스너가 설정되어 있는지 확인하고, 없으면 설정합니다.
     * 특히 새로 생성된 마커의 클릭 리스너 설정을 보장합니다.
     */
    private fun ensureAllMarkersHaveClickListeners() {
        try {
            var fixed = 0
            markers.forEach { (id, marker) ->
                if (!hasClickListener(id)) {
                    Timber.d("마커 ${id}에 클릭 리스너가 없음 - 리스너 설정 중")
                    val domainMarker = markerDataMap[id]
                    if (domainMarker != null) {
                        refreshMarkerClickListener(id, marker, domainMarker)
                        fixed++
                    } else {
                        Timber.w("마커 ${id}의 도메인 데이터가 없어 클릭 리스너를 설정할 수 없음")
                    }
                }
            }
            if (fixed > 0) {
                Timber.d("${fixed}개 마커에 클릭 리스너를 새로 설정함")
            }
        } catch (e: Exception) {
            Timber.e(e, "마커 클릭 리스너 확인 중 오류 발생")
        }
    }
    
    /**
     * 마커에 클릭 리스너가 설정되어 있는지 확인합니다.
     */
    private fun hasClickListener(markerId: String): Boolean {
        return markerListenerManager.hasListener(markerId)
    }
    
    /**
     * 마커의 클릭 리스너를 강화된 방식으로 새로 설정합니다.
     * 약한 참조를 사용하여 메모리 누수를 방지합니다.
     */
    private fun refreshMarkerClickListener(id: String, marker: Marker, domainMarker: DomainMarker) {
        // MarkerListenerManager를 사용하여 리스너 설정
        markerListenerManager.setClickListener(id, marker, onMarkerClickListener, domainMarker)
    }

    /**
     * 도메인 마커를 생성하거나 업데이트합니다.
     */
    private fun createOrUpdateMarker(domainMarker: DomainMarker) {
        val map = getNaverMap() ?: return
        
        // 마커 풀을 사용하여 마커 가져오기
        val marker = markerPool.getMarker(domainMarker.id)
        
        // 마커 속성 설정
        marker.position = LatLng(domainMarker.position.latitude, domainMarker.position.longitude)
        
        // 중요: 마커 데이터 맵 먼저 업데이트
        markerDataMap[domainMarker.id] = domainMarker
        
        // 클릭 리스너 설정 - 리스너 상태 추적 맵을 활용하여 최적화
        if (!hasClickListener(domainMarker.id)) {
            refreshMarkerClickListener(domainMarker.id, marker, domainMarker)
        }
        
        // 마커 맵에 추가
        markers[domainMarker.id] = marker
        
        // 마커가 맵에 표시되도록 설정 (map 속성은 null일 때만 설정)
        if (marker.map == null) {
            marker.map = map
            marker.isVisible = true // 명시적으로 isVisible = true 설정 추가
        }
        
        Timber.d("마커 생성/업데이트 완료: ${domainMarker.id}, 위치: (${domainMarker.position.latitude}, ${domainMarker.position.longitude}), Visible: ${marker.isVisible}")
    }

    /**
     * 마커를 삭제합니다.
     */
    fun removeMarker(id: String) {
        try {
            if (!isInitialized) {
                Timber.d("NaverMap이 초기화되지 않았으므로 마커 $id 삭제를 대기열에 추가합니다.")
                pendingRemovalMarkerIds.add(id)
                return
            }
            
            // 마커 찾기
            markers.remove(id)?.let { _ ->
                Timber.d("마커 $id 삭제 진행")
                
                // 마커 풀에 반환
                markerPool.releaseMarker(id)
                
                // 도메인 데이터 맵에서 제거
                markerDataMap.remove(id)
                
                // 마커 리스너 정리
                markerListenerManager.cleanupMarker(id)
                
                return
            }
            
            Timber.d("Cannot find marker $id to delete")
        } catch (e: Exception) {
            Timber.e(e, "마커 $id 삭제 중 오류 발생")
        }
    }

    /**
     * 모든 마커를 삭제합니다.
     */
    fun removeAllMarkers() {
        Timber.d("모든 마커 삭제 시작")
        try {
            // 모든 마커 ID 복사 (동시 수정 오류 방지)
            val allIds = markers.keys.toList()
            
            // 각 마커 삭제
            allIds.forEach { id ->
                removeMarker(id)
            }
            
            // 모든 마커 컬렉션 초기화
            markers.clear()
            markerDataMap.clear()
            
            // 마커 풀 전체 반환
            markerPool.releaseAllMarkers()
            
            // 마커 리스너 전체 정리
            markerListenerManager.cleanupAllListeners()
            
            Timber.d("모든 마커 삭제 완료")
        } catch (e: Exception) {
            Timber.e(e, "모든 마커 삭제 중 오류 발생")
        }
    }

    /**
     * 현재 관리 중인 마커 맵을 반환합니다.
     */
    fun getMarkers(): Map<String, Marker> = markers.toMap()
    
    /**
     * 맵이 초기화되었는지 확인합니다.
     */
    fun isMapInitialized(): Boolean = isInitialized

    /**
     * 모든 마커의 투명도를 업데이트합니다.
     * 
     * @param opacity 마커 투명도 (0.0f ~ 1.0f)
     */
    fun updateMarkerOpacity(opacity: Float) {
        try {
            if (!isInitialized) {
                Timber.d("NaverMap이 초기화되지 않아 마커 투명도 업데이트를 대기열에 추가합니다.")
                return
            }
            
            val clampedOpacity = opacity.coerceIn(0.0f, 1.0f)
            Timber.d("마커 투명도 업데이트: $clampedOpacity")
            
            // UI 스레드에서 실행
            runOnUiThreadSafely {
                markers.forEach { (_, marker) ->
                    marker.alpha = clampedOpacity
                }
                Timber.d("${markers.size}개 마커의 투명도가 업데이트되었습니다.")
            }
        } catch (e: Exception) {
            Timber.e(e, "마커 투명도 업데이트 중 오류 발생")
        }
    }

    /**
     * 마커 클릭 리스너를 맵과 함께 설정합니다.
     * 기존 마커들의 클릭 리스너도 다시 설정합니다.
     */
    fun setupMarkerClickListener(map: NaverMap, onMarkerClick: (DomainMarker) -> Boolean) {
        Timber.d("마커 클릭 리스너 설정 시작")
        weakNaverMap = WeakReference(map)
        this.onMarkerClickListener = { domainMarker -> 
            onMarkerClick(domainMarker)
        }
        isInitialized = true
        
        // 기존 마커들에 대해 클릭 리스너 다시 설정
        markers.forEach { (id, uiMarker) ->
            // markerData 맵에서 해당 마커의 도메인 데이터 찾기
            val domainMarker = markerDataMap[id]
            
            if (domainMarker != null) {
                // 리스너 강제로 초기화
                uiMarker.onClickListener = null
                
                // 새로운 방식으로 클릭 리스너 설정 - 항상 최신 데이터 참조하도록 변경
                refreshMarkerClickListener(id, uiMarker, domainMarker)
                
                // 마커가 맵에 표시되어 있는지 확인하고, 없으면 표시
                if (uiMarker.map == null) {
                    uiMarker.map = map
                }
                
                Timber.d("기존 마커의 클릭 리스너 재설정: $id")
            } else {
                Timber.w("마커 데이터를 찾을 수 없음: $id")
                
                // 데이터가 없는 마커는 리스너 초기화 및 로깅만 수행
                uiMarker.onClickListener = null
            }
        }
        
        // 리스너 설정 후 대기 중인 마커들 처리
        if (pendingMarkers.isNotEmpty()) {
            Timber.d("리스너 설정 후 대기 중인 마커 처리: ${pendingMarkers.size}개")
            updateMarkers(pendingMarkers)
            pendingMarkers.clear()
        }
        
        // 대기 중인 마커 삭제 요청 처리
        if (pendingRemovalMarkerIds.isNotEmpty()) {
            Timber.d("리스너 설정 후 대기 중인 마커 삭제 요청 처리: ${pendingRemovalMarkerIds.size}개")
            pendingRemovalMarkerIds.forEach { id ->
                removeMarker(id)
            }
            pendingRemovalMarkerIds.clear()
        }
    }
    
    /**
     * 특정 지오해시 영역 내에 있는 마커만 표시합니다.
     * 현재 위치의 geohash와 이웃 geohash 영역에 있는 마커만 화면에 표시합니다.
     * 
     * @param currentGeohash 현재 위치의 geohash6
     * @param neighbors 이웃 geohash6 목록
     */
    fun updateMarkersVisibility(currentGeohash: String?, neighbors: List<String>) {
        try {
            if (!isInitialized || getNaverMap() == null) {
                Timber.d("NaverMap이 초기화되지 않아 마커 가시성 업데이트를 스킵합니다.")
                return
            }
            
            if (currentGeohash == null) {
                Timber.d("현재 geohash가 null이라 마커 가시성 업데이트를 스킵합니다.")
                return
            }
            
            val validGeohashes = neighbors + currentGeohash
            
            Timber.d("===== 마커 가시성 업데이트 시작 =====")
            Timber.d("현재 geohash: $currentGeohash")
            Timber.d("이웃 geohash 목록: ${neighbors.joinToString(", ")}")
            
            // 마커가 없는 경우 처리
            if (markers.isEmpty()) {
                Timber.d("화면에 표시할 마커가 없습니다 (UI 마커 목록이 비어 있음)")
                return
            }
            
            updateMarkersVisibilityInternal(validGeohashes)
            
        } catch (e: Exception) {
            Timber.e(e, "마커 가시성 업데이트 중 오류 발생: ${e.message}")
        }
    }
    
    /**
     * 마커 가시성 업데이트를 수행하는 내부 메서드
     * updateMarkersVisibility 및 updateMarkersOptimized에서 재사용 가능한 공통 로직
     * 
     * @param validGeohashes 유효한 geohash 목록
     */
    private fun updateMarkersVisibilityInternal(validGeohashes: List<String>) {
        // 각 마커의 geohash 정보 수집
        val markerGeohashMap = mutableMapOf<String, String>() // markerId -> geohash
        val geohashToMarkersMap = mutableMapOf<String, MutableList<String>>() // geohash -> markerIds
        
        markers.forEach { (id, uiMarker) ->
            val markerGeohash = GeoHashUtil.encode(uiMarker.position.latitude, uiMarker.position.longitude, GeohashConstants.GEOHASH_PRECISION)
            markerGeohashMap[id] = markerGeohash
            
            geohashToMarkersMap.getOrPut(markerGeohash) { mutableListOf() }.add(id)
        }
        
        // geohash별 마커 수 로깅
        Timber.d("Geohash별 마커 분포:")
        geohashToMarkersMap.forEach { (geohash, markerIds) ->
            val isValidGeohash = geohash in validGeohashes
            Timber.d("- $geohash: ${markerIds.size}개 마커 (유효: $isValidGeohash)")
        }
        
        // 로그 출력 개선
        val visibleCount = markers.count { (id, _) ->
            val markerGeohash = markerGeohashMap[id] ?: return@count false
            markerGeohash in validGeohashes
        }
        
        Timber.d("전체 ${markers.size}개 중 ${visibleCount}개 마커가 표시 영역에 있음")
        
        // 내 위치 기준 geohash6 영역 내 마커만 표시 (일괄 처리 방식으로 변경)
        val visibilityUpdateBatch = markers.map { (markerId, uiMarker) ->
            // 마커 위치의 geohash 가져오기
            val markerGeohash = markerGeohashMap[markerId] ?: run {
                Timber.w("Marker ID $markerId has no geohash information")
                return@map Triple(markerId, uiMarker, false) // 기본적으로 보이지 않음
            }
            
            // 내 위치 영역 내의 마커만 표시
            val isInCurrentArea = markerGeohash in validGeohashes
            Triple(markerId, uiMarker, isInCurrentArea)
        }
        
        // 일괄 처리로 마커 표시/숨김 처리 (깜박임 방지)
        runOnUiThreadSafely {
            val currentMap = getNaverMap() // NaverMap 인스턴스를 한 번만 가져옴
            if (currentMap == null) {
                Timber.w("NaverMap 인스턴스가 null이므로 마커 가시성 업데이트를 중단합니다.")
                return@runOnUiThreadSafely
            }

            visibilityUpdateBatch.forEach { (markerId, uiMarker, isInCurrentArea) ->
                // 1. 마커가 아직 지도에 추가되지 않았다면 추가 (최초 또는 풀에서 가져온 경우)
                if (uiMarker.map == null && isInCurrentArea) {
                    uiMarker.map = currentMap
                    uiMarker.isVisible = true // 명시적으로 true 설정
                    Timber.d("Marker $markerId added to map and set visible.")
                } 
                // 2. 마커가 이미 지도에 있다면 isVisible 속성만 변경
                else if (uiMarker.map != null) {
                    if (uiMarker.isVisible != isInCurrentArea) {
                        uiMarker.isVisible = isInCurrentArea
                        Timber.d("Marker $markerId visibility changed to: $isInCurrentArea")
                    }
                }
                // 3. 마커가 지도에 없고, 보이지 않아야 하는 경우는 아무것도 안 함 (이미 숨겨진 상태)
            }
        }
        
        Timber.d("===== 마커 가시성 업데이트 완료 =====")
    }
    
    /**
     * UI 스레드에서 작업을 실행합니다.
     * 
     * @param action 실행할 작업
     */
    private fun runOnUiThreadSafely(action: () -> Unit) {
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                action()
            } else {
                Handler(Looper.getMainLooper()).post {
                    try {
                        action()
                    } catch (e: Exception) {
                        Timber.e(e, "UI 스레드 작업 중 오류 발생")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "UI 스레드 실행 메서드 호출 중 오류 발생")
        }
    }

    /**
     * 마커 풀 상태를 로깅합니다.
     */
    fun logPoolStatus() {
        markerPool.logStatus()
    }

    /**
     * 모든 마커의 클릭 가능 상태를 확인합니다.
     * @return 클릭 문제가 있는 마커의 수
     */
    fun checkAllMarkersClickable(): Int {
        if (getNaverMap() == null) return 0
        
        var problemCount = 0
        try {
            val startTime = System.currentTimeMillis()
            Timber.d("모든 마커 클릭 상태 확인 시작")
            
            markers.forEach { (id, marker) ->
                // isClickable 대신 hasClickListener 메서드를 사용하여 클릭 가능 여부 확인
                if (!hasClickListener(id)) {
                    Timber.w("마커 ${id}: 클릭 불가능 상태 발견")
                    problemCount++
                    // 클릭 리스너가 없으면 리스너 재설정 시도
                    val domainMarker = markerDataMap[id]
                    if (domainMarker != null) {
                        refreshMarkerClickListener(id, marker, domainMarker)
                    } else {
                        Timber.w("마커 ${id}: 도메인 데이터 없음")
                    }
                }
            }
            
            val duration = System.currentTimeMillis() - startTime
            Timber.d("마커 클릭 상태 확인 완료: ${problemCount}개 문제 발견, 소요시간: ${duration}ms")
        } catch (e: Exception) {
            Timber.e(e, "마커 클릭 상태 확인 중 오류 발생")
        }
        
        return problemCount
    }
    
    /**
     * 특정 ID의 도메인 마커를 반환합니다.
     */
    private fun getDomainMarkerById(id: String): DomainMarker? {
        return markerDataMap[id]
    }

    /**
     * 마커 데이터 변경 여부 확인
     */
    private fun hasMarkerDataChanged(id: String, marker: Marker, newData: DomainMarker): Boolean {
        try {
            // 위치 변경 확인 (가장 일반적인 변경)
            val currentPosition = marker.position
            val newPosition = LatLng(newData.position.latitude, newData.position.longitude)
            val positionChanged = abs(currentPosition.latitude - newPosition.latitude) > 0.00001 || 
                                 abs(currentPosition.longitude - newPosition.longitude) > 0.00001
            
            // 빠른 경로: 위치가 변경되었으면 다른 속성 확인 안 함
            if (positionChanged) return true
            
            // 기타 속성 변경 확인
            val existingData = markerDataMap[id]
            val attributesChanged = existingData?.let {
                it.userId != newData.userId ||           // 사용자 ID 변경
                it.modifiedAt != newData.modifiedAt ||   // 수정 시간 변경
                it.memos.size != newData.memos.size    // 메모 개수 변경
            } ?: true // 기존 데이터가 없으면 변경된 것으로 간주
            
            return attributesChanged
        } catch (e: Exception) {
            Timber.e(e, "마커 데이터 변경 확인 중 오류: $id")
            return true // 오류 시 안전하게 변경된 것으로 간주
        }
    }

    /**
     * 앱 메모리 부족 시 마커 리소스 최적화
     * MemoryTrimLevel에 따라 다양한 수준의 최적화 적용
     */
    fun onMemoryLow(isAggressive: Boolean) {
        try {
            Timber.w("메모리 부족 상황 감지 - 마커 리소스 정리 ${if (isAggressive) "(적극적)" else "(일반)"}")
            
            // 사용하지 않는 마커를 풀에 반환
            if (isAggressive) {
                // 적극적 정리: 모든 마커 해제 후 현재 필요한 것만 다시 로드
                removeAllMarkers()
            } else {
                // 일반 정리: 화면에 보이지 않는 마커만 해제
                pruneInvisibleMarkers()
            }
        } catch (e: Exception) {
            Timber.e(e, "메모리 부족 대응 중 오류 발생")
        }
    }
    
    /**
     * 화면에 보이지 않는 마커를 제거하여 메모리를 확보합니다.
     */
    private fun pruneInvisibleMarkers() {
        try {
            if (getNaverMap() == null) return
            
            // 현재 화면에 보이는 마커만 유지
            val visibleMarkerIds = mutableSetOf<String>()
            markers.forEach { (id, marker) ->
                if (marker.map != null) {
                    visibleMarkerIds.add(id)
                }
            }
            
            // 화면에 보이지 않는 마커 제거 (풀에 반환)
            val markersToRemove = markers.keys.filter { it !in visibleMarkerIds }
            
            Timber.d("화면에 보이지 않는 마커 정리: 총 ${markers.size}개 중 ${markersToRemove.size}개")
            
            markersToRemove.forEach { id ->
                val marker = markers.remove(id) ?: return@forEach
                
                // 마커 풀에 반환
                markerPool.releaseMarker(id)
                
                // 리스너 제거 (약한 참조 패턴 적용)
                markerListenerManager.removeListener(id, marker)
            }
            
            // 메모리 사용량 로깅
            Timber.d("마커 정리 후 상태: ${markers.size}개 마커 유지 중")
        } catch (e: Exception) {
            Timber.e(e, "마커 정리 중 오류 발생")
        }
    }
    
    /**
     * 진정한 차분 업데이트 구현 - 메모리와 성능 최적화
     * 기존 마커와 새 마커를 비교하여 변경된 부분만 업데이트
     * 깜박임 방지를 위해 기존 마커의 map 속성을 제거하지 않고 위치만 업데이트
     */
    fun updateMarkersWithDiff(
        newMarkers: List<DomainMarker>
    ) {
        if (!isInitialized || getNaverMap() == null) {
            Timber.d("NaverMap이 아직 초기화되지 않음 - 마커들을 대기 목록에 추가")
            pendingMarkers.clear()
            pendingMarkers.addAll(newMarkers)
            return
        }
        
        // 메모리 효율성을 위해 메인 스레드 외부에서 대부분의 작업 수행
        delegateScope.launch(Dispatchers.Default) {
            try {
                val startTime = System.currentTimeMillis()
                Timber.d("차분 업데이트 시작: ${newMarkers.size}개의 마커")
                
                // 1. 마커 ID 기반 맵 생성 (빠른 조회용) - 백그라운드 스레드에서 수행
                val newMarkerMap = newMarkers.associateBy { it.id }
                
                // 현재 마커 ID 세트 - UI 스레드 접근 필요
                val currentMarkerIds = withContext(Dispatchers.Main) {
                    markers.keys.toSet()
                }
                
                val newMarkerIds = newMarkerMap.keys
                
                // 2. 차분 계산 - 백그라운드 스레드에서 수행
                val markersToAdd = newMarkerIds - currentMarkerIds
                val markersToRemove = currentMarkerIds - newMarkerIds
                val markersToUpdate = currentMarkerIds.intersect(newMarkerIds)
                
                Timber.d("차분 계산 결과: 추가=${markersToAdd.size}, 제거=${markersToRemove.size}, 업데이트=${markersToUpdate.size}")
                
                // 모든 경우에 깜박임 방지 최적화 로직 사용
                withContext(Dispatchers.Main) {
                    // 1. 기존 마커 속성 업데이트 (map 속성 건드리지 않음으로 깜박임 방지)
                    markersToUpdate.forEach { id ->
                        val marker = markers[id] ?: return@forEach
                        val newData = newMarkerMap[id] ?: return@forEach
                        
                        // 위치가 실제로 변경된 경우에만 업데이트
                        val currentPos = marker.position
                        val newPos = LatLng(newData.position.latitude, newData.position.longitude)
                        
                        if (currentPos.latitude != newPos.latitude || currentPos.longitude != newPos.longitude) {
                            marker.position = newPos
                            Timber.v("마커 위치 업데이트: $id")
                        }
                        
                        // 데이터 맵 업데이트
                        markerDataMap[id] = newData
                        marker.tag = newData
                    }
                    
                    // 2. 새 마커 추가 (먼저 준비 후 일괄 표시)
                    val preparedNewMarkers = mutableListOf<Marker>()
                    markersToAdd.forEach { id ->
                        val newData = newMarkerMap[id] ?: return@forEach
                        val marker = markerPool.getMarker(id)
                        
                        // 기본 속성 설정
                        marker.position = LatLng(newData.position.latitude, newData.position.longitude)
                        marker.width = MARKER_WIDTH
                        marker.height = MARKER_HEIGHT
                        marker.alpha = MARKER_ALPHA_DEFAULT
                        marker.tag = newData
                        
                        // 먼저 데이터 맵 업데이트
                        markerDataMap[id] = newData
                        
                        // 내부 데이터 구조에 추가
                        markers[id] = marker
                        
                        // 클릭 리스너 설정
                        if (!hasClickListener(id)) {
                            refreshMarkerClickListener(id, marker, newData)
                        }
                        
                        // 준비된 마커 목록에 추가 (아직 맵에 표시 안 함)
                        preparedNewMarkers.add(marker)
                    }
                    
                    // 3. 새 마커를 맵에 일괄 표시 (제거 작업 전에 수행하여 깜박임 최소화)
                    preparedNewMarkers.forEach { marker ->
                        marker.map = getNaverMap()
                        marker.isVisible = true
                    }
                    
                    // 4. 제거할 마커를 마지막에 제거 (새 마커 표시 후 수행)
                    markersToRemove.forEach { id ->
                        removeMarker(id)
                    }
                    
                    Timber.d("차분 업데이트 완료: 업데이트=${markersToUpdate.size}, 추가=${preparedNewMarkers.size}, 제거=${markersToRemove.size}")
                }
                
                val duration = System.currentTimeMillis() - startTime
                Timber.d("차분 업데이트 완료: 소요시간 ${duration}ms, 현재 마커 ${markers.size}개")
                
            } catch (e: Exception) {
                Timber.e(e, "차분 업데이트 중 오류 발생: ${e.message}")
            }
        }
    }
    
    /**
     * 새 마커를 준비하지만 아직 맵에 표시하지 않음
     * 배치 처리를 위한 보조 메서드
     */
    private fun prepareNewMarker(id: String, domainMarker: DomainMarker): Marker? {
        try {
            // 마커 풀에서 마커 가져오기
            val marker = markerPool.getMarker(id)
            
            // 마커 위치 및 속성 설정
            marker.position = LatLng(domainMarker.position.latitude, domainMarker.position.longitude)
            marker.width = MARKER_WIDTH
            marker.height = MARKER_HEIGHT
            marker.alpha = MARKER_ALPHA_DEFAULT
            // 아직 맵에 표시하지 않음 (map은 null로 유지)
            
            // 중요: 먼저 도메인 마커 데이터 저장 (다른 메서드에서 참조할 수 있도록)
            markerDataMap[id] = domainMarker
            
            // 클릭 리스너 설정
            if (onMarkerClickListener != null) {
                refreshMarkerClickListener(id, marker, domainMarker)
            }
            
            // 내부 마커 맵에 추가
            markers[id] = marker
            
            return marker
        } catch (e: Exception) {
            Timber.e(e, "마커 준비 중 오류 발생: $id")
            return null
        }
    }
    
    /**
     * 마커 속성 업데이트
     */
    private fun updateMarkerProperties(id: String, marker: Marker, newData: DomainMarker) {
        try {
            // 먼저 마커 데이터 맵 업데이트 (클릭 리스너가 참조하는 데이터)
            markerDataMap[id] = newData
            
            // 위치 업데이트
            marker.position = LatLng(newData.position.latitude, newData.position.longitude)
            
            // 태그 업데이트 (메타데이터)
            marker.tag = newData
            
            // 기타 속성 업데이트 (필요한 경우)
            // ...
        } catch (e: Exception) {
            Timber.e(e, "마커 속성 업데이트 중 오류: $id")
        }
    }

    /**
     * 현재 위치 기반으로 마커 가시성 업데이트
     * 깜박임 방지를 위해 최적화된 구현
     */
    private fun updateVisibilityForCurrentLocation() {
        try {
            // 맵이 없으면 종료
            if (getNaverMap() == null) return
            
            // 맵이 null인 마커만 표시 (map != null인 마커는 그대로 유지)
            markers.forEach { (_, marker) ->
                if (marker.map == null) {
                    marker.map = getNaverMap()
                }
                // 이미 표시된 마커는 건드리지 않음 (map = null 설정 제거)
            }
            
            Timber.d("마커 가시성 업데이트 완료: ${markers.size}개 마커")
        } catch (e: Exception) {
            Timber.e(e, "마커 가시성 업데이트 중 오류: ${e.message}")
        }
    }

    /**
     * NaverMap과 Fragment가 파괴된 후 재생성될 때 마커 상태를 복원합니다.
     * 포그라운드로 돌아올 때 호출됩니다.
     * 깜박임 방지를 위해 마커의 위치나 가시성은 건드리지 않고 클릭 리스너만 확인합니다.
     */
    fun handleForegroundTransition() {
        try {
            Timber.d("포그라운드 전환 처리 시작")
            
            // NaverMap 참조 확인
            if (getNaverMap() == null) {
                Timber.w("NaverMap 참조가 없음, 전환 처리 불가")
                return
            }
            
            // 클릭 리스너 설정만 확인 (가시성 복원은 생략하여 깜박임 방지)
            ensureAllMarkersHaveClickListeners()
            
            Timber.d("포그라운드 전환 처리 완료")
        } catch (e: Exception) {
            Timber.e(e, "포그라운드 전환 처리 중 오류 발생")
        }
    }
    
    /**
     * 마커 UI 상태를 명시적으로 새로고침합니다.
     * 특히 백그라운드에서 포그라운드로 돌아올 때 마커가 올바르게 표시되지 않는 문제를 해결합니다.
     * 깜박임 방지를 위해 마커의 map 속성은 건드리지 않고 클릭 리스너만 확인합니다.
     */
    fun refreshMarkersUI() {
        try {
            Timber.d("마커 UI 상태 새로고침 시작")
            
            // NaverMap 참조 확인
            val map = getNaverMap()
            if (map == null) {
                Timber.w("NaverMap 참조가 없음, 새로고침 불가")
                return
            }
            
            // 클릭 리스너 설정만 확인 (마커 위치나 가시성은 건드리지 않음)
            ensureAllMarkersHaveClickListeners()
            
            // 마커 상태 통계만 로깅
            var visibleCount = 0
            var invisibleCount = 0
            
            markers.forEach { (_, marker) ->
                if (marker.map != null) {
                    visibleCount++
                } else {
                    invisibleCount++
                }
            }
            
            Timber.d("마커 UI 새로고침 완료: 총 ${markers.size}개 중 보이는 마커 ${visibleCount}개, 보이지 않는 마커 ${invisibleCount}개")
        } catch (e: Exception) {
            Timber.e(e, "마커 UI 새로고침 중 오류 발생")
        }
    }

    /**
     * 안전하게 작업을 수행합니다.
     * 활성화 상태일 때만 작업을 수행합니다.
     */
    private fun safeExecuteIfActive(action: () -> Unit) {
        if (isActive) {
            try {
                action()
            } catch (e: Exception) {
                Timber.e(e, "마커 UI 작업 중 오류 발생")
            }
        } else {
            Timber.d("MarkerUIDelegate가 비활성화되어 있어 작업을 수행하지 않습니다.")
        }
    }

    /**
     * 모든 마커를 맵에서 제거하고 참조를 해제합니다.
     * 메모리 누수 방지를 위해 사용됩니다.
     */
    fun clearAllMarkers() {
        try {
            Timber.d("모든 마커 참조 해제 시작")
            
            // 모든 마커를 맵에서 제거
            markers.forEach { (_, marker) ->
                marker.map = null
                marker.onClickListener = null
                marker.tag = null
            }
            
            // 모든 마커 컬렉션 비우기
            markers.clear()
            markerDataMap.clear()
            
            // 마커 풀 초기화
            markerPool.cleanup()
            
            Timber.d("모든 마커 참조 해제 완료")
        } catch (e: Exception) {
            Timber.e(e, "모든 마커 참조 해제 중 오류 발생")
        }
    }
} 