package com.parker.hotkey.presentation.state

import com.parker.hotkey.domain.model.Memo

sealed class MemoUiState {
    object Initial : MemoUiState()
    object Loading : MemoUiState()
    data class Success(
        val memos: List<Memo>,
        val currentMarkerId: String
    ) : MemoUiState()
    data class Error(val message: String) : MemoUiState()
} 