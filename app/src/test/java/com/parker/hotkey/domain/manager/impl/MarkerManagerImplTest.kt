package com.parker.hotkey.domain.manager.impl

import com.parker.hotkey.domain.manager.MarkerEvent
import com.parker.hotkey.domain.manager.MarkerStateAdapter
import com.parker.hotkey.domain.manager.TemporaryMarkerManager
import com.parker.hotkey.domain.manager.MemoManager
import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.model.MarkerState
import com.parker.hotkey.domain.repository.MarkerRepository
import com.parker.hotkey.domain.usecase.marker.CreateMarkerUseCase
import com.parker.hotkey.domain.usecase.marker.DeleteMarkerWithValidationUseCase
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class MarkerManagerImplTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var markerRepository: MarkerRepository

    @MockK
    private lateinit var createMarkerUseCase: CreateMarkerUseCase

    @MockK
    private lateinit var deleteMarkerWithValidationUseCase: DeleteMarkerWithValidationUseCase

    @MockK
    private lateinit var memoManager: MemoManager

    @MockK
    private lateinit var markerStateAdapter: MarkerStateAdapter

    @MockK
    private lateinit var temporaryMarkerManager: TemporaryMarkerManager

    private lateinit var testScope: TestScope
    private lateinit var markerManager: MarkerManagerImpl
    private val markerEvents = MutableSharedFlow<MarkerEvent>()

    @Before
    fun setUp() {
        testScope = TestScope()
        
        markerManager = MarkerManagerImpl(
            markerRepository = markerRepository,
            createMarkerUseCase = createMarkerUseCase,
            deleteMarkerWithValidationUseCase = deleteMarkerWithValidationUseCase,
            memoManager = memoManager,
            markerStateAdapter = markerStateAdapter,
            temporaryMarkerManager = temporaryMarkerManager,
            coroutineScope = testScope
        )
        
        // 기본 행동 설정
        coEvery { markerRepository.delete(any()) } returns Unit
        coEvery { deleteMarkerWithValidationUseCase(any()) } returns Result.success(Unit)
        every { temporaryMarkerManager.removeTemporaryMarker(any()) } just Runs
    }

    @Test
    fun `임시 마커 삭제 시 API 호출 없이 로컬에서만 처리되는지 확인`() = runTest {
        // Given: 임시 마커 설정
        val markerId = "temp-123"
        every { markerStateAdapter.getMarkerState(markerId) } returns MarkerState.TEMPORARY
        
        // When: 삭제 실행
        val result = markerManager.deleteMarkerByState(markerId)
        
        // Then: 로컬 삭제만 호출, API 호출 없음
        coVerify { markerRepository.delete(markerId) }
        verify { temporaryMarkerManager.removeTemporaryMarker(markerId) }
        coVerify(exactly = 0) { deleteMarkerWithValidationUseCase(markerId) }
        assert(result.isSuccess)
    }
    
    @Test
    fun `영구 마커 삭제 시 API 호출이 발생하는지 확인`() = runTest {
        // Given: 영구 마커 설정
        val markerId = "perm-456"
        every { markerStateAdapter.getMarkerState(markerId) } returns MarkerState.PERSISTED
        
        // When: 삭제 실행
        val result = markerManager.deleteMarkerByState(markerId)
        
        // Then: API 호출이 발생
        coVerify { deleteMarkerWithValidationUseCase(markerId) }
        coVerify(exactly = 0) { markerRepository.delete(markerId) }
        assert(result.isSuccess)
    }
} 