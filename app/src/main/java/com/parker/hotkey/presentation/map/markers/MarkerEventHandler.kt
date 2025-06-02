package com.parker.hotkey.presentation.map.markers

import com.parker.hotkey.domain.manager.MarkerEvent
import com.parker.hotkey.domain.manager.MarkerManager
import com.parker.hotkey.domain.manager.MemoManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import timber.log.Timber
import javax.inject.Inject
import dagger.Lazy

/**
 * 마커 이벤트 핸들러 인터페이스
 */
interface MarkerEventHandler {
    /**
     * 마커 이벤트 처리
     * @param event 처리할 마커 이벤트
     */
    suspend fun handleMarkerEvent(event: MarkerEvent)
    
    /**
     * 마커 클릭 이벤트 처리
     * @param markerId 클릭된 마커 ID
     * @return 이벤트 처리 여부
     */
    fun handleMarkerClick(markerId: String): Boolean
    
    /**
     * 마커 삭제 이벤트 처리
     * @param markerId 삭제할 마커 ID
     */
    suspend fun handleDeleteMarker(markerId: String)
}

/**
 * 마커 이벤트 처리 구현체
 */
class MarkerEventHandlerImpl @Inject constructor(
    private val markerManager: Lazy<MarkerManager>,
    private val memoManager: Lazy<MemoManager>
) : MarkerEventHandler {
    
    /**
     * 내부 이벤트 발행을 위한 SharedFlow
     */
    private val _internalEvents = MutableSharedFlow<InternalMarkerEvent>(extraBufferCapacity = 10)
    val internalEvents: SharedFlow<InternalMarkerEvent> = _internalEvents
    
    /**
     * 마커 이벤트 처리
     */
    override suspend fun handleMarkerEvent(event: MarkerEvent) {
        try {
            Timber.d("마커 이벤트 수신: $event")
            when (event) {
                is MarkerEvent.MarkerCreationSuccess -> {
                    handleMarkerCreationSuccess(event)
                }
                is MarkerEvent.MarkerDeleted -> {
                    handleMarkerDeleted(event)
                }
                is MarkerEvent.MarkerSelected -> {
                    handleMarkerSelected(event)
                }
                is MarkerEvent.MarkerSelectionCleared -> {
                    handleMarkerSelectionCleared()
                }
                is MarkerEvent.MarkerCreated -> {
                    Timber.d("마커 생성 이벤트 수신: ${event.marker.id}")
                    _internalEvents.emit(InternalMarkerEvent.MarkerCreated(event.marker.id))
                }
                is MarkerEvent.Error -> {
                    Timber.e(event.throwable, "마커 오류 이벤트 수신: ${event.message}")
                    _internalEvents.emit(InternalMarkerEvent.Error(
                        event.throwable ?: Exception(event.message),
                        event.message
                    ))
                }
                is MarkerEvent.RefreshMarkersUI -> {
                    Timber.d("마커 UI 새로고침 이벤트 수신")
                    _internalEvents.emit(InternalMarkerEvent.RefreshMarkersUI)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "마커 이벤트 처리 중 오류 발생")
            _internalEvents.emit(InternalMarkerEvent.Error(e, "마커 이벤트 처리 중 오류가 발생했습니다."))
        }
    }
    
    /**
     * 마커 생성 성공 이벤트 처리
     */
    private suspend fun handleMarkerCreationSuccess(event: MarkerEvent.MarkerCreationSuccess) {
        try {
            val markerId = event.marker.id
            Timber.d("마커 생성 이벤트: $markerId")
            
            // 마커를 선택 상태로 설정
            markerManager.get()?.selectMarker(markerId)
            
            // 마커 생성 성공 시 즉시 메모장 다이얼로그 표시
            memoManager.get()?.showMemoDialog(markerId)
            
            // 메모 로드 요청 - UI 표시 전에 메모 데이터를 미리 준비
            memoManager.get()?.loadMemosByMarkerId(markerId)
            
            _internalEvents.emit(InternalMarkerEvent.MarkerCreationSuccess(markerId))
        } catch (e: Exception) {
            Timber.e(e, "마커 생성 이벤트 처리 중 오류 발생")
            _internalEvents.emit(InternalMarkerEvent.Error(e, "마커 생성 이벤트 처리 중 오류가 발생했습니다."))
        }
    }
    
    /**
     * 마커 삭제 이벤트 처리
     */
    private suspend fun handleMarkerDeleted(event: MarkerEvent.MarkerDeleted) {
        val markerId = event.markerId
        Timber.d("마커 삭제 이벤트 수신: $markerId - 처리 시작")
        
        try {
            // 1. 마커 상태 직접 확인
            val markerExists = markerManager.get()?.markers?.value?.any { it.id == markerId } ?: false
            Timber.d("마커 상태 확인: id=$markerId, 존재함=$markerExists")
            
            if (markerExists) {
                // 2. 마커가 아직 목록에 있으면 강제로 제거
                Timber.d("마커가 아직 목록에 있음 - 강제 제거 실행: $markerId")
                markerManager.get()?.forceRemoveMarkerFromList(markerId)
            } else {
                Timber.d("마커가 이미 목록에서 제거됨: $markerId")
            }
            
            // 3. 선택 상태 확인/초기화
            if (markerManager.get()?.selectedMarkerId?.value == markerId) {
                Timber.d("삭제된 마커가 선택 상태임 - 선택 초기화: $markerId")
                markerManager.get()?.clearSelectedMarker()
            }
            
            // 4. 직접 마커 목록 강제 업데이트 (확실한 UI 갱신)
            val currentMarkers = markerManager.get()?.markers?.value ?: emptyList()
            if (currentMarkers.any { it.id == markerId }) {
                Timber.w("여전히 마커가 목록에 남아있음 - 최종 강제 제거: $markerId")
                val filteredMarkers = currentMarkers.filterNot { it.id == markerId }
                markerManager.get()?.updateMarkers(filteredMarkers)
            }
            
            Timber.d("마커 삭제 이벤트 처리 완료: $markerId")
            _internalEvents.emit(InternalMarkerEvent.MarkerDeleted(markerId))
        } catch (e: Exception) {
            Timber.e(e, "마커 삭제 이벤트 처리 중 예외 발생: $markerId")
            _internalEvents.emit(InternalMarkerEvent.Error(e, "마커 삭제 이벤트 처리 중 오류가 발생했습니다."))
        }
    }
    
    /**
     * 마커 선택 이벤트 처리
     */
    private suspend fun handleMarkerSelected(event: MarkerEvent.MarkerSelected) {
        Timber.d("마커 선택 이벤트 수신: ${event.markerId}")
        
        try {
            // 메모 로드 - 필요 시 수행
            memoManager.get()?.loadMemosByMarkerId(event.markerId)
            
            _internalEvents.emit(InternalMarkerEvent.MarkerSelected(event.markerId))
        } catch (e: Exception) {
            Timber.e(e, "마커 선택 이벤트 처리 중 오류 발생: ${event.markerId}")
            _internalEvents.emit(InternalMarkerEvent.Error(e, "마커 선택 이벤트 처리 중 오류가 발생했습니다."))
        }
    }
    
    /**
     * 마커 선택 해제 이벤트 처리
     */
    private suspend fun handleMarkerSelectionCleared() {
        Timber.d("마커 선택 해제 이벤트 수신")
        
        try {
            // 메모장 상태 초기화 - 필요 시 수행
            memoManager.get()?.clearMemos()
            
            _internalEvents.emit(InternalMarkerEvent.MarkerSelectionCleared)
        } catch (e: Exception) {
            Timber.e(e, "마커 선택 해제 이벤트 처리 중 오류 발생")
            _internalEvents.emit(InternalMarkerEvent.Error(e, "마커 선택 해제 이벤트 처리 중 오류가 발생했습니다."))
        }
    }
    
    /**
     * 마커 클릭 이벤트 처리
     */
    override fun handleMarkerClick(markerId: String): Boolean {
        Timber.d("마커 클릭 이벤트: $markerId")
        
        try {
            // 마커 선택 처리
            markerManager.get()?.selectMarker(markerId)
            
            // 메모 로드
            memoManager.get()?.loadMemosByMarkerId(markerId)
            
            return true
        } catch (e: Exception) {
            Timber.e(e, "마커 클릭 이벤트 처리 중 오류 발생: $markerId")
            return false
        }
    }
    
    /**
     * 마커 삭제 이벤트 처리
     */
    override suspend fun handleDeleteMarker(markerId: String) {
        Timber.d("마커 삭제 요청: $markerId")
        
        try {
            // UI 최적화를 위한 낙관적 업데이트
            markerManager.get()?.forceRemoveMarkerFromList(markerId)
            
            // 실제 삭제 수행
            val result = markerManager.get()?.deleteMarker(markerId) ?: Result.failure(
                IllegalStateException("마커 매니저가 초기화되지 않았습니다.")
            )
            
            result.onFailure { error ->
                Timber.e(error, "마커 삭제 실패: $markerId")
                _internalEvents.emit(InternalMarkerEvent.Error(error, "마커 삭제에 실패했습니다."))
            }
        } catch (e: Exception) {
            Timber.e(e, "마커 삭제 처리 중 예외 발생: $markerId")
            _internalEvents.emit(InternalMarkerEvent.Error(e, "마커 삭제 처리 중 오류가 발생했습니다."))
        }
    }
    
    /**
     * 내부 이벤트 모델
     */
    sealed class InternalMarkerEvent {
        data class MarkerCreated(val markerId: String) : InternalMarkerEvent()
        data class MarkerCreationSuccess(val markerId: String) : InternalMarkerEvent()
        data class MarkerDeleted(val markerId: String) : InternalMarkerEvent()
        data class MarkerSelected(val markerId: String) : InternalMarkerEvent()
        object MarkerSelectionCleared : InternalMarkerEvent()
        data class Error(val error: Throwable, val message: String) : InternalMarkerEvent()
        object RefreshMarkersUI : InternalMarkerEvent()
    }
} 