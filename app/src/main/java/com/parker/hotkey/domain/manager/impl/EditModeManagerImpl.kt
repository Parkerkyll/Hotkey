package com.parker.hotkey.domain.manager.impl

import android.view.View
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.NaverMap
import com.parker.hotkey.R
import com.parker.hotkey.di.qualifier.ApplicationScope
import com.parker.hotkey.domain.constants.MapConstants
import com.parker.hotkey.domain.constants.TimingConstants
import com.parker.hotkey.domain.constants.UIConstants
import com.parker.hotkey.domain.manager.BaseManager
import com.parker.hotkey.domain.manager.EditModeManager
import com.parker.hotkey.domain.manager.EditModeState
import com.parker.hotkey.domain.manager.EditModeEvent
import com.parker.hotkey.presentation.map.markers.MarkerUIDelegate
import com.parker.hotkey.util.dp2px
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EditModeManager 구현 클래스
 * 
 * 쓰기모드와 읽기모드의 상태를 관리하고, 타이머 기능을 제공합니다.
 * BaseManager를 상속하여 이벤트 처리 및 상태 관리 기능을 활용합니다.
 */
@Singleton
class EditModeManagerImpl @Inject constructor(
    @ApplicationScope coroutineScope: CoroutineScope
) : BaseManager<EditModeEvent>(coroutineScope), EditModeManager {
    
    // 상수 정의
    companion object {
        // TimingConstants로 이동
        // private const val MESSAGE_THROTTLE_MS = 1000L // 메시지 표시 간격 제한(1초)
        // private const val TIMER_UPDATE_INTERVAL_MS = 1000L // 타이머 갱신 주기(1초)
        
        // UIConstants로 이동
        // private const val SNACKBAR_DISMISS_EVENT_ACTION = 1 // Snackbar.Callback.DISMISS_EVENT_ACTION
        private const val USER_ACTION_PROTECTION_TIME_MS = 60000L  // 3초 => 1분으로 변경
    }
    
    // 상태 관리
    private val _editModeState = MutableStateFlow(false)
    override val editModeState: StateFlow<Boolean> = _editModeState
    
    private val _state = MutableStateFlow(EditModeState())
    override val state: StateFlow<EditModeState> = _state.asStateFlow()
    
    // 타이머 관리
    private var editModeJob: Job? = null
    private var editModeStartTime: Long = 0
    private var lastActionTime: Long = 0
    
    // UI 참조
    private var naverMapRef: WeakReference<NaverMap>? = null
    private var markerUIDelegateRef: WeakReference<MarkerUIDelegate>? = null
    
    // UI 컴포넌트 참조
    private var modeTextRef: WeakReference<TextView>? = null
    private var modeSwitchRef: WeakReference<SwitchMaterial>? = null
    private var modeBarRef: WeakReference<CardView>? = null
    private var editModeTimerRef: WeakReference<TextView>? = null

    // 콜백 관리
    private val onModeChangeListeners = mutableSetOf<(Boolean) -> Unit>()
    private var lastMessageTime = 0L
    private var onMapClickInEditMode: ((LatLng) -> Unit)? = null
    private var onMapClickInReadMode: (() -> Unit)? = null

    // 동기화를 위한 락 객체
    private val stateLock = Any()

    // 사용자 모드 변경 시간 추적을 위한 변수 추가
    private var lastUserModeChangeTime: Long = 0

    /**
     * 초기화
     */
    init {
        // 명시적 initialize 호출로 이동
        Timber.d("EditModeManagerImpl 생성됨")
    }
    
    /**
     * 매니저 초기화
     * 필요한 초기 설정을 수행합니다.
     */
    override fun initialize() {
        // 공통 초기화 로직 사용
        initializeCommon("EditModeManager") {
            // 초기 상태 설정
            updateState { 
                EditModeState(
                    isEditMode = false,
                    markerAlpha = 0.4f,
                    remainingTimeMs = 0
                ) 
            }
        }
    }
    
    /**
     * UI 관련 초기화 - 맵, 마커 UI, 클릭 이벤트 처리
     */
    fun initialize(
        map: NaverMap,
        delegate: MarkerUIDelegate,
        onMapClickInEditMode: (LatLng) -> Unit,
        onMapClickInReadMode: () -> Unit
    ) {
        Timber.d("EditModeManager UI 초기화 시작")
        naverMapRef = WeakReference(map)
        markerUIDelegateRef = WeakReference(delegate)
        this.onMapClickInEditMode = onMapClickInEditMode
        this.onMapClickInReadMode = onMapClickInReadMode
        
        // 네이버 지도 UI 설정 - 줌 컨트롤 버튼 숨기기
        map.uiSettings.isZoomControlEnabled = false
        Timber.d("네이버 지도 줌 컨트롤 버튼 비활성화")
        
        setupMapClickListener(map)
        setEditMode(false)
        Timber.d("EditModeManager UI 초기화 완료")
    }
    
    /**
     * UI 컴포넌트 설정
     */
    override fun updateUIComponents(
        modeText: TextView,
        modeSwitch: SwitchMaterial,
        modeBar: CardView,
        editModeTimer: TextView?
    ) {
        Timber.d("UI 컴포넌트 참조 설정")
        this.modeTextRef = WeakReference(modeText)
        this.modeSwitchRef = WeakReference(modeSwitch)
        this.modeBarRef = WeakReference(modeBar)
        this.editModeTimerRef = editModeTimer?.let { WeakReference(it) }
    }

    /**
     * 편집 모드 전환
     */
    override fun toggleEditMode() {
        val newMode = !_editModeState.value
        Timber.d("toggleEditMode 호출됨: 현재=${if(_editModeState.value) "쓰기" else "읽기"}, 변경=${if(newMode) "쓰기" else "읽기"}")
        
        synchronized(stateLock) {
            try {
                // 상태 업데이트
                _editModeState.value = newMode
                updateState { 
                    it.copy(
                        isEditMode = newMode,
                        markerAlpha = if (newMode) 1.0f else 0.4f
                    )
                }
                
                Timber.d("편집 모드 전환: ${if (newMode) "쓰기모드" else "읽기모드"}")
                
                // 이벤트 발행
                coroutineScope.launch {
                    bufferOrEmitEvent(EditModeEvent.ModeChanged(newMode))
                }
                
                handleModeChange(newMode, true)
                
                // 리스너들에게 상태 변경 알림
                Timber.d("모드 변경 리스너 ${onModeChangeListeners.size}개에 통지")
                onModeChangeListeners.forEach { listener ->
                    try {
                        listener(newMode) 
                        Timber.d("리스너 호출 성공")
                    } catch (e: Exception) {
                        Timber.e(e, "모드 변경 리스너 호출 중 오류 발생")
                    }
                }
            } catch (e: Exception) {
                handleError(e, "모드 전환 오류", emitErrorEvent = true)
            }
        }
    }
    
    /**
     * 편집 모드 설정
     * 
     * @param enabled 편집 모드 활성화 여부
     * @param resetTimer 타이머 리셋 여부 (기본값: true)
     * @param isUserAction 사용자 직접 액션인지 여부 (기본값: false)
     */
    override fun setEditMode(enabled: Boolean, resetTimer: Boolean, isUserAction: Boolean) {
        // 요청된 상태 로깅 - 명확한 로그 형식 사용
        Timber.d("[모드 변경] 요청: ${if(enabled) "쓰기" else "읽기"}모드, 현재: ${if(_editModeState.value) "쓰기" else "읽기"}모드, 타이머리셋: $resetTimer, 사용자액션: $isUserAction")
        
        // 사용자 액션인 경우 보호 시간 업데이트 
        if (isUserAction) {
            lastUserModeChangeTime = System.currentTimeMillis()
            Timber.d("[모드 변경] 사용자 직접 변경 시간 기록: $lastUserModeChangeTime")
        }
        
        // 상태가 이미 같으면 UI 업데이트만 수행
        if (_editModeState.value == enabled) {
            Timber.d("[모드 변경] 이미 ${if(enabled) "쓰기" else "읽기"}모드 상태입니다. 상태 변경 없음.")
            
            // UI 상태가 실제 상태와 일치하는지 확인하고 필요시 강제 업데이트
            forceUpdateUIIfNeeded(enabled)
            return
        }
        
        // 사용자 액션이 아니고, 마지막 사용자 액션으로부터 3초 이내인 경우 상태 변경 방지
        // 이것은 사용자 액션을 자동 시스템 동작보다 우선시함
        val protectionTimeElapsed = System.currentTimeMillis() - lastUserModeChangeTime
        if (!isUserAction && lastUserModeChangeTime > 0 && protectionTimeElapsed < USER_ACTION_PROTECTION_TIME_MS) {
            Timber.d("[모드 변경] 사용자 액션 보호 기간 (${protectionTimeElapsed}ms) - 자동 상태 변경 무시")
            return
        }
        
        // 상태 변경 처리 
        performModeChange(enabled, resetTimer)
    }
    
    /**
     * 모드 변경 실행 - 내부 메서드
     */
    private fun performModeChange(enabled: Boolean, resetTimer: Boolean) {
        Timber.d("[모드 변경] 상태 변경 시작: ${if(_editModeState.value) "쓰기" else "읽기"} -> ${if(enabled) "쓰기" else "읽기"}")
        
        val remainingTime: Long
        
        synchronized(stateLock) {
            try {
                // 타이머 관련 로직 먼저 처리
                if (!enabled) {
                    // 읽기 모드로 변경 시 항상 타이머 취소
                    cancelEditModeTimer()
                    Timber.d("[모드 변경] 읽기 모드로 변경 - 타이머 취소")
                }
                
                // 상태 변경
                _editModeState.value = enabled
                
                // 편집 모드 시작 시간 초기화
                remainingTime = if (enabled && resetTimer) {
                    TimingConstants.EDIT_MODE_TIMEOUT_MS
                } else if (!enabled) {
                    0 // 읽기 모드로 변경 시 항상 타이머는 0
                } else {
                    _state.value.remainingTimeMs // 타이머 리셋 않으면 현재 값 유지
                }
                
                // 상태 업데이트
                updateState {
                    it.copy(
                        isEditMode = enabled,
                        remainingTimeMs = remainingTime
                    )
                }
                
                Timber.d("[모드 변경] 상태 업데이트 완료: ${if (enabled) "쓰기모드" else "읽기모드"}, 타임아웃: ${remainingTime}ms")
                
                // 이벤트 발행
                coroutineScope.launch {
                    bufferOrEmitEvent(EditModeEvent.ModeChanged(enabled))
                }
                
            } catch (e: Exception) {
                Timber.e(e, "[모드 변경] 오류 발생")
                handleError(e, "모드 변경 오류", emitErrorEvent = true)
                return
            }
        }
        
        // UI 업데이트 (메인 스레드에서 실행)
        coroutineScope.launch(Dispatchers.Main) {
            try {
                // UI 상태 업데이트 (스위치 포함)
                updateUIComponentsForMode(enabled)
                Timber.d("[모드 변경] UI 업데이트 완료")
                
                // 모드 변경 처리
                handleModeChange(enabled, resetTimer)
                
                // 리스너에게 상태 변경 알림
                notifyListeners(enabled)
                Timber.d("[모드 변경] 리스너 호출 완료")
            } catch (e: Exception) {
                Timber.e(e, "[모드 변경] UI 업데이트 중 오류 발생")
                handleError(e, "UI 업데이트 오류", emitErrorEvent = false)
            }
        }
    }
    
    /**
     * 필요한 경우 UI 강제 업데이트 수행
     */
    private fun forceUpdateUIIfNeeded(enabled: Boolean) {
        coroutineScope.launch(Dispatchers.Main) {
            try {
                // UI 컴포넌트가 설정되었는지 확인
                val uiComponentsAvailable = modeTextRef?.get() != null && 
                                          modeSwitchRef?.get() != null && 
                                          modeBarRef?.get() != null
                
                if (uiComponentsAvailable) {
                    // 스위치 상태 확인
                    val currentSwitchState = modeSwitchRef?.get()?.isChecked ?: false
                    
                    // 스위치 상태가 실제 상태와 다르면 강제 업데이트
                    if (currentSwitchState != enabled) {
                        Timber.d("[모드 변경] UI 불일치 감지 - 강제 업데이트: UI=${if(currentSwitchState) "쓰기" else "읽기"}, 실제=${if(enabled) "쓰기" else "읽기"}")
                        updateUIComponentsForMode(enabled)
                    } else {
                        Timber.d("[모드 변경] UI 상태 일치 확인됨 - 강제 업데이트 불필요")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "[모드 변경] UI 강제 업데이트 중 오류 발생")
            }
        }
    }
    
    /**
     * 리스너에게 모드 변경 알림 - 별도의 메서드로 분리하여 오류 처리 개선
     */
    private fun notifyListeners(isEditMode: Boolean) {
        try {
            Timber.d("모드 변경 리스너 ${onModeChangeListeners.size}개에 통지 시작: isEditMode=$isEditMode")
            
            // 각 리스너에게 별도로 알림 (한 리스너의 오류가 다른 리스너에 영향 주지 않도록)
            onModeChangeListeners.forEach { listener ->
                try {
                    listener(isEditMode)
                } catch (e: Exception) {
                    Timber.e(e, "모드 변경 리스너 호출 중 오류 발생")
                }
            }
            
            Timber.d("모드 변경 리스너 통지 완료")
        } catch (e: Exception) {
            Timber.e(e, "모드 변경 리스너 통지 중 오류 발생")
        }
    }

    /**
     * 리스너 추가
     */
    override fun addOnModeChangeListener(listener: (Boolean) -> Unit) {
        onModeChangeListeners.add(listener)
    }
    
    /**
     * 리스너 제거
     */
    override fun removeOnModeChangeListener(listener: (Boolean) -> Unit) {
        onModeChangeListeners.remove(listener)
    }

    /**
     * 모드 변경 처리 - 타이머 시작/취소 및 UI 업데이트
     * 
     * @param newMode 새로운 모드 (true: 쓰기모드, false: 읽기모드)
     * @param resetTimer 타이머 리셋 여부 (기본값: true)
     */
    private fun handleModeChange(newMode: Boolean, resetTimer: Boolean) {
        try {
            Timber.d("모드 변경 처리: newMode=$newMode, resetTimer=$resetTimer")
            
            if (newMode) {
                // 쓰기 모드로 변경
                if (resetTimer) {
                    startEditModeTimer()
                } else {
                    // 타이머 상태는 유지하되 기존 타이머는 취소하고 재시작
                    restartTimerWithCurrentRemaining()
                }
                updateMapUI(true)
            } else {
                // 읽기 모드로 변경 (타이머는 이미 취소됨)
                updateMapUI(false)
            }
            
            Timber.d("모드 변경 처리 완료")
        } catch (e: Exception) {
            Timber.e(e, "모드 변경 처리 중 오류 발생")
        }
    }
    
    /**
     * 현재 남은 시간으로 타이머 재시작
     */
    private fun restartTimerWithCurrentRemaining() {
        val remainingTime = _state.value.remainingTimeMs
        
        // 남은 시간이 없거나 너무 적으면 타이머 시작하지 않음
        if (remainingTime <= 500) {
            Timber.d("남은 시간이 너무 적어 타이머 재시작 하지 않음: ${remainingTime}ms")
            return
        }
        
        // 기존 타이머 취소
        editModeJob?.cancel()
        
        // 새 타이머 시작 시점과 종료 시점 계산
        editModeStartTime = System.currentTimeMillis()
        val absoluteEndTime = editModeStartTime + remainingTime
        
        editModeJob = coroutineScope.launch {
            try {
                Timber.d("타이머 재시작: 남은 시간 ${remainingTime}ms")
                
                // 초기 타이머 이벤트 발행
                bufferOrEmitEvent(EditModeEvent.TimerUpdated(remainingTime))
                
                while (isActive && _editModeState.value) {
                    val currentTime = System.currentTimeMillis()
                    
                    // 남은 시간 계산
                    val newRemainingTime = (absoluteEndTime - currentTime).coerceAtLeast(0)
                    
                    // 상태 업데이트
                    updateState {
                        it.copy(remainingTimeMs = newRemainingTime)
                    }
                    
                    // 타이머 UI 업데이트
                    updateTimerUI(newRemainingTime)
                    
                    // 타이머 이벤트 발행
                    bufferOrEmitEvent(EditModeEvent.TimerUpdated(newRemainingTime))
                    
                    // 시간이 다 되면 타이머 종료
                    if (newRemainingTime <= 0) {
                        Timber.d("재시작된 타이머 종료: 시간 초과")
                        
                        // 타이머 만료 이벤트 발행
                        bufferOrEmitEvent(EditModeEvent.TimerExpired, highPriority = true)
                        
                        // Main 디스패처에서 UI 상태 변경
                        withContext(Dispatchers.Main) {
                            _editModeState.value = false
                            updateState { 
                                it.copy(
                                    isEditMode = false,
                                    markerAlpha = 0.4f,
                                    remainingTimeMs = 0
                                ) 
                            }
                            
                            // 모드 변경 이벤트 발행
                            launch {
                                bufferOrEmitEvent(EditModeEvent.ModeChanged(false), highPriority = true)
                            }
                            
                            // UI 업데이트
                            updateMapUI(false)
                            
                            // 리스너에게 모드 변경 알림
                            onModeChangeListeners.forEach { it(false) }
                        }
                        
                        Timber.d("재시작된 타이머 종료로 인한 읽기 모드 전환 완료")
                        break
                    }
                    
                    // 대기 후 다음 주기
                    delay(TimingConstants.TIMER_UPDATE_INTERVAL_MS)
                }
            } catch (e: CancellationException) {
                Timber.d("재시작된 타이머 취소됨")
            } catch (e: Exception) {
                Timber.e(e, "재시작된 타이머 오류: ${e.message}")
                handleError(e, "타이머 오류", emitErrorEvent = true)
            }
        }
    }

    /**
     * 편집 모드 타이머 재시작
     */
    override fun restartEditModeTimer() {
        // 쓰기 모드가 아니면 재시작하지 않음
        if (!_editModeState.value) {
            Timber.d("쓰기 모드가 아닌 상태에서 타이머 재시작 요청이 무시됨")
            return
        }
        
        val currentTime = System.currentTimeMillis()
        
        // 현재 남은 시간 확인
        val remainingTimeMs = _state.value.remainingTimeMs
        
        // 남은 시간이 너무 적으면(500ms 이하) 재시작하지 않음
        if (remainingTimeMs <= 500) {
            Timber.d("타이머 종료가 임박하여 재시작하지 않음 (남은 시간: ${remainingTimeMs}ms)")
            return
        }
        
        Timber.d("쓰기 모드 타이머 재시작: 마지막 액션으로부터 ${currentTime - lastActionTime}ms 경과, 남은 시간: ${remainingTimeMs}ms")
        lastActionTime = currentTime
        
        // 수정: 이전 타이머 취소 후 새로운 타이머를 시작하는 대신 
        // 현재 남은 시간을 유지하면서 타이머만 재시작
        cancelEditModeTimer()
        restartTimerWithCurrentRemaining()
    }

    /**
     * 편집 모드 타이머 시작
     */
    private fun startEditModeTimer() {
        // 기존 타이머 취소
        cancelEditModeTimer()
        
        // 타이머 시작 시점 저장
        editModeStartTime = System.currentTimeMillis()
        lastActionTime = editModeStartTime
        
        editModeJob = coroutineScope.launch {
            try {
                // 편집 모드 상태가 true일 때만 타이머 실행
                if (_editModeState.value) {
                    Timber.d("편집 모드 타이머 시작: ${TimingConstants.EDIT_MODE_TIMEOUT_MS}ms, 종료 예정: ${System.currentTimeMillis() + TimingConstants.EDIT_MODE_TIMEOUT_MS}ms")
                    
                    // 초기 타이머 이벤트 발행
                    val timerUpdatedEvent = EditModeEvent.TimerUpdated(TimingConstants.EDIT_MODE_TIMEOUT_MS)
                    bufferOrEmitEvent(timerUpdatedEvent)
                    
                    // 타이머 종료 시간 계산 (절대 값)
                    val absoluteEndTime = editModeStartTime + TimingConstants.EDIT_MODE_TIMEOUT_MS
                    
                    // 타이머 루프
                    while (isActive && _editModeState.value) {
                        val currentTime = System.currentTimeMillis()
                        
                        // 남은 시간 계산
                        val remainingTime = (absoluteEndTime - currentTime).coerceAtLeast(0)
                        
                        // 상태 업데이트
                        updateState {
                            it.copy(remainingTimeMs = remainingTime)
                        }
                        
                        // 타이머 UI 업데이트
                        updateTimerUI(remainingTime)
                        
                        // 타이머 이벤트 발행
                        bufferOrEmitEvent(EditModeEvent.TimerUpdated(remainingTime))
                        
                        // 시간이 다 되면 타이머 종료
                        if (remainingTime <= 0) {
                            Timber.d("편집 모드 타이머 종료: 시간 초과 (${TimingConstants.EDIT_MODE_TIMEOUT_MS}ms)")
                            
                            // 상태 변경 전에 타이머 만료 이벤트 먼저 발행 (중요)
                            bufferOrEmitEvent(EditModeEvent.TimerExpired, highPriority = true)
                            
                            // Main 디스패처에서 UI 상태 변경
                            withContext(Dispatchers.Main) {
                                // 상태가 아직 쓰기 모드인지 확인 (다른 요인으로 이미 읽기 모드로 변경됐을 수 있음)
                                if (_editModeState.value) {
                                    Timber.d("타이머 종료 - 읽기 모드로 자동 전환 시작")
                                    
                                    // 읽기 모드로 전환 (재귀호출 방지를 위해 내부 상태 직접 변경)
                                    synchronized(stateLock) {
                                        _editModeState.value = false
                                        updateState { 
                                            it.copy(
                                                isEditMode = false,
                                                markerAlpha = 0.4f,
                                                remainingTimeMs = 0
                                            ) 
                                        }
                                    }
                                    
                                    // UI 업데이트 먼저 수행 (빠른 피드백)
                                    updateUIComponentsForMode(false)
                                    
                                    // 이벤트 발행 및 리스너 알림
                                    launch {
                                        bufferOrEmitEvent(EditModeEvent.ModeChanged(false), highPriority = true)
                                    }
                                    
                                    // 맵 UI 업데이트
                                    updateMapUI(false)
                                    
                                    // 리스너에게 모드 변경 알림
                                    notifyListeners(false)
                                    
                                    Timber.d("타이머 종료로 인한 읽기 모드 전환 완료")
                                } else {
                                    Timber.d("타이머 종료됐으나 이미 읽기 모드 상태임 - 추가 전환 불필요")
                                }
                            }
                            
                            break
                        }
                        
                        // 대기 후 다음 주기
                        delay(TimingConstants.TIMER_UPDATE_INTERVAL_MS)
                    }
                }
            } catch (e: CancellationException) {
                Timber.d("편집 모드 타이머 취소됨")
            } catch (e: Exception) {
                Timber.e(e, "편집 모드 타이머 오류: ${e.message}")
                handleError(e, "타이머 오류", emitErrorEvent = true)
            }
        }
    }

    /**
     * 타이머 업데이트 이벤트 처리
     */
    private fun handleTimerUpdated(event: EditModeEvent.TimerUpdated) {
        updateTimerUI(event.remainingTimeMs)
    }

    /**
     * 편집 모드 타이머 취소
     */
    private fun cancelEditModeTimer() {
        editModeJob?.let {
            if (it.isActive) {
                it.cancel()
                Timber.d("편집 모드 타이머 취소됨")
            }
        }
        editModeJob = null
    }

    /**
     * 맵 UI 업데이트
     */
    private fun updateMapUI(isEditMode: Boolean) {
        // 네이버 맵 관련 UI 업데이트
        naverMapRef?.get()?.let { map ->
            coroutineScope.launch(Dispatchers.Main) {
                try {
                    // 기존 카메라 위치 저장
                    val currentZoom = map.cameraPosition.zoom
                    
                    // 편집 모드에 따라 줌 레벨과 마커 알파 조정
                    if (isEditMode) {
                        // 편집 모드 활성화 시 줌 레벨 설정
                        if (currentZoom < MapConstants.EDIT_MODE_ZOOM) {
                            map.moveCamera(
                                CameraUpdate.zoomTo(MapConstants.EDIT_MODE_ZOOM)
                            )
                        }
                        
                        // 쓰기모드에서는 줌 레벨을 고정 (확대/축소 불가)
                        map.minZoom = MapConstants.EDIT_MODE_ZOOM
                        map.maxZoom = MapConstants.EDIT_MODE_ZOOM
                        Timber.d("쓰기모드: 맵 줌 레벨을 ${MapConstants.EDIT_MODE_ZOOM}으로 고정")
                        
                        // 마커 강조
                        markerUIDelegateRef?.get()?.updateMarkerOpacity(1.0f)
                    } else {
                        // 읽기모드에서는 원래의 줌 레벨 범위로 복원
                        map.minZoom = MapConstants.MIN_ZOOM
                        map.maxZoom = MapConstants.MAX_ZOOM
                        Timber.d("읽기모드: 맵 줌 레벨 범위 복원 (${MapConstants.MIN_ZOOM} ~ ${MapConstants.MAX_ZOOM})")
                        
                        // 마커 투명도 설정
                        markerUIDelegateRef?.get()?.updateMarkerOpacity(0.5f)
                    }
                } catch (e: Exception) {
                    handleError(e, "UI 업데이트 오류", emitErrorEvent = true)
                }
            }
        }
        
        // UI 컴포넌트 업데이트
        updateUIComponentsForMode(isEditMode)
    }
    
    /**
     * 모드에 따른 UI 컴포넌트 업데이트
     */
    private fun updateUIComponentsForMode(isEditMode: Boolean) {
        coroutineScope.launch(Dispatchers.Main) {
            try {
                // 모드 텍스트 및 색상 업데이트
                modeTextRef?.get()?.let { textView ->
                    val context = textView.context
                    // 모드 텍스트 설정
                    textView.text = context.getString(if (isEditMode) R.string.write_mode else R.string.read_mode)
                    
                    // 텍스트 색상 설정
                    val textColor = if (isEditMode) {
                        ContextCompat.getColor(context, R.color.write_mode_text)
                    } else {
                        ContextCompat.getColor(context, R.color.read_mode_text)
                    }
                    textView.setTextColor(textColor)
                    
                    Timber.d("모드 텍스트 업데이트: ${textView.text}")
                }
                
                // 모드 바 업데이트
                modeBarRef?.get()?.let { cardView ->
                    val context = cardView.context
                    val backgroundColor = ContextCompat.getColor(context, R.color.mode_bar_background)
                    cardView.setCardBackgroundColor(backgroundColor)
                    cardView.cardElevation = 4f
                }
                
                // 타이머 표시 업데이트
                editModeTimerRef?.get()?.visibility = if (isEditMode) View.VISIBLE else View.GONE
                
                // 스위치 상태 업데이트 - 리스너 호출하지 않도록 주의
                modeSwitchRef?.get()?.let { switch ->
                    val currentChecked = switch.isChecked
                    Timber.d("스위치 상태 확인: 현재=${if(currentChecked) "쓰기" else "읽기"}, 목표=${if(isEditMode) "쓰기" else "읽기"}")
                    
                    if (currentChecked != isEditMode) {
                        // 리스너를 임시로 제거하고 상태만 변경
                        switch.setOnCheckedChangeListener(null)
                        switch.isChecked = isEditMode
                        Timber.d("스위치 상태 직접 변경: ${if(isEditMode) "쓰기" else "읽기"}모드")
                        
                        // 애플리케이션 내부 전파 방지를 위해 빈 리스너 설정
                        // 여기서는 MapUIController가 리스너를 다시 설정할 것임
                    }
                }
                
                Timber.d("UI 컴포넌트 업데이트 완료: isEditMode=$isEditMode")
            } catch (e: Exception) {
                Timber.e(e, "UI 컴포넌트 업데이트 중 오류 발생")
            }
        }
    }
    
    /**
     * 타이머 UI 업데이트
     */
    private fun updateTimerUI(remainingTimeMs: Long) {
        coroutineScope.launch(Dispatchers.Main) {
            try {
                val seconds = (remainingTimeMs / 1000).toInt()
                val minutes = seconds / 60
                val remainingSeconds = seconds % 60
                
                editModeTimerRef?.get()?.let { timer ->
                    timer.text = String.format("%02d:%02d", minutes, remainingSeconds)
                    timer.visibility = if (remainingTimeMs > 0) View.VISIBLE else View.GONE
                }
                
                Timber.d("타이머 UI 업데이트: ${minutes}분 ${remainingSeconds}초")
            } catch (e: Exception) {
                Timber.e(e, "타이머 UI 업데이트 중 오류 발생")
            }
        }
    }

    /**
     * 모든 타이머 및 작업 정리
     */
    override fun clearTimerAndJobs() {
        cancelEditModeTimer()
        onModeChangeListeners.clear()
        clearUIReferences()
        onMapClickInEditMode = null
        onMapClickInReadMode = null
        Timber.d("EditModeManager 리소스 정리 완료")
    }
    
    /**
     * UI 참조 정리 메서드
     * Fragment의 onDestroyView에서 호출하여 메모리 누수 방지
     */
    override fun clearUIReferences() {
        Timber.d("UI 참조 정리 시작")
        // UI 참조만 정리하고 모드 상태는 유지 (백그라운드에서 돌아와도 쓰기모드 유지)
        modeTextRef = null
        modeSwitchRef = null
        modeBarRef = null
        editModeTimerRef = null
        Timber.d("UI 참조 정리 완료")
    }

    /**
     * 현재 모드 반환
     */
    override fun getCurrentMode(): Boolean {
        return _editModeState.value
    }
    
    /**
     * 현재 편집 모드 타이머 남은 시간 반환 (ms)
     */
    override fun getRemainingTimeMs(): Long {
        return _state.value.remainingTimeMs
    }

    /**
     * 쓰기 모드 메시지 표시
     */
    override fun showWriteModeMessage(view: View) {
        // 현재 상태에 따라 다른 메시지 표시
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastMessageTime < TimingConstants.MESSAGE_THROTTLE_MS) return
        lastMessageTime = currentTime
        
        if (_editModeState.value) {
            // 쓰기 모드일 때는 타이머 정보가 포함된 활성화 메시지 표시
            Timber.d("쓰기 모드 활성화 메시지 표시")
            
            Snackbar.make(view, view.context.getString(R.string.write_mode_active), Snackbar.LENGTH_LONG)
                .apply {
                    this.setText(
                        view.context.getString(
                            R.string.write_mode_auto_switch_message,
                            TimingConstants.EDIT_MODE_TIMEOUT_MS / 1000
                        )
                    )
                    
                    this.addCallback(object : Snackbar.Callback() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            super.onDismissed(transientBottomBar, event)
                            if (event == UIConstants.SNACKBAR_DISMISS_EVENT_ACTION) {
                                Timber.d("사용자가 쓰기 모드 메시지를 닫음. 편집 모드 유지")
                            }
                        }
                    })
                    
                    this.view.translationY = -(UIConstants.SNACKBAR_BOTTOM_MARGIN_DP.dp2px(view.resources))
                }
                .show()
        } else {
            // 읽기 모드일 때는 쓰기 모드로 전환하라는 메시지 표시
            Timber.d("쓰기 모드 전환 필요 메시지 표시")
            
            Snackbar.make(view, view.context.getString(R.string.write_mode_message), Snackbar.LENGTH_LONG)
                .apply {
                    setAction(view.context.getString(R.string.to_write_mode)) {
                        toggleEditMode()
                        Timber.d("사용자가 스낵바에서 쓰기 모드 전환 버튼 클릭")
                    }
                    
                    this.view.translationY = -(UIConstants.SNACKBAR_BOTTOM_MARGIN_DP.dp2px(view.resources))
                }
                .show()
        }
    }

    /**
     * 맵 클릭 핸들러
     */
    fun handleMapClick(coord: LatLng) {
        // 현재 모드를 로깅
        Timber.d("지도 클릭 이벤트 처리: 현재 모드=${if (_editModeState.value) "쓰기모드" else "읽기모드"}")
        
        if (_editModeState.value) {
            Timber.d("쓰기 모드에서 지도 클릭 - 이벤트 처리")
            onMapClickInEditMode?.invoke(coord)
            // 편집 모드 타이머 재시작
            restartEditModeTimer()
        } else {
            Timber.d("읽기 모드에서 지도 클릭 - 쓰기 모드 전환 안내 메시지 표시")
            // 바로 쓰기 모드로 변경하지 않고 onMapClickInReadMode 호출
            // 이 콜백이 MapFragment에서 스낵바를 표시하도록 설정됨
            onMapClickInReadMode?.invoke()
        }
    }

    /**
     * 맵 클릭 리스너 설정
     */
    private fun setupMapClickListener(map: NaverMap) {
        map.setOnMapClickListener { _, latLng ->
            Timber.d("지도 클릭 이벤트 발생: 좌표=(${latLng.latitude}, ${latLng.longitude})")
            handleMapClick(latLng)
        }
        
        // 마커 클릭시 항상 처리되도록 기본 리스너 구성은 유지
        Timber.d("지도 클릭 리스너 설정 완료")
    }

    /**
     * 편집 모드 검증
     */
    override fun validateEditMode(view: View, action: () -> Unit) {
        if (getCurrentMode()) {
            action()
        } else {
            showWriteModeMessage(view)
        }
    }
    
    /**
     * 상태 업데이트 헬퍼 메서드 - 동기화 문제 방지를 위해 동기화 적용
     */
    private fun updateState(update: (EditModeState) -> EditModeState) {
        synchronized(stateLock) {
            try {
                val newState = update(_state.value)
                _state.value = newState
                Timber.v("상태 업데이트: $newState")
            } catch (e: Exception) {
                Timber.e(e, "상태 업데이트 중 오류 발생")
                setError(e)
            }
        }
    }
    
    /**
     * 이벤트 발행 메서드
     * - 기존 emitEvent 메서드가 있다면 이를 bufferOrEmitEvent로 변경
     */
    private suspend fun emitEvent(event: EditModeEvent) {
        bufferOrEmitEvent(event)
    }
    
    /**
     * 오류 이벤트 변환 구현
     */
    override fun createErrorEvent(throwable: Throwable, message: String): EditModeEvent {
        return EditModeEvent.Error(message, throwable)
    }

    /**
     * 이벤트 구독 설정
     * 
     * @param scope 이벤트를 수집할 코루틴 스코프
     * @param handler 이벤트 처리 핸들러
     * @return 구독 작업 Job
     */
    override fun subscribeToEvents(scope: CoroutineScope, handler: suspend (EditModeEvent) -> Unit): Job {
        return scope.launch {
            events.collect { event ->
                try {
                    handler(event)
                    
                    // TimerExpired 이벤트 발생 시 읽기 모드로 강제 전환 확인
                    if (event is EditModeEvent.TimerExpired && _editModeState.value) {
                        Timber.d("TimerExpired 이벤트 처리 후 읽기 모드 전환 확인")
                        if (_editModeState.value) {
                            Timber.w("타이머 만료됐으나 여전히 쓰기 모드 상태임. 강제로 읽기 모드로 전환")
                            // 강제로 읽기 모드로 전환
                            setEditMode(false, true, false)
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "이벤트 핸들러 오류: ${e.message}")
                }
            }
        }
    }

    // (신규 추가) 백그라운드/포그라운드 전환 처리 메서드 추가
    /**
     * 앱이 백그라운드로 진입할 때 호출
     * 이제 쓰기모드를 읽기모드로 강제로 전환하지 않고 타이머만 유지
     */
    override fun onAppBackground() {
        Timber.d("앱 백그라운드 진입 - 타이머 상태 저장")
        // 상태만 저장하고 모드는 변경하지 않음
        // 여기서는 아무것도 하지 않음 - 타이머 유지
    }

    /**
     * 앱이 포그라운드로 돌아올 때 호출
     * 타이머가 아직 남아있다면 쓰기모드를 유지하고 UI 업데이트
     */
    override fun onAppForeground() {
        Timber.d("EditModeManager - 앱이 포그라운드로 돌아옴. 현재 상태: ${if (_editModeState.value) "쓰기모드" else "읽기모드"}, 남은 시간: ${_state.value.remainingTimeMs}ms")
        
        // 포그라운드로 돌아와도 쓰기모드 상태를 유지 (타이머 만료되지 않았다면)
        if (_editModeState.value) {
            // 상태를 현재 그대로 유지하되 타이머 재시작
            val remainingTime = _state.value.remainingTimeMs
            if (remainingTime > 500) { // 아직 타이머가 충분히 남아있다면
                Timber.d("쓰기모드 유지 및 타이머 재시작 (남은 시간: ${remainingTime}ms)")
                restartEditModeTimer()
            } else {
                Timber.d("타이머 시간이 거의 만료됨 (남은 시간: ${remainingTime}ms)")
            }
        }
    }
} 