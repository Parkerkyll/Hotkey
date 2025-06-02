package com.parker.hotkey.domain.usecase.memo

import com.parker.hotkey.domain.model.Memo
import com.parker.hotkey.domain.repository.MemoRepository
import javax.inject.Inject

class CreateMemoUseCase @Inject constructor(
    private val memoRepository: MemoRepository
) {
    suspend operator fun invoke(userId: String, markerId: String, content: String): Result<Memo> = runCatching {
        memoRepository.createMemo(userId, markerId, content)
    }
} 