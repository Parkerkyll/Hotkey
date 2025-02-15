package com.parker.hotkey.domain.usecase.memo

import com.parker.hotkey.data.local.entity.MemoEntity
import com.parker.hotkey.data.repository.MemoRepository
import javax.inject.Inject

class CreateMemoUseCase @Inject constructor(
    private val memoRepository: MemoRepository
) {
    suspend operator fun invoke(markerId: String, content: String): Result<MemoEntity> {
        return try {
            val memo = memoRepository.createMemo(markerId, content)
            Result.success(memo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 