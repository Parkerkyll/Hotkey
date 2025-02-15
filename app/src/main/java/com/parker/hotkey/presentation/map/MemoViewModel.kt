package com.parker.hotkey.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parker.hotkey.data.mapper.toDomain
import com.parker.hotkey.domain.model.Memo
import com.parker.hotkey.domain.repository.MemoRepository
import com.parker.hotkey.domain.manager.MarkerDeletionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MemoViewModel @Inject constructor(
    private val memoRepository: MemoRepository,
    private val markerDeletionManager: MarkerDeletionManager
) : ViewModel() {

    private val _memoState = MutableStateFlow(MemoState())
    val memoState: StateFlow<MemoState> = _memoState.asStateFlow()

    fun addMemo(markerId: String, content: String) {
        viewModelScope.launch {
            try {
                // 현재 메모 개수 확인
                val currentMemoCount = memoRepository.getMemoCount(markerId)
                if (currentMemoCount >= 10) {
                    _memoState.value = _memoState.value.copy(error = "메모는 마커당 최대 10개까지만 추가할 수 있습니다.")
                    return@launch
                }

                // 메모 생성
                val memo = Memo(
                    id = java.util.UUID.randomUUID().toString(),
                    markerId = markerId,
                    content = content,
                    createdAt = System.currentTimeMillis()
                )
                memoRepository.createMemo(markerId, content)
                
                // 메모 목록 갱신
                loadMemos(markerId)
            } catch (e: Exception) {
                Timber.e(e, "메모 추가 중 오류 발생")
                _memoState.value = _memoState.value.copy(error = "메모 추가 중 오류가 발생했습니다.")
            }
        }
    }

    fun deleteMemo(memo: Memo) {
        viewModelScope.launch {
            try {
                memoRepository.deleteMemo(memo.id, memo.markerId)
                
                // 메모 목록 갱신
                loadMemos(memo.markerId)
                
                // 현재 메모 개수 확인
                val currentMemoCount = memoRepository.getMemoCount(memo.markerId)
                if (currentMemoCount == 0) {
                    // 마지막 메모가 삭제된 경우 마커 자동 삭제 예약
                    markerDeletionManager.scheduleMarkerDeletion(memo.markerId)
                    Timber.d("마지막 메모 삭제로 인한 마커 삭제 예약: ${memo.markerId}")
                }
            } catch (e: Exception) {
                Timber.e(e, "메모 삭제 중 오류 발생")
                _memoState.value = _memoState.value.copy(error = "메모 삭제 중 오류가 발생했습니다.")
            }
        }
    }

    fun loadMemos(markerId: String) {
        viewModelScope.launch {
            try {
                val memos = memoRepository.getMemosByMarkerId(markerId).first().map { it.toDomain() }
                _memoState.value = _memoState.value.copy(
                    memos = memos,
                    selectedMarkerId = markerId
                )
            } catch (e: Exception) {
                Timber.e(e, "메모 로딩 중 오류 발생")
                _memoState.value = _memoState.value.copy(error = "메모 로딩 중 오류가 발생했습니다.")
            }
        }
    }

    fun clearSelectedMarkerId() {
        _memoState.value = _memoState.value.copy(selectedMarkerId = null)
    }

    suspend fun getMemosByMarkerId(markerId: String): List<Memo> {
        return memoRepository.getMemosByMarkerId(markerId).first().map { it.toDomain() }
    }

    private fun handleDeleteMemoResult(result: Result<Unit>) {
        when {
            result.isSuccess -> {
                _memoState.value = _memoState.value.copy(
                    isDeleting = false,
                    error = null
                )
            }
            result.isFailure -> {
                _memoState.value = _memoState.value.copy(
                    isDeleting = false,
                    error = result.exceptionOrNull()?.message ?: "메모 삭제 실패"
                )
            }
        }
    }
}

data class MemoState(
    val memos: List<Memo> = emptyList(),
    val selectedMarkerId: String? = null,
    val error: String? = null,
    val isDeleting: Boolean = false
) 