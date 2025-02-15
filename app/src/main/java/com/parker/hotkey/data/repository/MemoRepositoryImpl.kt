package com.parker.hotkey.data.repository

import com.parker.hotkey.data.local.dao.MemoDao
import com.parker.hotkey.data.local.entity.MemoEntity
import com.parker.hotkey.data.local.entity.SyncState
import com.parker.hotkey.domain.repository.MemoRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class MemoRepositoryImpl @Inject constructor(
    private val memoDao: MemoDao
) : MemoRepository {
    
    override fun getMemosByMarkerId(markerId: String): Flow<List<MemoEntity>> {
        return memoDao.getMemosByMarkerId(markerId)
    }
    
    override suspend fun getMemoById(memoId: String): MemoEntity? {
        return memoDao.getMemoById(memoId)
    }
    
    override suspend fun createMemo(markerId: String, content: String): MemoEntity {
        val now = System.currentTimeMillis()
        val memo = MemoEntity(
            id = UUID.randomUUID().toString(),
            markerId = markerId,
            content = content,
            createdAt = now,
            modifiedAt = now,
            syncedAt = null,
            syncState = SyncState.PENDING,
            version = 1
        )
        memoDao.insertMemo(memo)
        return memo
    }
    
    override suspend fun updateMemo(memo: MemoEntity) {
        memoDao.updateMemo(memo.copy(
            modifiedAt = System.currentTimeMillis(),
            syncState = SyncState.PENDING
        ))
    }
    
    override suspend fun deleteMemo(memoId: String, markerId: String) {
        memoDao.deleteMemoAndCheckMarker(memoId, markerId, System.currentTimeMillis())
    }
    
    override suspend fun getMemoCount(markerId: String): Int {
        return memoDao.getMemoCount(markerId)
    }
    
    override suspend fun getUnsyncedMemos(): List<MemoEntity> {
        return memoDao.getMemosBySyncState(SyncState.PENDING)
    }
    
    override suspend fun updateMemosSyncState(memoIds: List<String>, newState: SyncState) {
        memoDao.updateMemosSyncState(memoIds, newState)
    }
} 