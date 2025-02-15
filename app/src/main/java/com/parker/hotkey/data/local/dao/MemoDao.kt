package com.parker.hotkey.data.local.dao

import androidx.room.*
import com.parker.hotkey.data.local.entity.MemoEntity
import com.parker.hotkey.data.local.entity.SyncState
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoDao {
    @Query("SELECT * FROM memos WHERE markerId = :markerId AND isDeleted = 0 ORDER BY createdAt DESC")
    fun getMemosByMarkerId(markerId: String): Flow<List<MemoEntity>>
    
    @Query("SELECT * FROM memos WHERE id = :memoId AND isDeleted = 0")
    suspend fun getMemoById(memoId: String): MemoEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemo(memo: MemoEntity)
    
    @Update
    suspend fun updateMemo(memo: MemoEntity)
    
    @Query("UPDATE memos SET isDeleted = 1, modifiedAt = :timestamp, syncState = :syncState WHERE id = :memoId")
    suspend fun softDeleteMemo(memoId: String, timestamp: Long, syncState: SyncState = SyncState.PENDING)
    
    @Query("SELECT COUNT(*) FROM memos WHERE markerId = :markerId AND isDeleted = 0")
    suspend fun getMemoCount(markerId: String): Int
    
    @Query("SELECT * FROM memos WHERE syncState = :syncState AND isDeleted = 0")
    suspend fun getMemosBySyncState(syncState: SyncState): List<MemoEntity>
    
    @Query("UPDATE memos SET syncState = :newState WHERE id IN (:memoIds)")
    suspend fun updateMemosSyncState(memoIds: List<String>, newState: SyncState)
    
    @Transaction
    suspend fun deleteMemoAndCheckMarker(memoId: String, markerId: String, timestamp: Long) {
        softDeleteMemo(memoId, timestamp)
        val remainingMemos = getMemoCount(markerId)
        if (remainingMemos == 0) {
            softDeleteMarker(markerId, timestamp)
        }
    }

    @Query("UPDATE markers SET isDeleted = 1, modifiedAt = :timestamp, syncState = :syncState WHERE id = :markerId")
    suspend fun softDeleteMarker(markerId: String, timestamp: Long, syncState: SyncState = SyncState.PENDING)
} 