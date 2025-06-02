package com.parker.hotkey.presentation.map.event

import com.naver.maps.geometry.LatLng
import com.parker.hotkey.domain.manager.EditModeManager
import com.parker.hotkey.domain.manager.MarkerManager
import com.parker.hotkey.domain.manager.MemoManager
import com.parker.hotkey.domain.manager.TemporaryMarkerManager
import com.parker.hotkey.domain.repository.AuthRepository
import timber.log.Timber
import javax.inject.Inject
import dagger.Lazy
import javax.inject.Singleton
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

/**
 * MapEventHandler 인터페이스의 구현 클래스
 * 실제 이벤트 처리 로직을 구현합니다.
 */
@Singleton
class MapEventHandlerImpl @Inject constructor(
    private val markerManager: Lazy<MarkerManager>,
    private val memoManager: Lazy<MemoManager>,
    private val editModeManager: Lazy<EditModeManager>,
    private val scope: CoroutineScope,
    private val temporaryMarkerManager: Lazy<TemporaryMarkerManager>,
    private val authRepository: Lazy<AuthRepository>
) : MapEventHandler {

    private var lastClickedMarkerId: String? = null
    private var lastClickTime: Long = 0

    /**
     * 마커 클릭 이벤트 처리
     * @param markerId 클릭된 마커 ID
     * @return 이벤트 처리 여부
     */
    override fun handleMarkerClick(markerId: String): Boolean {
        try {
            Timber.d("마커 클릭 이벤트 처리 시작: 마커 ID=$markerId")
            
            // 마지막 클릭과 동일한 마커인지 확인하고 시간 차이 확인 (디바운싱)
            val currentTime = System.currentTimeMillis()
            if (markerId == lastClickedMarkerId && (currentTime - lastClickTime < MARKER_CLICK_DEBOUNCE_TIME)) {
                Timber.d("마커 클릭 무시: 짧은 시간 내 동일 마커 클릭 (${currentTime - lastClickTime}ms)")
                return true
            }
            
            // 클릭 상태 업데이트
            lastClickedMarkerId = markerId
            lastClickTime = currentTime
            
            // 마커 선택 처리
            markerManager.get().selectMarker(markerId)
            
            // 메모 다이얼로그가 이미 표시 중인지 확인
            val isMemoDialogVisible = memoManager.get().shouldShowMemoDialog.value
            val currentMarkerId = memoManager.get().selectedMarkerId.value
            
            if (isMemoDialogVisible && currentMarkerId == markerId) {
                Timber.d("동일한 마커의 메모 다이얼로그가 이미 표시 중: $markerId")
                return true
            }
            
            Timber.d("메모 다이얼로그 표시 요청 - 지연 적용")
            
            // 마커 클릭 후 약간의 지연 시간을 두고 메모장 다이얼로그 표시
            // (네이버 지도 애니메이션과 동시 실행 시 UI 버벅임 방지)
            scope.launch {
                delay(150)
                memoManager.get().showMemoDialog(markerId)
                Timber.d("마커 클릭 처리 완료: 메모 다이얼로그 표시 요청")
            }
            
            return true
        } catch (e: Exception) {
            Timber.e(e, "마커 클릭 이벤트 처리 중 오류 발생: $markerId")
            return false
        }
    }
    
    override fun handleMapClick(latLng: LatLng) {
        Timber.d("지도 클릭 이벤트: ${latLng.latitude}, ${latLng.longitude}")
        
        try {
            // 편집 모드인 경우 마커 생성, 아닌 경우 선택 해제
            if (editModeManager.get().getCurrentMode()) {
                scope.launch {
                    try {
                        val userId = authRepository.get().getUserId()
                        markerManager.get().createMarker(userId, latLng)
                    } catch (e: Exception) {
                        Timber.e(e, "마커 생성 중 오류 발생")
                    }
                }
            } else {
                markerManager.get().clearSelectedMarker()
                memoManager.get().clearMemos()
            }
        } catch (e: Exception) {
            Timber.e(e, "지도 클릭 이벤트 처리 중 오류 발생")
        }
    }
    
    override fun handleMemoDialogOpen(markerId: String) {
        Timber.d("메모 다이얼로그 열기 요청: $markerId")
        
        try {
            // MemoManager로 메모 로드
            memoManager.get().loadMemosByMarkerId(markerId)
            Timber.d("메모 다이얼로그 열기 처리 성공")
        } catch (e: Exception) {
            Timber.e(e, "메모 다이얼로그 열기 중 오류 발생")
        }
    }
    
    override fun handleMemoDialogShown() {
        Timber.d("메모 다이얼로그 표시 완료 처리")
        
        try {
            // 현재 선택된 마커가 임시 마커인 경우 영구 마커로 변환
            val markerId = markerManager.get().selectedMarkerId.value
            if (markerId != null && temporaryMarkerManager.get().isTemporaryMarker(markerId)) {
                temporaryMarkerManager.get().makeMarkerPermanent(markerId)
                Timber.d("임시 마커를 영구 마커로 변환 성공: $markerId")
            }
        } catch (e: Exception) {
            Timber.e(e, "메모 다이얼로그 표시 완료 처리 중 오류 발생")
        }
    }
    
    override fun handleMemoDialogDismissed() {
        Timber.d("메모 다이얼로그 닫힘 처리")
        // 특별한 처리가 필요 없음 (현재는 로깅만)
    }
    
    override fun toggleEditMode() {
        Timber.d("MapEventHandler: 편집 모드 토글 이벤트 처리")
        try {
            val currentMode = editModeManager.get().getCurrentMode()
            Timber.d("현재 모드: ${if(currentMode) "쓰기모드" else "읽기모드"} -> ${if(!currentMode) "쓰기모드" else "읽기모드"}로 전환")
            editModeManager.get().toggleEditMode()
        } catch (e: Exception) {
            Timber.e(e, "편집 모드 토글 중 오류 발생")
        }
    }
    
    /**
     * 메모 생성 이벤트 처리
     * @param markerId 메모를 생성할 마커 ID
     * @param content 메모 내용
     */
    override fun handleCreateMemo(markerId: String, content: String) {
        Timber.d("메모 생성 요청: 마커 ID=$markerId, 내용=$content")
        
        scope.launch {
            try {
                val userId = authRepository.get().getUserId()
                Timber.d("메모 생성 요청: 사용자 ID=$userId, 마커 ID=$markerId")
                // MemoManager를 통해 메모 생성
                memoManager.get().createMemo(userId, markerId, content)
                Timber.d("메모 생성 요청 완료")
            } catch (e: Exception) {
                Timber.e(e, "메모 생성 중 오류 발생")
            }
        }
    }
    
    override fun handleDeleteMemo(memoId: String) {
        Timber.d("메모 삭제 요청: $memoId")
        
        scope.launch {
            try {
                // MemoManager를 통해 메모 삭제
                memoManager.get().deleteMemo(memoId)
            } catch (e: Exception) {
                Timber.e(e, "메모 삭제 중 오류 발생")
            }
        }
    }
    
    override fun handleDeleteMarker(markerId: String) {
        Timber.d("마커 삭제 요청: $markerId")
        
        scope.launch {
            try {
                // 삭제 진행 중 오류 방지를 위한 3단계 접근
                try {
                    // 1. 마커 강제 제거 (UI 즉시 업데이트)
                    Timber.d("마커 UI에서 즉시 제거: $markerId")
                    markerManager.get().forceRemoveMarkerFromList(markerId)
                    
                    // 2. 실제 DB 삭제 시도
                    Timber.d("마커 DB 삭제 요청: $markerId")
                    val result = markerManager.get().deleteMarker(markerId)
                    
                    result.onSuccess {
                        Timber.d("마커 삭제 성공: $markerId")
                        
                        // 3. 성공 후에도 UI 상태 최종 확인
                        if (markerManager.get().markers.value.any { it.id == markerId }) {
                            Timber.w("마커 DB 삭제 성공 후에도 UI에 남아있음 - 강제 제거 실행: $markerId")
                            markerManager.get().forceRemoveMarkerFromList(markerId)
                        }
                        
                        // 4. 선택된 마커였으면 선택 취소
                        if (markerManager.get().selectedMarkerId.value == markerId) {
                            Timber.d("삭제된 마커가 선택 상태 - 선택 초기화: $markerId")
                            markerManager.get().clearSelectedMarker()
                        }
                        
                        // 5. 삭제 후 추가 처리
                        scope.launch {
                            kotlinx.coroutines.delay(100)
                            Timber.d("마커 삭제 성공 후 삭제 이벤트 재발행: $markerId")
                            markerManager.get().forceRemoveMarkerFromList(markerId)
                        }
                    }
                    
                    result.onFailure { error ->
                        Timber.e(error, "마커 삭제 실패: $markerId")
                        
                        // 실패해도 UI는 강제 업데이트
                        scope.launch {
                            try {
                                Timber.d("마커 삭제 실패해도 UI 업데이트 시도: $markerId")
                                markerManager.get().forceRemoveMarkerFromList(markerId)
                            } catch (e: Exception) {
                                Timber.e(e, "마커 삭제 실패 후 UI 강제 업데이트 중 오류: $markerId")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "마커 삭제 처리 중 심각한 오류 발생: $markerId")
                    
                    // 복구 시도
                    try {
                        Timber.d("심각한 오류 후에도 UI 상태 강제 업데이트 시도: $markerId")
                        markerManager.get().forceRemoveMarkerFromList(markerId)
                    } catch (innerEx: Exception) {
                        Timber.e(innerEx, "마커 삭제 중 복구 시도 실패: $markerId")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "마커 삭제 중 최상위 오류 발생: $markerId")
            }
        }
    }
    
    /**
     * 메모 로드 이벤트 처리
     * @param markerId 메모를 로드할 마커 ID
     */
    override fun handleLoadMemos(markerId: String) {
        Timber.d("메모 로드 요청: $markerId")
        
        try {
            // MemoManager로 메모 로드
            memoManager.get().loadMemosByMarkerId(markerId)
            Timber.d("메모 로드 요청 완료")
        } catch (e: Exception) {
            Timber.e(e, "메모 로드 중 오류 발생: $markerId")
        }
    }

    private companion object {
        const val MARKER_CLICK_DEBOUNCE_TIME = 800L // 800ms (0.8초) 디바운스 시간
    }
} 