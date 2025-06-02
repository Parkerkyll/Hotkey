package com.parker.hotkey.domain.manager.impl

import android.view.View
import com.google.android.material.snackbar.Snackbar
import com.parker.hotkey.R
import com.parker.hotkey.data.remote.sync.util.RetryUtil
import com.parker.hotkey.domain.constants.CoroutineConstants
import com.parker.hotkey.domain.constants.MemoConstants
import com.parker.hotkey.domain.manager.BaseManager
import com.parker.hotkey.domain.manager.EditModeEvent
import com.parker.hotkey.domain.manager.EditModeManager
import com.parker.hotkey.domain.manager.MemoEvent
import com.parker.hotkey.domain.manager.MemoManager
import com.parker.hotkey.domain.model.Memo
import com.parker.hotkey.domain.model.state.DialogState
import com.parker.hotkey.domain.model.state.MemoState
import com.parker.hotkey.domain.repository.AuthRepository
import com.parker.hotkey.domain.repository.MarkerRepository
import com.parker.hotkey.domain.repository.MemoRepository
import com.parker.hotkey.domain.usecase.memo.CreateMemoUseCase
import com.parker.hotkey.domain.usecase.memo.DeleteMemoUseCase
import com.parker.hotkey.domain.usecase.marker.DeleteMarkerUseCase
import com.parker.hotkey.domain.usecase.marker.DeleteMarkerWithValidationUseCase
import com.parker.hotkey.domain.usecase.UploadChangesUseCase
import com.parker.hotkey.domain.util.JobManager
import com.parker.hotkey.domain.util.StateLogger
import com.parker.hotkey.domain.util.StateUpdateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 메모 관리 기능을 제공하는 구현 클래스
 * 
 * 리팩토링 노트:
 * - Phase 2 리팩토링으로 모든 상태를 domain.model.state.MemoState로 통합
 * - 기존 Flow와의 호환성을 위해 매핑 StateFlow로 제공
 * - StateUpdateHelper를 사용하여 상태 업데이트 일관성 유지
 * - Phase 5 리팩토링으로 성능 최적화 적용 (디바운싱, 배치 업데이트)
 */
