package com.parker.hotkey.data.local.dao

import androidx.room.*
import com.parker.hotkey.data.local.entity.MemoEntity
import com.parker.hotkey.domain.model.LastSync
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@Dao
interface MemoDao {
    @Query("SELECT * FROM memos")
    fun getAllMemos(): Flow<List<MemoEntity>>
    
    /**
     * 특정 마커의 메모 조회
     */
    @Query("SELECT * FROM memos WHERE markerId = :markerId ORDER BY modifiedAt DESC")
    fun getMemosByMarkerId(markerId: String): Flow<List<MemoEntity>>
    
    /**
     * ID로 메모 조회
     */
    @Query("SELECT * FROM memos WHERE id = :id")
    suspend fun getMemoById(id: String): MemoEntity?
    
    /**
     * 메모 삽입
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemo(memo: MemoEntity)
    
    /**
     * 여러 메모 삽입
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemos(memos: List<MemoEntity>)
    
    @Update
    suspend fun updateMemo(memo: MemoEntity)
    
    /**
     * 메모 객체 삭제
     */
    @Delete
    suspend fun deleteMemo(memo: MemoEntity)
    
    /**
     * ID로 메모 삭제
     */
    @Query("DELETE FROM memos WHERE id = :id")
    suspend fun deleteMemoById(id: String)
    
    /**
     * 특정 마커의 메모 개수 조회
     */
    @Query("SELECT COUNT(*) FROM memos WHERE markerId = :markerId")
    suspend fun getMemoCount(markerId: String): Int
    
    @Query("""
        SELECT * FROM memos 
        WHERE syncStatus = :syncStatus
    """)
    suspend fun getUnsyncedMemos(syncStatus: Int = LastSync.SyncStatus.NONE.ordinal): List<MemoEntity>
    
    @Query("UPDATE memos SET syncStatus = :syncStatus, syncTimestamp = :syncTimestamp, syncError = :syncError WHERE id IN (:memoIds)")
    suspend fun updateMemosLastSync(memoIds: List<String>, syncStatus: Int, syncTimestamp: Long, syncError: String?)
    
    @Query("""
        UPDATE markers 
        SET modifiedAt = :timestamp 
        WHERE id = (
            SELECT markerId 
            FROM memos 
            WHERE id = :memoId
        )
    """)
    suspend fun updateParentMarkerModifiedAt(memoId: String, timestamp: Long)
    
    @Transaction
    suspend fun deleteMemoAndUpdateMarker(memoId: String, timestamp: Long) {
        updateParentMarkerModifiedAt(memoId, timestamp)
        deleteMemoById(memoId)
    }
    
    @Query("DELETE FROM memos WHERE markerId = :markerId")
    suspend fun deleteByMarkerId(markerId: String)
    
    @Query("DELETE FROM memos WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
    
    @Query("UPDATE memos SET syncStatus = :status, syncTimestamp = :timestamp, syncError = :error WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: Int, timestamp: Long, error: String?)
} 