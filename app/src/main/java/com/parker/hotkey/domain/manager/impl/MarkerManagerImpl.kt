package com.parker.hotkey.domain.manager.impl

import com.naver.maps.geometry.LatLng
import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.repository.MarkerRepository
import com.parker.hotkey.domain.usecase.marker.CreateMarkerUseCase
import com.parker.hotkey.domain.usecase.marker.DeleteMarkerWithValidationUseCase
import com.parker.hotkey.domain.manager.MarkerManager
import com.parker.hotkey.domain.manager.MarkerEvent
import com.parker.hotkey.domain.manager.BaseManager
import com.parker.hotkey.domain.model.state.MarkerState
import com.parker.hotkey.domain.util.StateLogger
import com.parker.hotkey.domain.util.StateUpdateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import com.parker.hotkey.domain.repository.MarkerQueryOptions
import com.parker.hotkey.domain.constants.GeohashConstants
import com.parker.hotkey.domain.manager.MemoManager
import com.parker.hotkey.domain.manager.MarkerStateAdapter
import com.parker.hotkey.domain.manager.TemporaryMarkerManager
import dagger.Lazy

@Singleton
class MarkerManagerImpl @Inject constructor(
    private val markerRepository: MarkerRepository,
    private val createMarkerUseCase: CreateMarkerUseCase,
    private val deleteMarkerWithValidationUseCase: DeleteMarkerWithValidationUseCase,
    private val memoManager: MemoManager,
    private val markerStateAdapter: MarkerStateAdapter,
    private val temporaryMarkerManager: Lazy<TemporaryMarkerManager>,
    coroutineScope: CoroutineScope
) : BaseManager<MarkerEvent>(coroutineScope), MarkerManager {

    private val TAG = "MarkerManagerImpl"
    
    // 통합된 상태 관리 - MarkerState를 단일 소스로 사용
    private val _state = MutableStateFlow(MarkerState())
    
    // StateUpdateHelper 초기화
    private val stateUpdateHelper = StateUpdateHelper(
        stateFlow = _state,
        errorHandler = { state, error, isLoading ->
            state.copy(error = error, isLoading = isLoading)
        },
        coroutineScope = coroutineScope
    )
    
    // StateLogger 초기화
    private val stateLogger = StateLogger(TAG)
    
    // 기존 인터페이스 호환성을 위한 파생 StateFlow
    override val markers: StateFlow<List<Marker>> = _state.map { it.markers }.stateIn(
        coroutineScope,
        SharingStarted.Eagerly,
        emptyList()
    )
    
    override val selectedMarkerId: StateFlow<String?> = _state.map { it.selectedId }.stateIn(
        coroutineScope,
        SharingStarted.Eagerly,
        null
    )
    
    // 마커 관련 이벤트를 발생시키기 위한 SharedFlow
    private val _markerEvents = MutableSharedFlow<MarkerEvent>(
        replay = 10,
        extraBufferCapacity = 50,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val markerEvents: SharedFlow<MarkerEvent> = _markerEvents.asSharedFlow()
    
    // 삭제 이벤트 큐 추가 - 앱 초기화 과정에서 이벤트가 손실되는 것을 방지
    private val pendingDeleteEvents = mutableSetOf<String>()
    
    init {
        // events를 markerEvents로 전달하여 호환성 유지
        coroutineScope.launch {
            events.collect { event ->
                try {
                    _markerEvents.emit(event)
                    stateLogger.logDebug("BaseManager events를 markerEvents로 전달: $event")
                } catch (e: Exception) {
                    stateLogger.logError(e, "이벤트 전달 중 오류 발생: $event")
                }
            }
        }
        
        // 명시적 initialize 호출로 이동
        stateLogger.logDebug("MarkerManagerImpl 생성됨")
    }
    
    /**
     * 매니저 초기화
     * 필요한 초기 설정을 수행합니다.
     */
    override fun initialize() {
        // 공통 초기화 로직 사용
        initializeCommon("MarkerManager") {
            // 구독자 상태 확인 로그 추가
            for (i in 1..5) {
                // 5초간 1초마다 구독자 상태를 로그로 출력
                stateLogger.logDebug("MarkerManager 구독자 상태 확인 (#$i): ${hasSubscribers.value}")
                if (hasSubscribers.value) {
                    stateLogger.logDebug("MarkerManager 구독자 확인됨 (#$i)")
                    break
                }
                kotlinx.coroutines.delay(1000)
            }
            
            // 초기화 완료 후 1초 간격으로 pendingDeleteEvents 재전송 (최대 10초간)
            if (pendingDeleteEvents.isNotEmpty()) {
                stateLogger.logDebug("초기화 후 보류 중인 삭제 이벤트 재전송 시작: ${pendingDeleteEvents.size}개")
                for (i in 1..10) {
                    delay(1000)
                    if (pendingDeleteEvents.isEmpty()) break
                    
                    val eventsToRetry = pendingDeleteEvents.toSet()
                    for (markerId in eventsToRetry) {
                        stateLogger.logDebug("보류 중인 마커 삭제 이벤트 재전송 (#$i): $markerId")
                        bufferOrEmitEvent(MarkerEvent.MarkerDeleted(markerId))
                    }
                }
            }
        }
    }
    
    override fun selectMarker(markerId: String) {
        stateUpdateHelper.updateState(TAG) { currentState ->
            currentState.copy(selectedId = markerId)
        }
        stateLogger.logDebug("마커 선택: $markerId")
        
        // 마커 선택 이벤트 발생
        coroutineScope.launch {
            bufferOrEmitEvent(MarkerEvent.MarkerSelected(markerId))
        }
    }
    
    override fun clearSelectedMarker() {
        stateUpdateHelper.updateState(TAG) { currentState ->
            currentState.copy(selectedId = null)
        }
        stateLogger.logDebug("선택된 마커 초기화")
        
        // 마커 선택 해제 이벤트 발생
        coroutineScope.launch {
            bufferOrEmitEvent(MarkerEvent.MarkerSelectionCleared)
        }
    }
    
    override suspend fun createMarker(userId: String, latLng: LatLng) = 
        createMarkerUseCase(userId, latLng.latitude, latLng.longitude)
            .also { result ->
                result.onSuccess { marker ->
                    stateUpdateHelper.updateState(TAG) { currentState ->
                        currentState.copy(markers = currentState.markers + marker)
                    }
                    stateLogger.logDebug("마커 생성 성공: $marker")
                    
                    // 마커 생성 이벤트 발행 - 일관된 순서로 이벤트 발행
                    coroutineScope.launch {
                        // 마커 생성 이벤트
                        bufferOrEmitEvent(MarkerEvent.MarkerCreated(marker))
                        
                        // 50ms 지연 후 성공 이벤트 발행 (UI 업데이트를 위한 지연)
                        delay(100) // 좀 더 긴 지연으로 변경하여 안정성 강화
                        bufferOrEmitEvent(MarkerEvent.MarkerCreationSuccess(marker))
                        stateLogger.logDebug("마커 생성 성공 이벤트 발행: ${marker.id}")
                        
                        // 추가 이벤트 처리가 필요한 경우 더 지연 후 발행
                        delay(50)
                        // UI 새로고침 이벤트 발행 (마커의 일관성을 위해)
                        bufferOrEmitEvent(MarkerEvent.RefreshMarkersUI)
                    }
                }.onFailure { error ->
                    stateLogger.logError(error, "마커 생성 실패: ${error.message}")
                }
            }
    
    override suspend fun createMarkerAndSelect(userId: String, latLng: LatLng): Result<Marker> {
        return createMarker(userId, latLng).also { result ->
            result.onSuccess { marker ->
                selectMarker(marker.id)
                stateLogger.logDebug("마커 생성 및 선택 완료: ${marker.id}")
            }
        }
    }
    
    override suspend fun createMarkerAndOpenMemo(userId: String, latLng: LatLng): Result<Marker> {
        return createMarkerAndSelect(userId, latLng).also { result ->
            result.onSuccess { marker ->
                // MemoManager를 통해 새 마커의 메모장 열기 요청
                memoManager.showMemoDialog(marker.id)
                stateLogger.logDebug("마커 생성 및 메모장 열기 요청: ${marker.id}")
            }
        }
    }
    
    /**
     * 마커 상태에 따른 삭제 처리
     * 임시 마커는 API 호출 없이 로컬에서만 처리하고
     * 영구 마커는 서버와 동기화를 시도합니다.
     */
    override suspend fun deleteMarkerByState(markerId: String): Result<Unit> {
        stateLogger.logDebug("마커 삭제 시작 (상태 기반) - ID=$markerId")
        stateUpdateHelper.setLoading(TAG, true)
        
        try {
            // 마커 상태 확인
            val markerState = markerStateAdapter.getMarkerState(markerId)
            stateLogger.logDebug("마커 상태: $markerState - ID=$markerId")
            
            // 상태에 따른 분기 처리
            val result = if (markerState == com.parker.hotkey.domain.model.MarkerState.TEMPORARY) {
                // 임시 마커: 로컬에서만 삭제 (API 호출 없음)
                stateLogger.logDebug("임시 마커 삭제 - 로컬 처리만 수행")
                // 로컬 데이터 소스에서 삭제
                markerRepository.delete(markerId)
                // 임시 마커 목록에서 제거
                temporaryMarkerManager.get().removeTemporaryMarker(markerId)
                Result.success(Unit)
            } else {
                // 영구 마커: API 호출하여 서버와 동기화
                stateLogger.logDebug("영구 마커 삭제 - 서버 API 호출")
                deleteMarkerWithValidationUseCase(markerId)
            }
            
            // UI 상태 업데이트
            result.onSuccess {
                updateUIState(markerId)
                emitMarkerDeletedEvent(markerId)
            }
            
            result.onFailure { e ->
                stateLogger.logError(e, "마커 삭제 실패 - ID=$markerId")
                stateUpdateHelper.setError(TAG, e.message)
            }
            
            return result
        } finally {
            stateUpdateHelper.setLoading(TAG, false)
        }
    }
    
    /**
     * UI 상태 업데이트 로직 (재사용성을 위해 분리)
     */
    private fun updateUIState(markerId: String) {
        stateUpdateHelper.updateState(TAG) { currentState ->
            val updatedMarkers = currentState.markers.filterNot { it.id == markerId }
            val updatedSelectedId = if (currentState.selectedId == markerId) null 
                                else currentState.selectedId
            
            currentState.copy(
                markers = updatedMarkers,
                selectedId = updatedSelectedId
            )
        }
    }

    override suspend fun deleteMarker(markerId: String): Result<Unit> {
        // 새로운 상태 기반 메서드로 리디렉션
        return deleteMarkerByState(markerId)
    }
    
    /**
     * 마커 삭제 이벤트를 신뢰성 있게 발행
     */
    private fun emitMarkerDeletedEvent(markerId: String) {
        // 삭제 이벤트 큐에 추가
        pendingDeleteEvents.add(markerId)
        
        // 즉시 이벤트 발행 (고우선순위)
        coroutineScope.launch {
            bufferOrEmitEvent(MarkerEvent.MarkerDeleted(markerId), highPriority = true)
            stateLogger.logDebug("마커 삭제 이벤트 발행 - ID=$markerId")
            
            // 1초 후 큐에서 제거 (이벤트가 전달되었다고 가정)
            kotlinx.coroutines.delay(1000)
            pendingDeleteEvents.remove(markerId)
        }
    }
    
    override fun loadMarkersInArea(geohash: String, neighbors: List<String>) {
        // 로딩 상태 설정
        stateUpdateHelper.setLoading(TAG, true)
        
        coroutineScope.launch {
            try {
                stateLogger.logDebug("특정 영역의 마커 로딩 시작 - geohash: $geohash, neighbors: ${neighbors.size}개")
                stateLogger.logDebug("이웃 geohash 목록: ${neighbors.joinToString(", ")}")
                
                // 현재 카메라 줌 레벨을 가져올 수 없어 기본값 사용
                markerRepository.getMarkers(geohash, neighbors)
                    .catch { e ->
                        stateLogger.logError(e, "마커 데이터 로딩 에러")
                        stateUpdateHelper.setError(TAG, e.message)
                    }
                    .collect { loadedMarkers ->
                        stateLogger.logDebug("마커 ${loadedMarkers.size}개 로드됨")
                        
                        // 각 마커의 geohash와 위치 로깅
                        loadedMarkers.take(10).forEach { marker ->
                            stateLogger.logDebug("로드된 마커: id=${marker.id}, geohash=${marker.geohash}, lat=${marker.latitude}, lng=${marker.longitude}")
                        }
                        
                        // 특정 geohash에 해당하는 마커만 카운트
                        val markersInTargetGeohash = loadedMarkers.filter { it.geohash == geohash }
                        stateLogger.logDebug("요청한 geohash($geohash)에 있는 마커: ${markersInTargetGeohash.size}개")
                        
                        // 마커 ID들을 기록 (디버깅용)
                        val markerIds = loadedMarkers.map { it.id }
                        stateLogger.logDebug("로드된 마커 ID 목록: ${markerIds.take(5)}")
                        
                        // 상태 업데이트
                        stateUpdateHelper.updateState(TAG) { currentState ->
                            val updated = currentState.copy(markers = loadedMarkers)
                            stateLogger.logDebug("상태 업데이트: 마커 ${updated.markers.size}개")
                            updated
                        }
                        
                        // 현재 선택된 마커가 있는지 확인
                        val selectedId = _state.value.selectedId
                        if (selectedId != null) {
                            val selectedMarkerExists = loadedMarkers.any { it.id == selectedId }
                            stateLogger.logDebug("선택된 마커(${selectedId}) ${if (selectedMarkerExists) "존재함" else "존재하지 않음"}")
                        }
                    }
            } catch (e: Exception) {
                stateLogger.logError(e, "마커 로딩 중 오류 발생")
                stateUpdateHelper.setError(TAG, e.message)
            } finally {
                stateUpdateHelper.setLoading(TAG, false)
            }
        }
    }

    override fun loadMarkersInAreaOptimized(geohash: String, neighbors: List<String>, zoomLevel: Double) {
        // 로딩 상태 설정
        stateUpdateHelper.setLoading(TAG, true)
        
        coroutineScope.launch {
            try {
                // 줌 레벨에 따라 정밀도와 마커 제한 수 결정
                val precision = calculatePrecisionByZoomLevel(zoomLevel)
                
                // 실제 geohash 길이 확인하여 정밀도 조정
                val actualGeohashLength = geohash.length
                val adjustedPrecision = minOf(precision, actualGeohashLength)
                
                // 줌 레벨에 따른 마커 제한 계산
                val limit = calculateMarkerLimitByZoomLevel(zoomLevel)
                
                stateLogger.logDebug("최적화된 마커 로딩 시작 - 정밀도: $adjustedPrecision, 제한: $limit, 줌 레벨: $zoomLevel")
                
                val options = MarkerQueryOptions(
                    precision = adjustedPrecision,
                    limit = limit
                )
                
                markerRepository.getMarkers(geohash, neighbors, options)
                    .catch { e ->
                        stateLogger.logError(e, "마커 데이터 로딩 에러 (최적화)")
                        stateUpdateHelper.setError(TAG, e.message)
                    }
                    .collect { loadedMarkers ->
                        stateUpdateHelper.updateState(TAG) { currentState ->
                            currentState.copy(markers = loadedMarkers)
                        }
                        stateLogger.logDebug("마커 ${loadedMarkers.size}개 로드됨 (최적화)")
                    }
            } catch (e: Exception) {
                stateLogger.logError(e, "마커 로딩 중 오류 발생 (최적화)")
                stateUpdateHelper.setError(TAG, e.message)
            } finally {
                stateUpdateHelper.setLoading(TAG, false)
            }
        }
    }
    
    @Deprecated("loadMarkersInAreaOptimized로 통합됨", ReplaceWith("loadMarkersInAreaOptimized(geohash, neighbors, zoomLevel)"))
    override fun loadMarkersInAreaGeohash6Optimized(geohash: String, neighbors: List<String>, zoomLevel: Double) {
        // 기존 최적화 메서드를 정밀도 6 고정으로 호출
        loadMarkersInAreaOptimized(geohash, neighbors, zoomLevel)
    }
    
    /**
     * 줌 레벨에 따라 적절한 정밀도 계산
     * @param zoomLevel 현재 줌 레벨
     * @return geohash 정밀도 (5 또는 6)
     */
    private fun calculatePrecisionByZoomLevel(zoomLevel: Double): Int {
        return when {
            zoomLevel >= 14.0 -> GeohashConstants.GEOHASH_PRECISION // 가까운/중간 줌 - 높은 정밀도
            else -> 5 // 먼 줌 - 낮은 정밀도
        }
    }
    
    /**
     * 줌 레벨에 따라 마커 표시 제한 계산
     * @param zoomLevel 현재 줌 레벨
     * @return 마커 제한 수
     */
    private fun calculateMarkerLimitByZoomLevel(zoomLevel: Double): Int {
        return when {
            zoomLevel >= 16.0 -> 1000 // 가까운 줌 - 많은 마커
            zoomLevel >= 14.0 -> 500  // 중간 줌 - 중간 마커
            else -> 100               // 먼 줌 - 적은 마커
        }
    }
    
    override fun getMarkerById(markerId: String): Marker? {
        return _state.value.markers.find { it.id == markerId }
    }
    
    override fun updateMarkers(markers: List<Marker>) {
        stateUpdateHelper.updateState(TAG) { currentState ->
            currentState.copy(markers = markers)
        }
        stateLogger.logDebug("마커 목록 업데이트됨: ${markers.size}개")
    }
    
    override fun forceRemoveMarkerFromList(markerId: String) {
        stateUpdateHelper.updateState(TAG) { currentState ->
            val updatedMarkers = currentState.markers.filterNot { it.id == markerId }
            currentState.copy(markers = updatedMarkers)
        }
        stateLogger.logDebug("마커 강제 제거됨: $markerId")
    }

    /**
     * 오류 이벤트 변환 구현
     */
    override fun createErrorEvent(throwable: Throwable, message: String): MarkerEvent {
        return MarkerEvent.Error(message, throwable)
    }

    /**
     * 백그라운드에서 포그라운드로 전환 시 마커 UI를 새로고침합니다.
     * 마커 깜박임 방지를 위해 사용합니다.
     */
    override fun refreshMarkersUI() {
        // 이 메서드는 마커 뷰모델에서 호출되어 MarkerUIDelegate로 전달됩니다.
        // 이벤트 발행만 처리하면 됩니다.
        coroutineScope.launch {
            try {
                Timber.d("마커 UI 새로고침 이벤트 발행")
                _events.emit(MarkerEvent.RefreshMarkersUI)
            } catch (e: Exception) {
                Timber.e(e, "마커 UI 새로고침 이벤트 발행 중 오류 발생")
            }
        }
    }
} 