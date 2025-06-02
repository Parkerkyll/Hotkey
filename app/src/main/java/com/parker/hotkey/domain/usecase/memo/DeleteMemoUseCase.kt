package com.parker.hotkey.domain.usecase.memo

import com.parker.hotkey.domain.repository.MemoRepository
import javax.inject.Inject

class DeleteMemoUseCase @Inject constructor(
    private val memoRepository: MemoRepository
) {
    suspend operator fun invoke(memoId: String, markerId: String): Result<Unit> = runCatching {
        memoRepository.deleteMemo(memoId, markerId)
    }
} 