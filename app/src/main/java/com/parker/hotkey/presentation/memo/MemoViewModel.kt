package com.parker.hotkey.presentation.memo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parker.hotkey.domain.constants.MemoConstants
import com.parker.hotkey.domain.manager.EditModeManager
import com.parker.hotkey.domain.manager.MemoEvent
import com.parker.hotkey.domain.manager.MemoManager
import com.parker.hotkey.domain.model.Memo
import com.parker.hotkey.domain.model.state.DialogState
import com.parker.hotkey.presentation.state.MemoUiState
import com.parker.hotkey.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 메모 관련 기능을 담당하는 ViewModel
 * 
 * TODO: 다음 단계 리팩토링 계획
 * 1. 현재 이 클래스는 다음 세 가지 책임을 가짐:
 *   - 메모 다이얼로그 상태 관리 (MemoDialogStateHolder로 분리 예정)
 *   - 메모 이벤트 처리 (MemoEventHandler로 분리 예정)
 *   - 메모 관련 비즈니스 로직 (MemoInteractor로 분리 완료)
 * 
 * 2. 리팩토링 단계:
 *   - 다이얼로그 상태 관리 관련 코드를 MemoDialogStateHolder로 이동
 *   - 이벤트 처리 관련 코드를 MemoEventHandler로 이동
 *   - 비즈니스 로직 관련 코드를 MemoInteractor로 이동 (완료)
 *   - 각 컴포넌트 간 인터페이스 정의
 */
