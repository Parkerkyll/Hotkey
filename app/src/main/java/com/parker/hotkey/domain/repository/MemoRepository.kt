package com.parker.hotkey.domain.repository

import com.parker.hotkey.data.local.entity.MemoEntity
import com.parker.hotkey.data.local.entity.SyncState
import kotlinx.coroutines.flow.Flow

interface MemoRepository {
    fun getMemosByMarkerId(markerId: String): Flow<List<MemoEntity>>
    suspend fun getMemoById(memoId: String): MemoEntity?
    suspend fun createMemo(markerId: String, content: String): MemoEntity
    suspend fun updateMemo(memo: MemoEntity)
    suspend fun deleteMemo(memoId: String, markerId: String)
    suspend fun getMemoCount(markerId: String): Int
    suspend fun getUnsyncedMemos(): List<MemoEntity>
    suspend fun updateMemosSyncState(memoIds: List<String>, newState: SyncState)
} 