@Singleton
class MemoManagerImpl @Inject constructor(
    private val memoRepository: MemoRepository,
    private val markerRepository: MarkerRepository,
    private val authRepository: AuthRepository,
    private val createMemoUseCase: CreateMemoUseCase,
    private val deleteMemoUseCase: DeleteMemoUseCase,
    private val deleteMarkerUseCase: DeleteMarkerUseCase,
    private val deleteMarkerWithValidationUseCase: DeleteMarkerWithValidationUseCase,
    private val editModeManager: EditModeManager,
    private val uploadChangesUseCase: UploadChangesUseCase,
    coroutineScope: CoroutineScope
) : BaseManager<MemoEvent>(coroutineScope), MemoManager {

    private val TAG = "MemoManagerImpl"
    private val stateLogger = StateLogger(TAG)

    // 통합된 내부 상태 설정 (com.parker.hotkey.domain.model.state.MemoState 사용)
    private val _state = MutableStateFlow(com.parker.hotkey.domain.model.state.MemoState())
    
    // 인터페이스 호환성을 위한 상태 변환
    override val state: StateFlow<com.parker.hotkey.domain.manager.MemoState> = _state.map { 
        com.parker.hotkey.domain.manager.MemoState(
            memos = it.memos,
            selectedMarkerId = it.selectedId,
            isLoading = it.isLoading,
            error = it.error
        )
    }.stateIn(
        coroutineScope,
        SharingStarted.Eagerly,
        com.parker.hotkey.domain.manager.MemoState()
    )
    
    // StateUpdateHelper 초기화
    private val stateUpdateHelper = StateUpdateHelper(
        stateFlow = _state,
        errorHandler = { state, error, isLoading ->
            state.copy(error = error, isLoading = isLoading)
        },
        coroutineScope = coroutineScope
    )
    
    // 상태에서 파생된 StateFlow - 기존 코드와의 호환성 유지
    override val memos: StateFlow<List<Memo>> = _state.map { it.memos }.stateIn(
        coroutineScope,
        SharingStarted.Eagerly,
        emptyList()
    )
    
    override val shouldShowMemoDialog: StateFlow<Boolean> = _state.map { it.dialogState.isVisible }.stateIn(
        coroutineScope,
        SharingStarted.Eagerly,
        false
    )
    
    override val selectedMarkerId: StateFlow<String?> = _state.map { it.selectedId }.stateIn(
        coroutineScope,
        SharingStarted.Eagerly,
        null
    )
    
    // 편집 모드 상태를 EditModeManager에서 추출
    override val editModeState: StateFlow<Boolean> = editModeManager.state.map { it.isEditMode }.stateIn(
        coroutineScope,
        SharingStarted.Eagerly,
        false
    )
    
    // 작업 관리 최적화를 위한 맵
    private val jobs = ConcurrentHashMap<String, Job>()
    
    // 메모 ID별 마지막 업데이트 시간 추적 (동일 데이터의 불필요한 중복 업데이트 방지)
    private val lastMemoUpdateTimes = ConcurrentHashMap<String, Long>()
    
    // 디바운싱 도구들
    private val memoLoadingDebouncer = JobManager<String>()
    private val memoUpdateDebouncer = JobManager<String>()
    
    // 대기 중인 메모 업데이트
    private val pendingMemoUpdates = ConcurrentHashMap<String, Memo>()
    
    /**
     * 대기 중인 메모 변경사항을 커밋합니다.
     */
    private suspend fun commitMemoPendingChanges() {
        if (pendingMemoUpdates.isEmpty()) return
        
        stateLogger.logDebug("대기 중인 메모 변경사항 커밋 (총 ${pendingMemoUpdates.size}개)")
        
        // 배치 모드 시작
        stateUpdateHelper.startBatchUpdate()
        
        try {
            // 모든 대기 업데이트를 처리
            val updates = HashMap(pendingMemoUpdates)
            pendingMemoUpdates.clear()
            
            // DB 저장
            updates.values.forEach { memo ->
                try {
                    val result = memoRepository.update(memo)
                    Timber.d("메모 업데이트 저장 결과: $result (ID: ${memo.id})")
                } catch (e: Exception) {
                    Timber.e(e, "메모 저장 중 오류 발생: ${memo.id}")
                }
            }
            
            // 상태 일괄 업데이트
            val currentMemos = _state.value.memos.toMutableList()
            var changed = false
            
            updates.values.forEach { memo ->
                val index = currentMemos.indexOfFirst { it.id == memo.id }
                if (index >= 0) {
                    currentMemos[index] = memo
                    changed = true
                } else {
                    currentMemos.add(memo)
                    changed = true
                }
            }
            
            // 상태 업데이트
            if (changed) {
                stateUpdateHelper.updateState(TAG) { state ->
                    state.copy(memos = currentMemos)
                }
            }
        } finally {
            // 배치 모드 종료 및 업데이트 적용
            stateUpdateHelper.finishBatchUpdate(TAG)
        }
        
        // 변경사항 업로드 (백그라운드)
        uploadChangesInBackground()
    }
    
    /**
     * 백그라운드에서 변경사항을 업로드합니다.
     */
    private fun uploadChangesInBackground() {
        // 기존 업로드 작업 취소
        val jobKey = "upload_changes"
        jobs[jobKey]?.cancel()
        
        jobs[jobKey] = coroutineScope.launch(Dispatchers.IO) {
            try {
                delay(1000) // 추가 변경사항이 있을 경우 대기
                if (!isActive) return@launch
                
                Timber.d("변경사항 업로드 시작")
                // 현재 상태의 메모들에 대해 업로드 수행
                val currentMemos = _state.value.memos
                var uploadedCount = 0
                
                currentMemos.forEach { memo ->
                    try {
                        // 마지막 동기화 시간 확인 - 최근에 수정된 메모만 업로드
                        if (memo.lastSync.status != com.parker.hotkey.domain.model.LastSync.SyncStatus.SUCCESS) {
                            val result = uploadChangesUseCase.uploadMemo(memo)
                            if (result != null) {
                                uploadedCount++
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "메모 ${memo.id} 업로드 중 오류 발생")
                    }
                }
                
                Timber.d("변경사항 업로드 완료: 총 ${uploadedCount}개 업로드됨")
            } catch (e: Exception) {
                Timber.e(e, "변경사항 업로드 중 오류 발생")
            } finally {
                jobs.remove(jobKey)
            }
        }
    }
    
    /**
     * 초기화 로직
     */
    override fun initialize() {
        // 공통 초기화 로직 사용
        initializeCommon("MemoManager") {
            // 이벤트 구독
            subscribeToEvents(coroutineScope) { _ ->
                // 이벤트 처리 로직이 필요하면 여기에 추가
            }
        }
    }
    
    /**
     * 리소스 정리
     */
    override suspend fun cleanup() {
        super.cleanup()
        
        // 작업 정리
        jobs.forEach { (_, job) -> job.cancel() }
        jobs.clear()
        
        // 디바운서 정리
        memoLoadingDebouncer.cancelAll()
        memoUpdateDebouncer.cancelAll()
        
        // StateUpdateHelper 정리
        stateUpdateHelper.cleanup()
        
        Timber.d("MemoManager 정리 완료")
    }

    override suspend fun getMemoCountForMarker(markerId: String): Int {
        return try {
            val memos = memoRepository.getMemosByMarkerId(markerId).first()
            Timber.d("마커($markerId)의 메모 개수: ${memos.size}")
            memos.size
        } catch (e: Exception) {
            Timber.e(e, "메모 개수 조회 실패: 마커ID=$markerId")
            handleError(e, "메모 개수 조회 실패", emitErrorEvent = true)
            0
        }
    }
    
    /**
     * 지정된 마커 ID에 해당하는 메모들을 로드합니다.
     * 디바운싱 적용을 위해 내부에서 loadMemosByMarkerIdInternal을 호출합니다.
     */
    override fun loadMemosByMarkerId(markerId: String) {
        if (markerId.isEmpty()) {
            Timber.w("마커 ID가 비어있어 메모 로딩을 건너뜁니다.")
            return
        }
        
        Timber.d("마커 ID $markerId 대한 메모 로드 요청 (디바운싱 적용)")
        memoLoadingDebouncer.launch(
            scope = coroutineScope,
            key = markerId,
            debounceTime = 200L
        ) {
            loadMemosByMarkerIdInternal(markerId)
        }
    }
    
    /**
     * 메모 로딩 내부 구현 (디바운싱 후 실제 호출)
     */
    private fun loadMemosByMarkerIdInternal(markerId: String) {
        // 기존 로딩 작업 취소
        val jobKey = "load_memos_$markerId"
        jobs[jobKey]?.cancel()
        
        jobs[jobKey] = coroutineScope.launch(Dispatchers.IO) {
            try {
                Timber.d("마커($markerId)의 메모 로드 시작")
                
                // 로딩 상태 설정
                stateUpdateHelper.setLoading(TAG, true)
                
                // 먼저 이전 메모 목록 초기화 (문제 해결을 위한 핵심 수정)
                stateUpdateHelper.updateState(TAG) { 
                    it.copy(
                        memos = emptyList(),
                        selectedId = markerId
                    ) 
                }
                
                // 메모 조회
                val memos = memoRepository.getMemosByMarkerId(markerId).first()
                
                // 상태 업데이트
                stateUpdateHelper.updateState(TAG) { 
                    it.copy(
                        memos = memos,
                        selectedId = markerId,
                        error = null
                    ) 
                }
                
                // 이벤트 발행
                bufferOrEmitEvent(MemoEvent.MemosLoaded(markerId, memos))
                
                Timber.d("마커($markerId)의 메모 로드 성공: ${memos.size}개")
            } catch (e: Exception) {
                // 작업 취소 예외는 오류로 처리하지 않음
                if (e is kotlinx.coroutines.CancellationException) {
                    Timber.d("마커($markerId)의 메모 로드 작업이 취소됨")
                    return@launch
                }
                
                Timber.e(e, "메모 로드 실패: 마커ID=$markerId")
                
                // 에러 상태 설정
                stateUpdateHelper.setError(TAG, e.message)
                
                // 에러 이벤트 발행
                handleError(e, "메모 로드 실패", emitErrorEvent = true)
            } finally {
                // 작업이 취소되지 않은 경우만 로딩 상태 해제
                if (isActive) {
                    stateUpdateHelper.setLoading(TAG, false)
                }
                
                // 작업 참조 제거
                jobs.remove(jobKey)
            }
        }
    }
    
    /**
     * 메모를 생성합니다.
     */
    override fun createMemo(userId: String, markerId: String, content: String) {
        // 기존 생성 작업 취소
        val jobKey = "create_memo_$markerId"
        jobs[jobKey]?.cancel()
        
        jobs[jobKey] = coroutineScope.launch(Dispatchers.IO) {
            try {
                Timber.d("메모 생성 시작: 사용자ID=$userId, 마커ID=$markerId, 내용=${content.take(10)}...")
                
                // 마커 존재 여부 확인 로그 (캐시 조회로 최적화)
                val marker = markerRepository.getById(markerId)
                Timber.d("마커 존재 여부 확인: ${marker != null}, 마커ID=$markerId")
                
                // 마커가 존재하지 않으면 예외 발생
                if (marker == null) {
                    val exception = Exception("마커를 찾을 수 없습니다: $markerId")
                    handleError(exception, "마커가 존재하지 않아 메모를 저장할 수 없습니다", emitErrorEvent = true)
                    throw exception
                }
                
                // 마커의 메모 갯수 확인
                val existingMemos = memoRepository.getMemosByMarkerId(markerId).first()
                if (existingMemos.size >= MemoConstants.MAX_MEMO_COUNT) {
                    Timber.w("메모 제한 초과: 마커ID=$markerId, 현재 메모 수=${existingMemos.size}, 최대=${MemoConstants.MAX_MEMO_COUNT}")
                    
                    // 에러 상태 설정
                    stateUpdateHelper.setError(TAG, MemoConstants.MEMO_LIMIT_EXCEEDED_MESSAGE)
                    
                    // 에러 이벤트 발행
                    val exception = Exception(MemoConstants.MEMO_LIMIT_EXCEEDED_MESSAGE)
                    handleError(exception, "메모 제한 초과", emitErrorEvent = true)
                    
                    return@launch
                }
                
                // 로딩 상태 설정
                stateUpdateHelper.setLoading(TAG, true)
                
                // 배치 업데이트 시작
                stateUpdateHelper.startBatchUpdate()
                
                try {
                    Timber.d("createMemoUseCase 호출 시작")
                    val result = createMemoUseCase(userId, markerId, content)
                    Timber.d("createMemoUseCase 호출 완료: 성공=${result.isSuccess}")
                    
                    result.onSuccess { memo ->
                        Timber.d("메모 생성 성공: ID=${memo.id}")
                        
                        // 상태 업데이트
                        stateUpdateHelper.updateState(TAG) { currentState ->
                            currentState.copy(
                                memos = currentState.memos + memo,
                                error = null
                            )
                        }
                        Timber.d("메모 상태 업데이트 완료")
                        
                        // 서버에 메모 업로드 - 백그라운드로 처리하여 UI 응답성 개선
                        uploadMemoAsync(memo)
                        
                        // 이벤트 발행 (로컬 저장 성공 시 즉시 발행)
                        bufferOrEmitEvent(MemoEvent.MemoCreated(memo))
                        
                        // 저장 검증 예약 (비동기)
                        scheduleValidation(memo.id)
                    }.onFailure { e ->
                        // 오류 처리
                        Timber.e(e, "메모 생성 실패: ${e.message}")
                        stateUpdateHelper.updateState(TAG) { currentState ->
                            currentState.copy(
                                error = e.message
                            )
                        }
                        
                        // 에러 이벤트 발행
                        handleError(e, "메모 생성 실패", emitErrorEvent = true)
                    }
                } finally {
                    // 배치 업데이트 종료 및 상태 적용
                    stateUpdateHelper.finishBatchUpdate(TAG)
                }
            } catch (e: Exception) {
                Timber.e(e, "메모 생성 중 예외 발생: ${e.message}")
                stateUpdateHelper.setError(TAG, e.message)
                handleError(e, "메모 생성 중 예외 발생", emitErrorEvent = true)
            } finally {
                stateUpdateHelper.setLoading(TAG, false)
                jobs.remove(jobKey)
            }
        }
    }
    
    /**
     * 메모를 비동기적으로 서버에 업로드합니다.
     * 관련 마커가 서버에 먼저 업로드되도록 보장합니다.
     */
    private fun uploadMemoAsync(memo: Memo) {
        val jobKey = "upload_memo_${memo.id}"
        jobs[jobKey]?.cancel()
        
        jobs[jobKey] = coroutineScope.launch(Dispatchers.IO) {
            try {
                // 다른 작업과 충돌 방지를 위한 지연
                delay(300)
                if (!isActive) return@launch
                
                Timber.d("메모 서버 업로드 시작: ID=${memo.id}")
                
                // 관련 마커 가져오기 (옵션)
                val relatedMarker = try {
                    Timber.d("연관된 마커 조회: markerId=${memo.markerId}")
                    markerRepository.getById(memo.markerId)
                } catch (e: Exception) {
                    Timber.e(e, "연관 마커 조회 중 오류: ${memo.markerId}")
                    null
                }
                
                // 마커가 서버에 있는지 확인하는 로직을 포함한 업로드 메서드 호출
                val updatedMemo = uploadChangesUseCase.uploadMemo(memo, relatedMarker)
                
                if (updatedMemo != null) {
                    // 서버에서 업데이트된 메모로 로컬 DB 갱신
                    Timber.d("메모 서버 업로드 성공, DB 업데이트 시작: ID=${memo.id}")
                    memoRepository.update(updatedMemo)
                    Timber.d("메모 서버 업로드 성공, DB 업데이트 완료: ID=${memo.id}")
                    
                    // 상태 업데이트 - 업로드 성공 상태 반영
                    stateUpdateHelper.updateStateDebounced(TAG, CoroutineConstants.UI_DEBOUNCE_TIME) { currentState ->
                        val updatedMemos = currentState.memos.map { 
                            if (it.id == updatedMemo.id) updatedMemo else it 
                        }
                        currentState.copy(memos = updatedMemos)
                    }
                } else {
                    Timber.w("메모 서버 업로드 실패, 로컬에만 저장됨: ID=${memo.id}")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    Timber.d("메모 업로드 작업 취소됨: ID=${memo.id}")
                    return@launch
                }
                
                Timber.e(e, "메모 서버 업로드 중 오류 발생: ID=${memo.id}")
            } finally {
                jobs.remove(jobKey)
            }
        }
    }
    
    /**
     * 메모 저장 검증을 비동기로 예약합니다.
     */
    private fun scheduleValidation(memoId: String) {
        val jobKey = "validate_memo_$memoId"
        jobs[jobKey]?.cancel()
        
        jobs[jobKey] = coroutineScope.launch(Dispatchers.IO) {
            try {
                // 저장 지연을 고려한 대기 시간
                delay(500)
                if (!isActive) return@launch
                
                validateMemoSaved(memoId)
            } catch (e: Exception) {
                Timber.e(e, "메모 저장 검증 중 오류 발생: ID=$memoId")
            } finally {
                jobs.remove(jobKey)
            }
        }
    }
    
    /**
     * 메모가 데이터베이스에 저장되었는지 검증합니다.
     */
    private suspend fun validateMemoSaved(memoId: String) {
        try {
            Timber.d("메모 저장 검증 시작: ID=$memoId")
            
            // RetryUtil 사용으로 변경
            val savedMemo = RetryUtil.retry(
                maxRetries = 2,
                initialDelayMillis = 200
            ) { attempt ->
                val memo = memoRepository.getById(memoId)
                if (memo != null) {
                    Timber.d("메모 저장 검증 성공 (시도 ${attempt+1}): ID=$memoId")
                }
                memo
            }
            
            if (savedMemo != null) {
                Timber.d("메모 저장 검증 성공: ID=$memoId")
            } else {
                Timber.e("메모 저장 최종 검증 실패: ID=$memoId")
                stateUpdateHelper.updateState(TAG) { currentState ->
                    currentState.copy(
                        error = "메모 저장에 문제가 발생했습니다. 다시 시도해주세요."
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "메모 저장 검증 중 오류 발생: ID=$memoId")
        }
    }
    
    override fun deleteMemo(memoId: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                Timber.d("메모 삭제 시작: ID=$memoId")
                
                // 현재 편집 모드 확인
                if (!editModeManager.getCurrentMode()) {
                    Timber.w("메모 삭제 실패: 편집 모드가 아님")
                    stateUpdateHelper.setError(TAG, "편집 모드에서만 메모를 삭제할 수 있습니다.")
                    return@launch
                }
                
                // 로딩 상태 설정
                stateUpdateHelper.setLoading(TAG, true)
                
                // 현재 상태에서 해당 메모의 markerId 찾기
                val memo = _state.value.memos.find { it.id == memoId }
                val markerId = memo?.markerId ?: run {
                    // 상태에서 찾을 수 없는 경우 DB에서 직접 조회 시도
                    try {
                        val dbMemo = memoRepository.getById(memoId)
                        dbMemo?.markerId
                    } catch (e: Exception) {
                        Timber.e(e, "DB에서 메모 조회 실패: $memoId")
                        null
                    }
                }
                
                if (markerId == null) {
                    Timber.w("메모의 마커 ID를 찾을 수 없음: memoId=$memoId")
                    stateUpdateHelper.setError(TAG, "메모 정보를 찾을 수 없습니다.")
                    return@launch
                }
                
                Timber.d("메모 삭제: memoId=$memoId, markerId=$markerId")
                val result = deleteMemoUseCase(memoId, markerId)
                result.onSuccess {
                    // 상태 업데이트
                    stateUpdateHelper.updateState(TAG) { currentState ->
                        currentState.copy(
                            memos = currentState.memos.filter { it.id != memoId },
                            error = null
                        )
                    }
                    
                    // 이벤트 발행
                    bufferOrEmitEvent(MemoEvent.MemoDeleted(memoId))
                    
                    Timber.d("메모 삭제 성공: ID=$memoId")
                }.onFailure { e ->
                    // 에러 처리
                    stateUpdateHelper.setError(TAG, e.message)
                    handleError(e, "메모 삭제 실패", emitErrorEvent = true)
                    Timber.e(e, "메모 삭제 실패: ID=$memoId, 오류=${e.message}")
                }
            } catch (e: Exception) {
                // 에러 처리
                stateUpdateHelper.setError(TAG, e.message)
                handleError(e, "메모 삭제 실패", emitErrorEvent = true)
                Timber.e(e, "메모 삭제 중 예외 발생: ID=$memoId")
            } finally {
                stateUpdateHelper.setLoading(TAG, false)
            }
        }
    }
    
    override fun clearSelectedMarker() {
        stateUpdateHelper.updateState(TAG) { state ->
            state.copy(
                selectedId = null
            )
        }
        
        coroutineScope.launch {
            bufferOrEmitEvent(MemoEvent.ClearedSelection)
        }
        
        Timber.d("선택된 마커 초기화 완료")
    }
    
    override fun validateEditMode(view: View, action: () -> Unit) {
        editModeManager.validateEditMode(view, action)
    }
    
    override fun restartEditModeTimer() {
        editModeManager.restartEditModeTimer()
    }
    
    /**
     * 현재 모드 반환
     */
    override fun getCurrentMode(): Boolean {
        return editModeManager.getCurrentMode()
    }
    
    /**
     * 현재 편집 모드 타이머 남은 시간 반환 (ms)
     */
    override fun getRemainingTimeMs(): Long {
        return editModeManager.getRemainingTimeMs()
    }
    
    /**
     * 편집 모드 설정
     */
    override fun setEditMode(enabled: Boolean) {
        editModeManager.setEditMode(enabled)
    }
    
    override fun toggleEditMode() {
        editModeManager.toggleEditMode()
    }
    
    override fun deleteMarker(markerId: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                Timber.d("MemoManagerImpl: 마커 삭제 시작 - ID=$markerId")
                stateUpdateHelper.setLoading(TAG, true)
                
                deleteMarkerWithValidationUseCase(markerId)
                    .onSuccess {
                        // 메모 목록 초기화
                        stateUpdateHelper.updateState(TAG) { currentState -> 
                            currentState.copy(
                                memos = emptyList(),
                                selectedId = null,
                                dialogState = currentState.dialogState.copy(
                                    isVisible = false,
                                    markerId = null
                                )
                            ) 
                        }
                        
                        Timber.d("MemoManagerImpl: 마커 삭제 성공 - ID=$markerId")
                    }
                    .onFailure { e ->
                        // 마커가 존재하지 않는 경우 정상 처리로 간주
                        if (e.message?.contains("마커가 존재하지 않습니다") == true) {
                            Timber.w("MemoManagerImpl: 마커가 이미 삭제되었거나 존재하지 않음 - ID=$markerId")
                            
                            // 메모 목록 초기화 (성공 시와 동일하게 처리)
                            stateUpdateHelper.updateState(TAG) { currentState -> 
                                currentState.copy(
                                    memos = emptyList(),
                                    selectedId = null,
                                    dialogState = currentState.dialogState.copy(
                                        isVisible = false,
                                        markerId = null
                                    )
                                ) 
                            }
                        } else {
                            // 그 외 에러 처리
                            handleError(e as Exception, "마커 삭제 실패: ID=$markerId", emitErrorEvent = true)
                        }
                    }
            } catch (e: Exception) {
                // 에러 처리
                handleError(e, "마커 삭제 중 예외 발생: ID=$markerId", emitErrorEvent = true)
            } finally {
                stateUpdateHelper.setLoading(TAG, false)
            }
        }
    }
    
    /**
     * 오류 이벤트 변환 구현
     */
    override fun createErrorEvent(throwable: Throwable, message: String): MemoEvent {
        return MemoEvent.Error("$message: ${throwable.message}")
    }

    /**
     * 메모 다이얼로그 표시
     * 
     * @param markerId 메모 다이얼로그를 표시할 마커 ID
     */
    override fun showMemoDialog(markerId: String) {
        // 새로운 마커의 다이얼로그를 표시하기 전에 이전 상태 완전 초기화
        val currentSelectedId = _state.value.selectedId
        
        if (currentSelectedId != markerId) {
            Timber.d("새로운 마커로 변경됨: $currentSelectedId -> $markerId")
            
            // 이전 마커의 메모 데이터 완전 초기화
            stateUpdateHelper.updateState(TAG) { currentState ->
                currentState.copy(
                    memos = emptyList(),  // 이전 메모 목록 완전 삭제
                    selectedId = null,    // 선택된 마커 ID 초기화
                    dialogState = DialogState(  // 다이얼로그 상태 초기화
                        isVisible = false,
                        markerId = null,
                        isTemporary = false
                    )
                )
            }
            
            Timber.d("이전 마커 상태 초기화 완료: $currentSelectedId")
        }
        
        // 새로운 마커의 다이얼로그 상태 설정
        stateUpdateHelper.updateState(TAG) { currentState ->
            currentState.copy(
                selectedId = markerId,
                dialogState = currentState.dialogState.copy(
                    isVisible = true,
                    markerId = markerId
                )
            )
        }
        
        // 새 마커의 메모 로드 요청
        loadMemosByMarkerId(markerId)
        
        Timber.d("메모 다이얼로그 표시 요청 완료: markerId=$markerId")
    }
    
    /**
     * 메모 다이얼로그 숨김
     */
    override fun hideMemoDialog() {
        stateUpdateHelper.updateState(TAG) { currentState ->
            currentState.copy(
                dialogState = currentState.dialogState.copy(
                    isVisible = false
                )
            )
        }
        Timber.d("메모 다이얼로그 숨김 처리 완료")
    }
    
    /**
     * 메모 다이얼로그 표시 완료 처리
     */
    override fun onMemoDialogShown() {
        stateUpdateHelper.updateState(TAG) { state ->
            val dialogState = state.dialogState.copy(isVisible = true)
            state.copy(dialogState = dialogState)
        }
        Timber.d("메모 다이얼로그 표시 완료 처리")
    }
    
    /**
     * 메모 다이얼로그 닫힘 처리
     */
    override fun onMemoDialogClosed() {
        stateUpdateHelper.updateStateDebounced(TAG) { state ->
            val dialogState = state.dialogState.copy(isVisible = false)
            // 다이얼로그 닫힘 후 선택된 마커 ID 초기화 방지를 위해 selectedId는 변경하지 않음
            state.copy(dialogState = dialogState)
        }
        Timber.d("메모 다이얼로그 닫힘 처리 완료")
        
        // 닫힌 후 작업이 필요한 경우 여기에 추가
        coroutineScope.launch {
            delay(300) // 약간의 지연 후 처리
            // 추가 작업 수행...
        }
    }

    /**
     * 모든 메모 상태 초기화
     */
    override fun clearMemos() {
        // 명시적으로 selectedId를 null로 설정하여 메모 목록 초기화
        stateUpdateHelper.updateState(TAG) { state ->
            state.copy(
                memos = emptyList(),
                selectedId = null,
                dialogState = state.dialogState.copy(
                    isVisible = false,
                    markerId = null
                )
            )
        }
        Timber.d("모든 메모 상태 초기화 완료")
    }

    /**
     * 메모 콘텐츠를 업데이트합니다. (디바운싱 적용)
     * 실제 저장은 commitMemoPendingChanges()에서 일괄 처리됩니다.
     */
    override fun updateMemoContent(memoId: String, content: String) {
        Timber.d("메모 콘텐츠 업데이트 요청: $memoId (디바운싱 적용)")
        
        // 메모 객체 찾기
        val memo = _state.value.memos.find { it.id == memoId } ?: run {
            Timber.w("ID $memoId 해당하는 메모를 찾을 수 없습니다.")
            return
        }
        
        // 변경된 내용이 없다면 업데이트하지 않음
        if (memo.content == content) {
            Timber.d("변경된 내용이 없어 업데이트를 건너뜁니다: $memoId")
            return
        }
        
        // 콘텐츠 변경 및 수정 시간 업데이트된 새 메모 객체 생성
        val updatedMemo = memo.copy(
            content = content,
            modifiedAt = System.currentTimeMillis(),
            lastSync = com.parker.hotkey.domain.model.LastSync(
                status = com.parker.hotkey.domain.model.LastSync.SyncStatus.NONE,
                timestamp = System.currentTimeMillis()
            )
        )
        
        // 대기 중인 업데이트에 추가
        pendingMemoUpdates[memoId] = updatedMemo
        
        // UI 즉시 업데이트 (상태 변경)
        stateUpdateHelper.updateState(TAG) { state ->
            val memos = state.memos.toMutableList()
            val index = memos.indexOfFirst { it.id == memoId }
            if (index >= 0) {
                memos[index] = updatedMemo
            }
            state.copy(memos = memos)
        }
        
        // 디바운스된 저장 요청
        memoUpdateDebouncer.launch(
            scope = coroutineScope,
            key = "memo_update",
            debounceTime = 300L
        ) {
            commitMemoPendingChanges()
        }
    }

    /**
     * 에러 메시지 스낵바 표시
     */
    override fun showErrorSnackbar(view: View, message: String): Snackbar {
        val snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
        snackbar.show()
        return snackbar
    }

    /**
     * 메모 작성 쓰기 모드 스낵바 표시
     */
    override fun showWriteModeSnackbar(view: View): Snackbar {
        val snackbar = Snackbar.make(view, "메모를 작성하려면 쓰기 모드로 전환해주세요", Snackbar.LENGTH_LONG)
        snackbar.show()
        return snackbar
    }

    /**
     * ViewModel 정리 작업 처리
     * @param viewModel 정리할 ViewModel 객체
     */
    override fun cleanupViewModel(viewModel: Any) {
        try {
            Timber.d("[MemoManager] ViewModel 정리 시작: ${viewModel.javaClass.simpleName}")
            
            // 이벤트 구독자 제거
            val viewModelKey = viewModel.hashCode().toString()
            eventHandler.unsubscribe(viewModelKey)
            
            // 현재 작업 취소
            jobs.keys.filter { it.startsWith(viewModelKey) }.forEach { key ->
                jobs[key]?.cancel()
                jobs.remove(key)
            }
            
            Timber.d("[MemoManager] ViewModel 정리 완료: ${viewModel.javaClass.simpleName}")
        } catch (e: Exception) {
            Timber.e(e, "[MemoManager] ViewModel 정리 중 오류 발생")
        }
    }
    
    /**
     * ViewModel 등록
     * @param viewModel 등록할 ViewModel 객체
     */
    override fun registerViewModel(viewModel: Any) {
        try {
            val viewModelKey = viewModel.hashCode().toString()
            Timber.d("[MemoManager] ViewModel 등록: ${viewModel.javaClass.simpleName} (key=$viewModelKey)")
            
            // ViewModel 등록 후 현재 상태 전달
            val isDialogVisible = _state.value.dialogState.isVisible
            val currentMarkerId = _state.value.selectedId
            
            if (isDialogVisible && currentMarkerId != null) {
                Timber.d("[MemoManager] 현재 활성화된 메모 상태가 있음: markerId=$currentMarkerId")
                
                // 현재 상태를 새 ViewModel에 한 번만 전달 (연속 이벤트 발생 방지)
                coroutineScope.launch {
                    // 상태 변경 대신 현재 상태 그대로 유지
                    // 단, 새 ViewModel이 감지할 수 있도록 조금 변경된 형태로 전달
                    val currentState = _state.value
                    
                    // 다이얼로그가 이미 표시된 상태면 다시 열리지 않도록 처리
                    if (currentState.dialogState.isVisible && currentState.selectedId != null) {
                        Timber.d("[MemoManager] 현재 활성 메모 상태를 유지합니다: visible=${currentState.dialogState.isVisible}, markerId=${currentState.selectedId}")
                        
                        // 여기서는 상태를 재발행하지 않고, MemoViewModel이 초기화될 때 
                        // 기존 상태를 그대로 사용하도록 함 
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "[MemoManager] ViewModel 등록 중 오류 발생")
        }
    }

    /**
     * 편집 모드 이벤트 구독 설정
     * 
     * @param handler 이벤트 처리 핸들러
     * @return 구독 작업 Job
     */
    override fun subscribeToEditModeEvents(handler: suspend (EditModeEvent) -> Unit): Job {
        return editModeManager.subscribeToEvents(coroutineScope, handler)
    }
} 