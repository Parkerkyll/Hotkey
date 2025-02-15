package com.parker.hotkey.domain.usecase.memo

import com.parker.hotkey.data.local.entity.MemoEntity
import com.parker.hotkey.data.repository.MemoRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreateMemoUseCaseTest {
    private lateinit var repository: MemoRepository
    private lateinit var useCase: CreateMemoUseCase
    
    @Before
    fun setup() {
        repository = mockk()
        useCase = CreateMemoUseCase(repository)
    }
    
    @Test
    fun `invoke should return success with memo when creation succeeds`() = runTest {
        // Given
        val markerId = "1"
        val content = "Test memo"
        val memo = MemoEntity(
            id = "1",
            markerId = markerId,
            content = content,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis(),
            syncedAt = null,
            syncState = com.parker.hotkey.data.local.entity.SyncState.PENDING,
            version = 1
        )
        
        coEvery { repository.createMemo(markerId, content) } returns memo
        
        // When
        val result = useCase(markerId, content)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(memo, result.getOrNull())
    }
    
    @Test
    fun `invoke should return failure when creation fails`() = runTest {
        // Given
        val markerId = "1"
        val content = "Test memo"
        val exception = RuntimeException("Creation failed")
        
        coEvery { repository.createMemo(markerId, content) } throws exception
        
        // When
        val result = useCase(markerId, content)
        
        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }
} 