@HiltViewModel
class MemoViewModel @Inject constructor(
    private val memoInteractor: MemoInteractor, // MemoInteractor 의존성 주입
    private val editModeManager: EditModeManager,
    private val memoManager: MemoManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    // 메모 상태 (비즈니스 로직 관련)
    private val _memoState = MutableStateFlow<MemoUiState>(MemoUiState.Initial)
    val memoState: StateFlow<MemoUiState> = _memoState.asStateFlow()
    
    private var loadMemosJob: Job? = null
    
    // 디바운싱을 위한 변수
    private var lastLoadedMarkerId: String? = null
    private val loadMemoDebounceTime = 500L // 0.5초 디바운싱
    private var isLoadingMemos = false

    // 다이얼로그 상태 관리 관련 코드 (MemoDialogStateHolder로 이동 예정) -----
    
    private val _memoDialogState = MutableStateFlow(DialogState())
    val shouldShowMemoDialog: StateFlow<Boolean> = _memoDialogState.map { it.isVisible }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            // WhileSubscribed에서 Eagerly로 다시 변경
            SharingStarted.Eagerly,
            false
        )

    // 메모장에 표시할 마커 ID
    val selectedMemoMarkerId: StateFlow<String?> = _memoDialogState.map { it.markerId }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope, 
            SharingStarted.Eagerly, // WhileSubscribed에서 Eagerly로 다시 변경
            null
        )
    
    // 임시 마커 여부 Flow 추가
    val isTemporaryMarker: StateFlow<Boolean> = _memoDialogState.map { it.isTemporary }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly, // WhileSubscribed에서 Eagerly로 다시 변경
            false
        )
    // 다이얼로그 상태 관리 관련 코드 끝 ------------------------------------

    // Flow 구독 및 코루틴 Job 참조 저장
    private var flowCollectionJobs = mutableListOf<Job>()
    
    // 마지막으로 처리된 마커 ID와 타임스탬프를 추적하여 중복 열림 방지
    private var lastProcessedMarkerId: String? = null
    private var lastProcessedTime: Long = 0
    private val EVENT_DEBOUNCE_TIME = 500L // 이벤트 디바운싱 시간 (ms)

    init {
        Timber.d("MemoViewModel 초기화")
        subscribeToMemoEvents()
        
        // 이벤트 구독 관련 코드 (MemoEventHandler로 이동 예정) -----------
        val job = viewModelScope.launch {
            // memoManager의 상태 변화 감지
            try {
                memoManager.shouldShowMemoDialog
                    .combine(memoManager.selectedMarkerId) { shouldShow, markerId ->
                        Pair(shouldShow, markerId)
                    }
                    .collect { (shouldShow, markerId) ->
                        Timber.d("MemoManager 상태 변경 감지: shouldShow=$shouldShow, markerId=$markerId")
                        
                        // 중복 이벤트 방지 로직 추가
                        if (shouldShow && markerId != null) {
                            val currentTime = System.currentTimeMillis()
                            
                            // 동일한 마커에 대한 요청이 짧은 시간 내에 다시 들어온 경우 무시
                            if (markerId == lastProcessedMarkerId && 
                                currentTime - lastProcessedTime < EVENT_DEBOUNCE_TIME) {
                                Timber.d("중복 요청 무시: markerId=$markerId (${currentTime - lastProcessedTime}ms 내)")
                                return@collect
                            }
                            
                            // 요청 처리 시간 업데이트
                            lastProcessedMarkerId = markerId
                            lastProcessedTime = currentTime
                            
                            Timber.d("MemoManager에서 다이얼로그 표시 요청 감지: markerId=$markerId")
                            
                            // 이미 같은 마커에 대해 메모장이 표시 중인지 확인
                            val currentState = _memoDialogState.value
                            if (currentState.isVisible && currentState.markerId == markerId) {
                                Timber.d("이미 다이얼로그 표시 중: markerId=$markerId")
                                return@collect
                            }
                            
                            // UI 상태 업데이트
                            _memoDialogState.value = DialogState(
                                isVisible = true,
                                markerId = markerId
                            )
                            
                            // 필요한 경우 메모 로드
                            loadMemos(markerId)
                        } else if (!shouldShow) {
                            // 메모장 숨김 처리 - markerId는 유지
                            if (_memoDialogState.value.isVisible) {
                                Timber.d("MemoManager에서 다이얼로그 숨김 요청 감지")
                                _memoDialogState.value = _memoDialogState.value.copy(isVisible = false)
                            }
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "MemoManager 상태 변화 구독 중 오류 발생: ${e.message}")
            }
        }
        flowCollectionJobs.add(job)
        // 이벤트 구독 관련 코드 끝 ------------------------------------
        
        // 메모장 상태 모니터링을 위한 코드 구독
        val monitorJob = viewModelScope.launch {
            shouldShowMemoDialog.collect { shouldShow ->
                Timber.d("메모장 표시 상태 변경: $shouldShow, 마커 ID: ${_memoDialogState.value.markerId}")
            }
        }
        flowCollectionJobs.add(monitorJob)
        
        // 초기화 직후 MemoManager에 이 ViewModel 등록 (중요)
        memoManager.cleanupViewModel(this) // 기존 연결 정리
        viewModelScope.launch {
            delay(100) // 약간의 지연 후 재연결
            memoManager.registerViewModel(this@MemoViewModel)
            Timber.d("MemoManager에 ViewModel 등록 완료")
        }
    }

    /**
     * 메모 이벤트 구독 (MemoEventHandler로 이동 예정)
     */
    private fun subscribeToMemoEvents() {
        val job = memoInteractor.subscribeToEvents(viewModelScope) { event ->
            try {
                Timber.d("메모 이벤트 수신: $event")
                when (event) {
                    is MemoEvent.MemosLoaded -> {
                        Timber.d("메모 로드 성공 이벤트: ${event.markerId}, ${event.memos.size}개")
                    }
                    is MemoEvent.MemoCreated -> {
                        Timber.d("메모 생성 성공 이벤트: ${event.memo.id}")
                    }
                    is MemoEvent.MemoDeleted -> {
                        Timber.d("메모 삭제 성공 이벤트: ${event.memoId}")
                    }
                    is MemoEvent.Error -> {
                        val errorMessage = "메모 관련 오류 이벤트: ${event.message}"
                        val exception = Exception(event.message)
                        Timber.e(exception, errorMessage)
                        handleException(exception, event.message)
                    }
                    is MemoEvent.ClearedSelection -> {
                        Timber.d("메모 선택 초기화 이벤트")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "메모 이벤트 처리 중 오류 발생")
                handleException(e, "메모 이벤트 처리 중 오류가 발생했습니다.")
            }
        }
        // 구독 작업을 목록에 추가하여 나중에 정리할 수 있도록 함
        flowCollectionJobs.add(job)
    }

    // 비즈니스 로직 관련 코드 (MemoInteractor로 대체) -----------
    /**
     * 메모 로드 (디바운싱 적용)
     */
    fun loadMemos(markerId: String) {
        // 같은 마커에 대한 반복 요청 디바운싱
        if (markerId == lastLoadedMarkerId && isLoadingMemos) {
            Timber.d("메모 로딩 디바운싱: 이미 '$markerId' 마커에 대한 로드 작업이 진행 중입니다.")
            return
        }
        
        // 이미 진행 중인 작업이 있으면 취소
        loadMemosJob?.cancel()
        
        isLoadingMemos = true
        lastLoadedMarkerId = markerId
        
        loadMemosJob = memoInteractor.loadMemos(
            markerId = markerId,
            scope = viewModelScope,
            onSuccess = { memos, markerIdResult ->
                updateMemoState(memos, markerIdResult)
                isLoadingMemos = false
            },
            onError = { e, operation ->
                handleException(e, operation)
                isLoadingMemos = false
            }
        )
    }

    /**
     * 메모 생성 (MemoInteractor 위임)
     * @param userId 사용자 ID
     * @param markerId 마커 ID
     * @param content 메모 내용
     */
    fun createMemo(userId: String, markerId: String, content: String) {
        memoInteractor.createMemo(
            userId = userId,
            markerId = markerId,
            content = content,
            scope = viewModelScope,
            onError = { e ->
                Timber.e(e, "메모 생성 중 예외 발생: ${e.message}")
                handleException(e, "메모 생성")
            }
        )
    }

    /**
     * 메모 삭제 (MemoInteractor 위임)
     */
    fun deleteMemo(memo: Memo) {
        memoInteractor.deleteMemo(
            memo = memo,
            scope = viewModelScope,
            onError = { e ->
                handleException(e, "메모 삭제")
            }
        )
    }

    /**
     * 메모 삭제 (MemoInteractor 위임)
     */
    fun deleteMemo(memoId: String, markerId: String) {
        memoInteractor.deleteMemo(
            memoId = memoId,
            markerId = markerId,
            scope = viewModelScope,
            onError = { e ->
                handleException(e, "메모 삭제")
            }
        )
    }
    // 비즈니스 로직 관련 코드 끝 ------------------------------------

    // 다이얼로그 상태 관리 관련 코드 (MemoDialogStateHolder로 이동 예정) -----
    /**
     * 메모장 표시 - 지정된 마커의 메모장을 표시합니다.
     * @param markerId 메모장을 표시할 마커 ID
     * @param isTemporary 임시 마커 여부 (기본값: false)
     */
    fun showMemoDialogForMarker(markerId: String, isTemporary: Boolean = false) {
        // 이미 표시 중인지 확인
        if (_memoDialogState.value.isVisible && _memoDialogState.value.markerId == markerId) {
            Timber.d("이미 같은 마커의 메모장이 표시 중입니다: $markerId")
            return
        }
        
        // 중요: 임시 마커의 경우 이전 메모 정보 완전 정리 (섞임 방지)
        viewModelScope.launch {
            try {
                if (isTemporary) {
                    Timber.d("임시 마커 메모장 표시 - 이전 상태 완전 정리: $markerId")
                    
                    // 1. 메모 관리자 상태 초기화
                    memoInteractor.clearMemos()
                    memoInteractor.clearSelectedMarker()
                    
                    // 2. 로컬 메모 상태 초기화
                    _memoState.value = MemoUiState.Initial
                    _memoDialogState.value = DialogState(isVisible = false, markerId = null, isTemporary = false)
                    
                    // 3. 상태 정리 완료 대기
                    delay(100)
                }
                
                // 임시 마커 상태 설정
                _memoDialogState.value = DialogState(
                    isVisible = true,
                    markerId = markerId,
                    isTemporary = isTemporary
                )
                
                // MemoManager의 showMemoDialog 함수를 호출
                memoManager.showMemoDialog(markerId)
                
                // 메모 로드 (임시 마커는 빈 상태로 시작)
                loadMemos(markerId)
                
                Timber.d("메모장 표시 요청 완료: markerId=$markerId, isTemporary=$isTemporary")
                
            } catch (e: Exception) {
                Timber.e(e, "메모장 표시 중 오류 발생: markerId=$markerId")
                // 오류 발생 시 상태 초기화
                _memoDialogState.value = DialogState()
            }
        }
    }

    /**
     * 임시 마커용 메모장 표시 - 임시 마커의 메모장을 표시합니다.
     * @param markerId 메모장을 표시할 마커 ID
     */
    fun showMemoDialogForTemporaryMarker(markerId: String) {
        showMemoDialogForMarker(markerId, true)
    }

    /**
     * 메모장 표시 완료 처리 - 사용자가 메모장을 확인했을 때 호출됩니다.
     */
    fun onMemoDialogShown() {
        // 메모장 상태 유지 (isVisible를 true로 유지)
        val currentMarkerId = _memoDialogState.value.markerId
        Timber.d("메모장 표시 완료 처리: shouldShow=true, markerId=$currentMarkerId")
    }

    /**
     * 메모장 닫기 - 사용자가 메모장을 닫을 때 호출됩니다.
     * @param resetState 상태를 완전히 초기화할지 여부 (기본값: false)
     */
    fun closeMemoDialog(resetState: Boolean = false) {
        val oldMarkerId = _memoDialogState.value.markerId
        val isTemporary = _memoDialogState.value.isTemporary
        
        Timber.d("메모장 닫힘 처리: 마커 ID=$oldMarkerId, 임시 마커=$isTemporary, 상태 초기화=$resetState")
        
        viewModelScope.launch {
            // 상태 변경 전에 짧은 지연 추가
            delay(100)
            
            if (resetState) {
                // 상태 완전 초기화
                _memoDialogState.value = DialogState(
                    isVisible = false,
                    markerId = null,
                    isTemporary = false
                )
            } else {
                // shouldShow만 false로 변경 (markerId는 유지)
                _memoDialogState.value = _memoDialogState.value.copy(isVisible = false)
            }
            
            // 완전히 닫힌 후 상태 정리를 위한 지연
            delay(200)
            
            // MemoManager에 다이얼로그 닫힘 알림
            memoManager.onMemoDialogClosed()
            
            Timber.d("메모장 닫힘 처리 완료: 마커 ID=$oldMarkerId")
        }
    }

    /**
     * 메모장 상태 초기화
     */
    fun resetMemoDialogState() {
        _memoDialogState.value = _memoDialogState.value.copy(isVisible = false)
    }
    
    /**
     * 현재 마커가 임시 마커인지 확인합니다.
     */
    fun isCurrentMarkerTemporary(): Boolean {
        return _memoDialogState.value.isTemporary
    }
    // 다이얼로그 상태 관리 관련 코드 끝 ------------------------------------

    // 공통 유틸리티 코드 -----------------------------------------------
    private fun updateMemoState(memos: List<Memo>, currentMarkerId: String) {
        // 메모를 최신순으로 정렬하고 표시할 갯수 제한
        val sortedMemos = memos.sortedByDescending { it.modifiedAt }
        
        Timber.d("메모 상태 업데이트: 총 ${memos.size}개의 메모, 정렬 후 적용")
        
        _memoState.value = MemoUiState.Success(
            memos = sortedMemos,
            currentMarkerId = currentMarkerId
        )
        
        // 메모 갯수가 제한에 가까워지면 로그 남기기
        if (memos.size >= MemoConstants.MAX_MEMO_COUNT - 2) {
            Timber.w("메모 갯수 경고: 현재 ${memos.size}개, 최대 ${MemoConstants.MAX_MEMO_COUNT}개")
        }
    }

    private fun handleException(e: Throwable, operation: String) {
        if (e is CancellationException) {
            Timber.d("$operation 작업 취소됨")
            return
        }
        Timber.e(e, "$operation 중 오류 발생")
        _memoState.value = MemoUiState.Error("${operation}에 실패했습니다")
    }

    fun clearError() {
        _memoState.value = MemoUiState.Initial
    }

    override fun onCleared() {
        try {
            Timber.d("MemoViewModel onCleared() 시작")
            
            // 모든 리소스 정리
            performCleanup()
            
            Timber.d("MemoViewModel onCleared() 완료")
        } catch (e: Exception) {
            Timber.e(e, "MemoViewModel onCleared() 중 오류 발생")
        } finally {
            super.onCleared()
        }
    }

    fun cleanup() {
        Timber.d("MemoViewModel cleanup 시작")
        try {
            // 코루틴 작업 취소 및 Flow 구독 정리
            flowCollectionJobs.forEach { job ->
                if (job.isActive) {
                    job.cancel()
                    Timber.d("Flow 구독 Job 취소됨")
                }
            }
            flowCollectionJobs.clear()
            
            // 코루틴 작업 취소
            viewModelScope.coroutineContext.cancelChildren()
            
            // 로드 작업 취소
            loadMemosJob?.cancel()
            loadMemosJob = null
            
            // 상태 초기화
            lastLoadedMarkerId = null
            isLoadingMemos = false
            
            Timber.d("MemoViewModel cleanup 완료")
        } catch (e: Exception) {
            Timber.e(e, "MemoViewModel cleanup 중 오류 발생")
        }
    }
    
    /**
     * 모든 리소스를 정리하는 공통 메서드
     */
    private fun performCleanup() {
        // 1. 진행 중인 작업 취소
        loadMemosJob?.cancel()
        loadMemosJob = null
        
        // 2. Flow 구독 취소
        flowCollectionJobs.forEach { job ->
            job.cancel()
        }
        flowCollectionJobs.clear()
        
        // 3. 코루틴 스코프 정리
        viewModelScope.coroutineContext.cancelChildren()
        
        // 4. Flow 상태 초기화
        try {
            viewModelScope.launch {
                _memoState.emit(MemoUiState.Initial)
                _memoDialogState.emit(DialogState(isVisible = false, markerId = null, isTemporary = false))
            }
        } catch (e: Exception) {
            Timber.e(e, "Flow 초기화 중 오류 발생")
        }
        
        // 5. 상태 초기화
        _memoState.value = MemoUiState.Initial
        _memoDialogState.value = DialogState(isVisible = false, markerId = null, isTemporary = false)
        
        // 6. 마지막 로드된 마커 ID 초기화
        lastLoadedMarkerId = null
        isLoadingMemos = false
        
        // 7. MemoManager에서 이 ViewModel 관련 리소스 정리 요청
        memoManager.cleanupViewModel(this)
    }

    /**
     * EditModeManager 인스턴스를 반환합니다.
     */
    fun getEditModeManager(): EditModeManager {
        return memoInteractor.getEditModeManager()
    }

    /**
     * ViewModel 상태 초기화 - 테스트용
     */
    fun resetForTesting() {
        _memoDialogState.value = DialogState(isVisible = false, markerId = null, isTemporary = false)
        // ... existing code ...
    }
    
    // suspend 함수 테스트를 위한 함수
    suspend fun resetMemoDialogStateForTesting() {
        _memoDialogState.emit(DialogState())
        // ... existing code ...
    }
} 