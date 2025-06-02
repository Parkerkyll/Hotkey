package com.parker.hotkey.domain.usecase.memo

/*
import com.parker.hotkey.domain.repository.MemoRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreateMemoUseCaseTest {
    private lateinit var memoRepository: MemoRepository
    private lateinit var createMemoUseCase: CreateMemoUseCase
    
    @Before
    fun setup() {
        memoRepository = mockk(relaxed = true)
        createMemoUseCase = CreateMemoUseCase(memoRepository)
    }
    
    @Test
    fun `invoke should return success with memo when repository succeeds`() = runTest {
        // Given
        val markerId = "marker1"
        val content = "Test memo"
        
        val memo = Memo(
            id = "1",
            markerId = markerId,
            content = content,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis(),
            syncedAt = null,
            syncState = SyncState.PENDING,
            version = 1
        )
        
        coEvery { memoRepository.createMemo(markerId, content) } returns memo
        
        // When
        val result = createMemoUseCase(markerId, content)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(memo, result.getOrNull())
    }
    
    @Test
    fun `invoke should return failure when repository throws exception`() = runTest {
        // Given
        val markerId = "marker1"
        val content = "Test memo"
        val exception = RuntimeException("Test exception")
        
        coEvery { memoRepository.createMemo(markerId, content) } throws exception
        
        // When
        val result = createMemoUseCase(markerId, content)
        
        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }
}
*/ 