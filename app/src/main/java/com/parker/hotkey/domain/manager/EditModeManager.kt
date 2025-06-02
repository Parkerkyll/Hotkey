package com.parker.hotkey.domain.manager

import android.view.View
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 편집 모드 상태를 정의하는 데이터 클래스
 */
data class EditModeState(
    val isEditMode: Boolean = false,
    val markerAlpha: Float = if (isEditMode) 1.0f else 0.4f,
    val remainingTimeMs: Long = 0
)

/**
 * 편집 모드 관련 이벤트를 정의하는 sealed 클래스
 */
sealed class EditModeEvent {
    data class ModeChanged(val isEditMode: Boolean) : EditModeEvent()
    data class TimerUpdated(val remainingTimeMs: Long) : EditModeEvent()
    object TimerExpired : EditModeEvent()
    data class Error(val message: String, val exception: Throwable = Exception(message)) : EditModeEvent()
}

/**
 * 편집 모드 관리 인터페이스
 */
interface EditModeManager {
    /**
     * 현재 편집 모드 상태 Flow
     */
    val editModeState: StateFlow<Boolean>
    
    /**
     * 현재 상세 상태 Flow
     */
    val state: StateFlow<EditModeState>
    
    /**
     * 편집 모드 이벤트 Flow
     */
    val events: SharedFlow<EditModeEvent>
    
    /**
     * 초기화 완료 상태
     */
    val initialized: StateFlow<Boolean>
    
    /**
     * 이벤트 구독 설정
     * 
     * @param scope 이벤트를 수집할 코루틴 스코프
     * @param handler 이벤트 처리 핸들러
     * @return 구독 작업 Job
     */
    fun subscribeToEvents(scope: CoroutineScope, handler: suspend (EditModeEvent) -> Unit): Job
    
    /**
     * 매니저 초기화
     * 필요한 초기 설정을 수행합니다.
     */
    fun initialize()
    
    /**
     * 편집 모드 전환
     */
    fun toggleEditMode()
    
    /**
     * 편집 모드 설정
     *
     * @param enabled 편집 모드 활성화 여부
     * @param resetTimer 타이머 리셋 여부 (기본값: true)
     * @param isUserAction 사용자 직접 액션인지 여부 (기본값: false)
     */
    fun setEditMode(enabled: Boolean, resetTimer: Boolean = true, isUserAction: Boolean = false)
    
    /**
     * 편집 모드 타이머 재시작
     */
    fun restartEditModeTimer()
    
    /**
     * 현재 편집 모드 반환
     *
     * @return 현재 편집 모드 (true: 편집 모드, false: 읽기 모드)
     */
    fun getCurrentMode(): Boolean
    
    /**
     * 편집 모드 검증
     * 편집 모드일 때만 action을 실행하고, 읽기 모드일 때는 메시지 표시
     *
     * @param view 현재 뷰
     * @param action 편집 모드 시 수행할 액션
     */
    fun validateEditMode(view: View, action: () -> Unit)
    
    /**
     * 편집 모드 변경 리스너 추가
     *
     * @param listener 편집 모드 변경 시 호출될 리스너
     */
    fun addOnModeChangeListener(listener: (Boolean) -> Unit)
    
    /**
     * 편집 모드 변경 리스너 제거
     *
     * @param listener 제거할 리스너
     */
    fun removeOnModeChangeListener(listener: (Boolean) -> Unit)
    
    /**
     * UI 참조 정리 메서드
     * Fragment의 onDestroyView에서 호출하여 메모리 누수 방지
     */
    fun clearUIReferences()
    
    /**
     * 모든 타이머 및 작업 정리
     */
    fun clearTimerAndJobs()
    
    /**
     * 쓰기 모드 메시지 표시
     * 
     * @param view 현재 뷰
     */
    fun showWriteModeMessage(view: View)
    
    /**
     * UI 컴포넌트 설정
     * 모드 변경에 따라 UI를 업데이트하기 위한 컴포넌트 참조 설정
     *
     * @param modeText 모드 텍스트 표시 TextView
     * @param modeSwitch 모드 전환 스위치
     * @param modeBar 모드 표시 바
     * @param editModeTimer 편집 모드 타이머 텍스트뷰 (null 가능)
     */
    fun updateUIComponents(
        modeText: TextView,
        modeSwitch: SwitchMaterial,
        modeBar: CardView,
        editModeTimer: TextView? = null
    )
    
    /**
     * 앱이 백그라운드로 진입할 때 호출 (확장 기능)
     * 구현 클래스에 따라 필요시 구현
     */
    fun onAppBackground() { /* 기본 구현은 아무 동작 안함 */ }
    
    /**
     * 앱이 포그라운드로 돌아올 때 호출 (확장 기능)
     * 구현 클래스에 따라 필요시 구현
     */
    fun onAppForeground() { /* 기본 구현은 아무 동작 안함 */ }
    
    /**
     * 현재 편집 모드 타이머 남은 시간 반환 (ms)
     * 
     * @return 남은 시간 (밀리초)
     */
    fun getRemainingTimeMs(): Long
} 