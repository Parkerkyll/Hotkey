package com.parker.hotkey.data.repository

import com.parker.hotkey.data.local.dao.MemoDao
import com.parker.hotkey.data.local.entity.MemoEntity
import com.parker.hotkey.data.local.entity.SyncState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MemoRepositoryTest {
    private lateinit var memoDao: MemoDao
    private lateinit var repository: MemoRepository
    
    @Before
    fun setup() {
        memoDao = mockk(relaxed = true)
        repository = MemoRepositoryImpl(memoDao)
    }
    
    @Test
    fun `createMemo should create and return a new memo`() = runTest {
        // Given
        val markerId = "1"
        val content = "Test memo"
        
        // When
        val result = repository.createMemo(markerId, content)
        
        // Then
        assertNotNull(result)
        assertEquals(markerId, result.markerId)
        assertEquals(content, result.content)
        assertEquals(SyncState.PENDING, result.syncState)
        coVerify { memoDao.insertMemo(any()) }
    }
    
    @Test
    fun `getMemosByMarkerId should return memos for given markerId`() = runTest {
        // Given
        val markerId = "1"
        val memos = listOf(
            MemoEntity(
                id = "1",
                markerId = markerId,
                content = "Test memo",
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis(),
                syncedAt = null,
                syncState = SyncState.PENDING,
                version = 1
            )
        )
        coEvery { memoDao.getMemosByMarkerId(markerId) } returns flowOf(memos)
        
        // When
        val result = repository.getMemosByMarkerId(markerId)
        
        // Then
        result.collect { resultMemos ->
            assertEquals(memos, resultMemos)
        }
    }
    
    @Test
    fun `deleteMemo should call dao deleteMemoAndCheckMarker`() = runTest {
        // Given
        val memoId = "1"
        val markerId = "1"
        
        // When
        repository.deleteMemo(memoId, markerId)
        
        // Then
        coVerify { memoDao.deleteMemoAndCheckMarker(memoId, markerId, any()) }
    }
    
    @Test
    fun `updateMemosSyncState should update sync state for given memos`() = runTest {
        // Given
        val memoIds = listOf("1", "2")
        val newState = SyncState.SYNCED
        
        // When
        repository.updateMemosSyncState(memoIds, newState)
        
        // Then
        coVerify { memoDao.updateMemosSyncState(memoIds, newState) }
    }
} 