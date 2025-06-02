package com.parker.hotkey.domain.repository

import com.parker.hotkey.domain.repository.BaseRepository
import com.parker.hotkey.domain.model.Memo
import kotlinx.coroutines.flow.Flow

interface MemoRepository : BaseRepository<Memo> {
    fun getMemosByMarkerId(markerId: String): Flow<List<Memo>>
    suspend fun getMemoCount(markerId: String): Int
    suspend fun createMemo(userId: String, markerId: String, content: String): Memo
    suspend fun deleteMemo(memoId: String, markerId: String)
    
    /**
     * 지정된 마커 ID 목록에 해당하는 모든 메모를 동기적으로 가져옵니다.
     * @param markerIds 메모를 조회할 마커 ID 목록
     * @return 메모 목록
     */
    suspend fun getMemosByMarkersSync(markerIds: List<String>): List<Memo>
} 