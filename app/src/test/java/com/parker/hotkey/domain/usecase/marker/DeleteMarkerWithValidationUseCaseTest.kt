package com.parker.hotkey.domain.usecase.marker

import com.parker.hotkey.domain.manager.MarkerStateAdapter
import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.model.MarkerState
import com.parker.hotkey.domain.repository.MarkerRepository
import com.parker.hotkey.domain.repository.MemoRepository
import com.parker.hotkey.domain.repository.SyncRepository
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DeleteMarkerWithValidationUseCaseTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    lateinit var markerRepository: MarkerRepository

    @MockK
    lateinit var memoRepository: MemoRepository

    @MockK
    lateinit var syncRepository: SyncRepository

    @MockK
    lateinit var markerStateAdapter: MarkerStateAdapter

    private lateinit var useCase: DeleteMarkerWithValidationUseCase

    @Before
    fun setup() {
        useCase = DeleteMarkerWithValidationUseCase(
            markerRepository = markerRepository,
            memoRepository = memoRepository,
            syncRepository = syncRepository,
            markerStateAdapter = markerStateAdapter
        )

        // 기본 동작 설정
        coEvery { markerRepository.delete(any()) } just Runs
    }

    @Test
    fun `임시 마커 삭제 시 API 호출 없이 로컬에서만 삭제됨`() = runBlocking {
        // Given
        val markerId = "temp-marker-1"
        val tempMarker = Marker(
            id = markerId,
            userId = "user1",
            latitude = 37.5,
            longitude = 127.0,
            geohash = "abc123",
            state = MarkerState.TEMPORARY
        )
        
        // 마커 상태를 TEMPORARY로 설정
        every { markerStateAdapter.getMarkerState(markerId) } returns MarkerState.TEMPORARY
        
        // 마커 존재 여부 확인을 위한 설정
        coEvery { markerRepository.getById(markerId) } returns tempMarker
        
        // When
        val result = useCase(markerId)
        
        // Then
        assertTrue(result.isSuccess)
        
        // 로컬에서만 삭제되었는지 확인
        coVerify { markerRepository.delete(markerId) }
        
        // syncRepository.deleteMarker()가 호출되지 않았는지 확인
        coVerify(exactly = 0) { syncRepository.deleteMarker(markerId) }
    }

    @Test
    fun `영구 마커 삭제 시 API 호출이 수행됨`() = runBlocking {
        // Given
        val markerId = "persisted-marker-1"
        val persistedMarker = Marker(
            id = markerId,
            userId = "user1",
            latitude = 37.5,
            longitude = 127.0,
            geohash = "abc123",
            state = MarkerState.PERSISTED
        )
        
        // 마커 상태를 PERSISTED로 설정
        every { markerStateAdapter.getMarkerState(markerId) } returns MarkerState.PERSISTED
        
        // 마커 존재 여부 확인을 위한 설정
        coEvery { markerRepository.getById(markerId) } returns persistedMarker
        
        // 서버 삭제 성공 설정
        coEvery { syncRepository.deleteMarker(markerId) } returns true
        
        // When
        val result = useCase(markerId)
        
        // Then
        assertTrue(result.isSuccess)
        
        // 서버 API가 호출되었는지 확인
        coVerify { syncRepository.deleteMarker(markerId) }
        
        // 로컬 삭제는 호출되지 않음 (서버 삭제 성공 시 SyncRepository에서 처리)
        coVerify(exactly = 0) { markerRepository.delete(markerId) }
    }

    @Test
    fun `서버 삭제 실패 시 로컬에서만 삭제됨`() = runBlocking {
        // Given
        val markerId = "persisted-marker-2"
        val persistedMarker = Marker(
            id = markerId,
            userId = "user1",
            latitude = 37.5,
            longitude = 127.0,
            geohash = "abc123",
            state = MarkerState.PERSISTED
        )
        
        // 마커 상태를 PERSISTED로 설정
        every { markerStateAdapter.getMarkerState(markerId) } returns MarkerState.PERSISTED
        
        // 마커 존재 여부 확인을 위한 설정
        coEvery { markerRepository.getById(markerId) } returns persistedMarker
        
        // 서버 삭제 실패 설정
        coEvery { syncRepository.deleteMarker(markerId) } returns false
        
        // When
        val result = useCase(markerId)
        
        // Then
        assertTrue(result.isSuccess)
        
        // 서버 API가 호출되었는지 확인
        coVerify { syncRepository.deleteMarker(markerId) }
        
        // 서버 삭제 실패 시 로컬에서 삭제됨
        coVerify { markerRepository.delete(markerId) }
    }
} 