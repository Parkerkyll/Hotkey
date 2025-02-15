package com.parker.hotkey.domain.usecase.memo

import com.parker.hotkey.data.repository.MemoRepository
import javax.inject.Inject

class DeleteMemoUseCase @Inject constructor(
    private val memoRepository: MemoRepository
) {
    suspend operator fun invoke(memoId: String, markerId: String): Result<Unit> {
        return try {
            memoRepository.deleteMemo(memoId, markerId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 