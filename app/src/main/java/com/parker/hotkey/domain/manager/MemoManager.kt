package com.parker.hotkey.domain.manager

import android.view.View
import com.google.android.material.snackbar.Snackbar
import com.parker.hotkey.domain.model.Memo
import com.parker.hotkey.domain.model.state.MemoState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 메모 상태를 정의하는 데이터 클래스 (기존 호환성을 위해 유지)
 */
data class MemoState(
    val memos: List<Memo> = emptyList(),
    val selectedMarkerId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * 메모 관련 이벤트를 정의하는 sealed 클래스
 */
sealed class MemoEvent {
    data class MemosLoaded(val markerId: String, val memos: List<Memo>) : MemoEvent()
    data class MemoCreated(val memo: Memo) : MemoEvent()
    data class MemoDeleted(val memoId: String) : MemoEvent()
    data class Error(val message: String) : MemoEvent()
    object ClearedSelection : MemoEvent()
}

/**
 * 메모 관리 기능을 제공하는 인터페이스
 */
interface MemoManager {
    /**
     * 현재 메모 상태 Flow
     */
    val state: StateFlow<com.parker.hotkey.domain.manager.MemoState>
    
    /**
     * 메모 목록 Flow
     */
    val memos: StateFlow<List<Memo>>
    
    /**
     * 메모 관련 이벤트 Flow
     */
    val events: SharedFlow<MemoEvent>
    
    /**
     * 초기화 완료 상태
     */
    val initialized: StateFlow<Boolean>
    
    /**
     * 편집 모드 상태 Flow
     */
    val editModeState: StateFlow<Boolean>
    
    /**
     * 메모 다이얼로그 표시 여부 상태 Flow
     */
    val shouldShowMemoDialog: StateFlow<Boolean>
    
    /**
     * 선택된 마커 ID 상태 Flow
     */
    val selectedMarkerId: StateFlow<String?>
    
    /**
     * 이벤트 구독 설정
     * 
     * @param scope 이벤트를 수집할 코루틴 스코프
     * @param handler 이벤트 처리 핸들러
     * @return 구독 작업 Job
     */
    fun subscribeToEvents(scope: CoroutineScope, handler: suspend (MemoEvent) -> Unit): Job
    
    /**
     * 편집 모드 이벤트 구독 설정
     * 
     * @param handler 이벤트 처리 핸들러
     * @return 구독 작업 Job
     */
    fun subscribeToEditModeEvents(handler: suspend (EditModeEvent) -> Unit): Job
    
    /**
     * 특정 마커에 대한 메모 개수를 반환
     *
     * @param markerId 마커 ID
     * @return 해당 마커의 메모 개수
     */
    suspend fun getMemoCountForMarker(markerId: String): Int
    
    /**
     * 특정 마커에 대한 메모를 로드
     *
     * @param markerId 마커 ID
     */
    fun loadMemosByMarkerId(markerId: String)
    
    /**
     * 메모 생성
     *
     * @param userId 사용자 ID
     * @param markerId 마커 ID
     * @param content 메모 내용
     */
    fun createMemo(userId: String, markerId: String, content: String)
    
    /**
     * 메모 삭제
     *
     * @param memoId 메모 ID
     */
    fun deleteMemo(memoId: String)
    
    /**
     * 선택된 마커와 관련된 상태 초기화
     */
    fun clearSelectedMarker()
    
    /**
     * 모든 메모 상태 초기화
     */
    fun clearMemos()
    
    /**
     * 편집 모드 검증
     *
     * @param view 현재 뷰
     * @param action 편집 모드 시 수행할 액션
     */
    fun validateEditMode(view: View, action: () -> Unit)
    
    /**
     * 편집 모드 타이머 재시작
     */
    fun restartEditModeTimer()
    
    /**
     * 현재 편집 모드 상태 반환
     *
     * @return 현재 편집 모드 상태 (true: 편집 모드, false: 읽기 모드)
     */
    fun getCurrentMode(): Boolean
    
    /**
     * 현재 편집 모드 타이머 남은 시간 반환 (ms)
     *
     * @return 남은 시간 (밀리초)
     */
    fun getRemainingTimeMs(): Long
    
    /**
     * 편집 모드 설정
     *
     * @param enabled 편집 모드 활성화 여부
     */
    fun setEditMode(enabled: Boolean)
    
    /**
     * 편집 모드 토글
     */
    fun toggleEditMode()
    
    /**
     * 마커 삭제
     *
     * @param markerId 삭제할 마커 ID
     */
    fun deleteMarker(markerId: String)
    
    /**
     * 메모 다이얼로그 표시
     *
     * @param markerId 메모 다이얼로그를 표시할 마커 ID
     */
    fun showMemoDialog(markerId: String)
    
    /**
     * 메모 다이얼로그 숨김
     */
    fun hideMemoDialog()
    
    /**
     * 메모 다이얼로그 표시 완료 처리
     */
    fun onMemoDialogShown()
    
    /**
     * 메모 다이얼로그 닫힘 처리
     */
    fun onMemoDialogClosed()
    
    /**
     * 메모 콘텐츠를 업데이트합니다.
     *
     * @param memoId 업데이트할 메모 ID
     * @param content 업데이트할 메모 내용
     */
    fun updateMemoContent(memoId: String, content: String)
    
    /**
     * 매니저 초기화
     * 필요한 초기 설정을 수행합니다.
     */
    fun initialize()
    
    /**
     * 에러 메시지 스낵바 표시
     */
    fun showErrorSnackbar(view: View, message: String): Snackbar
    
    /**
     * 메모 작성 쓰기 모드 스낵바 표시
     */
    fun showWriteModeSnackbar(view: View): Snackbar
    
    /**
     * ViewModel 정리 작업 처리
     * @param viewModel 정리할 ViewModel 객체
     */
    fun cleanupViewModel(viewModel: Any)
    
    /**
     * ViewModel 등록
     * @param viewModel 등록할 ViewModel 객체
     */
    fun registerViewModel(viewModel: Any)
} 