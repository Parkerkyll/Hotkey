package com.parker.hotkey.domain.manager.impl

import com.parker.hotkey.MainCoroutineRule
import com.parker.hotkey.TestTree
import com.parker.hotkey.domain.manager.EditModeManager
import com.parker.hotkey.domain.manager.MemoEvent
import com.parker.hotkey.domain.model.Memo
import com.parker.hotkey.domain.model.state.DialogState
import com.parker.hotkey.domain.model.state.MemoState
import com.parker.hotkey.domain.repository.AuthRepository
import com.parker.hotkey.domain.repository.MarkerRepository
import com.parker.hotkey.domain.repository.MemoRepository
import com.parker.hotkey.domain.usecase.UploadChangesUseCase
import com.parker.hotkey.domain.usecase.marker.DeleteMarkerUseCase
import com.parker.hotkey.domain.usecase.marker.DeleteMarkerWithValidationUseCase
import com.parker.hotkey.domain.usecase.memo.CreateMemoUseCase
import com.parker.hotkey.domain.usecase.memo.DeleteMemoUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*
import timber.log.Timber
import java.lang.reflect.Field
import kotlinx.coroutines.flow.StateFlow

@ExperimentalCoroutinesApi
class MemoManagerImplTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()
    
    private lateinit var memoManager: MemoManagerImpl
    
    // Mocks
    private lateinit var memoRepository: MemoRepository
    private lateinit var markerRepository: MarkerRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var createMemoUseCase: CreateMemoUseCase
    private lateinit var deleteMemoUseCase: DeleteMemoUseCase
    private lateinit var deleteMarkerUseCase: DeleteMarkerUseCase
    private lateinit var deleteMarkerWithValidationUseCase: DeleteMarkerWithValidationUseCase
    private lateinit var editModeManager: EditModeManager
    private lateinit var uploadChangesUseCase: UploadChangesUseCase
    
    // 테스트 코루틴 환경
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    
    // 테스트 데이터
    private val testMarkerId = "test_marker_id"
    private val testUserId = "test_user_id"
    private val testMemoId = "test_memo_id"
    private val testContent = "테스트 메모 내용"
    
    private val testMemo = Memo(
        id = testMemoId,
        userId = testUserId,
        markerId = testMarkerId,
        content = testContent,
        modifiedAt = System.currentTimeMillis()
    )
    
    private val testMemoList = listOf(testMemo)
    
    @Before
    fun setup() = runTest {
        // Timber 초기화
        Timber.plant(TestTree())
        
        // Mocks 초기화
        memoRepository = mock()
        markerRepository = mock()
        authRepository = mock()
        createMemoUseCase = mock()
        deleteMemoUseCase = mock()
        deleteMarkerUseCase = mock()
        deleteMarkerWithValidationUseCase = mock()
        editModeManager = mock()
        uploadChangesUseCase = mock()
        
        // 기본 값 설정
        whenever(authRepository.getUserId()).thenReturn(testUserId)
        whenever(createMemoUseCase.invoke(any(), any(), any())).thenReturn(Result.success(testMemo))
        whenever(deleteMemoUseCase.invoke(any(), any())).thenReturn(Result.success(Unit))
        
        // 메모 리포지토리 설정
        whenever(memoRepository.getMemosByMarkerId(testMarkerId)).thenReturn(flowOf(testMemoList))
        
        // MemoManager 초기화
        memoManager = MemoManagerImpl(
            memoRepository = memoRepository,
            markerRepository = markerRepository,
            authRepository = authRepository,
            createMemoUseCase = createMemoUseCase,
            deleteMemoUseCase = deleteMemoUseCase,
            deleteMarkerUseCase = deleteMarkerUseCase,
            deleteMarkerWithValidationUseCase = deleteMarkerWithValidationUseCase,
            editModeManager = editModeManager,
            uploadChangesUseCase = uploadChangesUseCase,
            coroutineScope = testScope
        )
    }
    
    @After
    fun tearDown() {
        Timber.uprootAll()
    }
    
    @Test
    fun `showMemoDialog 호출 시 다이얼로그 상태가 업데이트됨`() = runTest {
        // when
        memoManager.showMemoDialog(testMarkerId)
        advanceUntilIdle() // 모든 코루틴이 완료될 때까지 대기
        
        // then
        val internalState = getInternalState(memoManager, "_state") as MutableStateFlow<*>
        val state = internalState.value as MemoState
        assertTrue(state.dialogState.isVisible)
        assertEquals(testMarkerId, state.selectedId)
    }
    
    @Test
    fun `hideMemoDialog 호출 시 다이얼로그 상태가 업데이트됨`() = runTest {
        // given
        memoManager.showMemoDialog(testMarkerId)
        advanceUntilIdle() // 모든 코루틴이 완료될 때까지 대기
        
        // when
        memoManager.hideMemoDialog()
        advanceUntilIdle() // 모든 코루틴이 완료될 때까지 대기
        
        // then
        val internalState = getInternalState(memoManager, "_state") as MutableStateFlow<*>
        val state = internalState.value as MemoState
        assertFalse(state.dialogState.isVisible)
        // 마커 선택 상태는 유지됨
        assertEquals(testMarkerId, state.selectedId)
    }
    
    @Test
    fun `clearSelectedMarker 호출 시 선택 상태가 초기화됨`() = runTest {
        // given
        memoManager.showMemoDialog(testMarkerId)
        advanceUntilIdle() // 모든 코루틴이 완료될 때까지 대기
        
        // when
        memoManager.clearSelectedMarker()
        advanceUntilIdle() // 모든 코루틴이 완료될 때까지 대기
        
        // then
        val internalState = getInternalState(memoManager, "_state") as MutableStateFlow<*>
        val state = internalState.value as MemoState
        assertNull(state.selectedId)
        assertFalse(state.dialogState.isVisible)
    }
    
    // 리플렉션을 사용하여 private 필드 값 가져오기
    private fun getInternalState(obj: Any, fieldName: String): Any? {
        val field = obj.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(obj)
    }
} 