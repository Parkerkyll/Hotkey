package com.parker.hotkey.domain.usecase.memo

import com.parker.hotkey.data.local.entity.MemoEntity
import com.parker.hotkey.data.repository.MemoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMemosByMarkerIdUseCase @Inject constructor(
    private val memoRepository: MemoRepository
) {
    operator fun invoke(markerId: String): Flow<List<MemoEntity>> {
        return memoRepository.getMemosByMarkerId(markerId)
    }
} 