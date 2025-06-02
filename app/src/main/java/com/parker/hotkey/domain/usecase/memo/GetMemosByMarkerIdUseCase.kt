package com.parker.hotkey.domain.usecase.memo

import com.parker.hotkey.domain.model.Memo
import com.parker.hotkey.domain.repository.MemoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMemosByMarkerIdUseCase @Inject constructor(
    private val memoRepository: MemoRepository
) {
    operator fun invoke(markerId: String): Flow<List<Memo>> {
        return memoRepository.getMemosByMarkerId(markerId)
    }
} 