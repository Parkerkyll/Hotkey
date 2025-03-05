package com.parker.hotkey.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parker.hotkey.domain.model.Memo
import com.parker.hotkey.domain.manager.MarkerDeletionManager
import com.parker.hotkey.domain.usecase.memo.CreateMemoUseCase
import com.parker.hotkey.domain.usecase.memo.DeleteMemoUseCase
import com.parker.hotkey.domain.usecase.memo.GetMemosByMarkerIdUseCase
import com.parker.hotkey.presentation.state.MemoState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MemoViewModel @Inject constructor(
    private val createMemoUseCase: CreateMemoUseCase,
    private val deleteMemoUseCase: DeleteMemoUseCase,
    private val getMemosByMarkerIdUseCase: GetMemosByMarkerIdUseCase,
    private val markerDeletionManager: MarkerDeletionManager
) : ViewModel() {

    private val _memoState = MutableStateFlow<MemoState>(MemoState.Initial)
    val memoState: StateFlow<MemoState> = _memoState.asStateFlow()
    
    private var loadMemosJob: Job? = null

    fun loadMemos(markerId: String) {
        loadMemosJob?.cancel()
        
        loadMemosJob = viewModelScope.launch {
            try {
                Timber.d("메모 로딩 시작: markerId=$markerId")
                _memoState.value = MemoState.Loading
                
                getMemosByMarkerIdUseCase(markerId)
                    .catch { e ->
                        handleException(e, "메모 로딩")
                    }
                    .collect { memos ->
                        Timber.d("메모 로딩 완료: ${memos.size}개")
                        updateMemoState(memos, markerId)
                    }
            } catch (e: Exception) {
                handleException(e, "메모 로딩")
            }
        }
    }

    fun addMemo(markerId: String, content: String) {
        viewModelScope.launch {
            try {
                Timber.d("메모 추가 시작: markerId=$markerId, content=$content")
                createMemoUseCase(markerId, content)
                    .onSuccess { memo ->
                        val currentState = _memoState.value
                        if (currentState is MemoState.Success) {
                            updateMemoState(
                                currentState.memos + memo,
                                markerId
                            )
                            markerDeletionManager.cancelScheduledDeletion(markerId)
                            Timber.d("마커 자동 삭제 취소됨: $markerId")
                        }
                    }
                    .onFailure { e ->
                        handleException(e, "메모 추가")
                    }
            } catch (e: Exception) {
                handleException(e, "메모 추가")
            }
        }
    }

    fun deleteMemo(memo: Memo) {
        deleteMemo(memo.id, memo.markerId)
    }

    fun deleteMemo(memoId: String, markerId: String) {
        viewModelScope.launch {
            try {
                Timber.d("메모 삭제 시작: memoId=$memoId")
                deleteMemoUseCase(memoId, markerId)
                    .onSuccess {
                        val currentState = _memoState.value
                        if (currentState is MemoState.Success) {
                            val updatedMemos = currentState.memos.filter { it.id != memoId }
                            updateMemoState(updatedMemos, currentState.currentMarkerId)
                            
                            if (updatedMemos.isEmpty()) {
                                markerDeletionManager.scheduleMarkerDeletion(markerId)
                                Timber.d("마커 자동 삭제 예약됨: $markerId")
                            }
                        }
                    }
                    .onFailure { e ->
                        handleException(e, "메모 삭제")
                    }
            } catch (e: Exception) {
                handleException(e, "메모 삭제")
            }
        }
    }

    private fun updateMemoState(memos: List<Memo>, markerId: String) {
        _memoState.value = MemoState.Success(
            memos = memos,
            currentMarkerId = markerId
        )
        Timber.d("메모 상태 업데이트: ${memos.size}개의 메모")
    }

    private fun handleException(e: Throwable, operation: String) {
        if (e is kotlinx.coroutines.CancellationException) {
            Timber.d("$operation 작업 취소됨")
            return
        }
        Timber.e(e, "$operation 중 오류 발생")
        _memoState.value = MemoState.Error("${operation}에 실패했습니다")
    }

    fun clearError() {
        _memoState.value = MemoState.Initial
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.coroutineContext.cancelChildren()
    }
} 