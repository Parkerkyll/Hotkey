package com.parker.hotkey.presentation.memo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parker.hotkey.domain.model.Memo
import com.parker.hotkey.domain.repository.MemoRepository
import com.parker.hotkey.data.mapper.toDomain
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
    private val memoRepository: MemoRepository
) : ViewModel() {

    private val _memoState = MutableStateFlow(MemoState())
    val memoState: StateFlow<MemoState> = _memoState.asStateFlow()

    fun getMemos(markerId: String) {
        viewModelScope.launch {
            try {
                _memoState.value = _memoState.value.copy(isLoading = true)
                val memos = memoRepository.getMemosByMarkerId(markerId).first().map { it.toDomain() }
                _memoState.value = _memoState.value.copy(
                    memos = memos,
                    isLoading = false
                )
            } catch (e: Exception) {
                Timber.e(e, "메모 로딩 중 오류 발생")
                _memoState.value = _memoState.value.copy(
                    isLoading = false,
                    error = "메모 로딩 중 오류가 발생했습니다."
                )
            }
        }
    }

    fun addMemo(markerId: String, content: String) {
        viewModelScope.launch {
            try {
                val memo = memoRepository.createMemo(markerId, content).toDomain()
                val currentMemos = _memoState.value.memos.toMutableList()
                currentMemos.add(memo)
                _memoState.value = _memoState.value.copy(
                    memos = currentMemos
                )
            } catch (e: Exception) {
                Timber.e(e, "메모 추가 중 오류 발생")
                _memoState.value = _memoState.value.copy(
                    error = "메모 추가 중 오류가 발생했습니다."
                )
            }
        }
    }

    fun deleteMemo(memoId: String) {
        viewModelScope.launch {
            try {
                val memo = _memoState.value.memos.find { it.id == memoId }
                if (memo != null) {
                    memoRepository.deleteMemo(memoId, memo.markerId)
                    _memoState.value = _memoState.value.copy(
                        memos = _memoState.value.memos.filter { it.id != memoId }
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "메모 삭제 중 오류 발생")
                _memoState.value = _memoState.value.copy(
                    error = "메모 삭제 중 오류가 발생했습니다."
                )
            }
        }
    }

    fun clearError() {
        _memoState.value = _memoState.value.copy(error = null)
    }

    data class MemoState(
        val memos: List<Memo> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null
    )
} 