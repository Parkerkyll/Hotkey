package com.parker.hotkey.presentation.memo

import com.parker.hotkey.domain.manager.EditModeManager
import com.parker.hotkey.domain.manager.MemoEvent
import com.parker.hotkey.domain.manager.MemoManager
import com.parker.hotkey.domain.model.Memo
import com.parker.hotkey.domain.repository.AuthRepository
import com.parker.hotkey.domain.usecase.memo.CreateMemoUseCase
import com.parker.hotkey.domain.usecase.memo.DeleteMemoUseCase
import com.parker.hotkey.domain.usecase.memo.GetMemosByMarkerIdUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 메모 관련 비즈니스 로직을 담당하는 클래스입니다.
 * MemoViewModel 및 MapViewModel에서 공유하여 사용할 수 있습니다.
 */
@Singleton
class MemoInteractor @Inject constructor(
    private val createMemoUseCase: CreateMemoUseCase,
    private val deleteMemoUseCase: DeleteMemoUseCase,
    private val getMemosByMarkerIdUseCase: GetMemosByMarkerIdUseCase,
    private val editModeManager: EditModeManager,
    private val memoManager: MemoManager,
    private val authRepository: AuthRepository
) {
    /**
     * 메모 매니저의 메모 목록을 반환합니다.
     */
    val memos: StateFlow<List<Memo>> = memoManager.memos

    /**
     * 메모 이벤트 구독 설정
     * 
     * @param scope 이벤트를 처리할 코루틴 스코프
     * @param handler 이벤트 처리 핸들러
     * @return 구독 작업 Job
     */
    fun subscribeToEvents(scope: CoroutineScope, handler: suspend (MemoEvent) -> Unit): Job {
        return memoManager.subscribeToEvents(scope, handler)
    }

    /**
     * 특정 마커에 대한 메모를 로드합니다.
     * 
     * @param markerId 마커 ID
     * @param scope 코루틴 스코프
     * @param onSuccess 성공 시 콜백
     * @param onError 실패 시 콜백
     * @return 작업 Job
     */
    fun loadMemos(
        markerId: String,
        scope: CoroutineScope,
        onSuccess: (List<Memo>, String) -> Unit,
        onError: (Exception, String) -> Unit
    ): Job {
        return scope.launch {
            try {
                Timber.d("메모 로딩 시작: markerId=$markerId")
                
                // MemoManager를 통해 메모 로드
                memoManager.loadMemosByMarkerId(markerId)
                
                // UseCase를 통해 메모 조회 결과를 Flow로 수집
                getMemosByMarkerIdUseCase(markerId)
                    .collect { memos ->
                        if (!coroutineContext.isActive) {
                            Timber.d("메모 로딩 작업 취소됨: markerId=$markerId")
                            return@collect
                        }
                        Timber.d("메모 로딩 완료: ${memos.size}개")
                        onSuccess(memos, markerId)
                    }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    Timber.d("메모 로딩 작업 취소됨: markerId=$markerId")
                    // 취소는 오류로 처리하지 않음
                    return@launch
                }
                Timber.e(e, "메모 로딩 중 오류 발생: $markerId")
                onError(e, "메모 로딩")
            }
        }
    }

    /**
     * 메모를 생성합니다.
     * 
     * @param userId 사용자 ID
     * @param markerId 마커 ID
     * @param content 메모 내용
     * @param scope 코루틴 스코프
     * @param onError 실패 시 콜백
     * @return 작업 Job
     */
    fun createMemo(
        userId: String, 
        markerId: String, 
        content: String,
        scope: CoroutineScope,
        onError: (Exception) -> Unit
    ): Job {
        return scope.launch {
            try {
                Timber.d("메모 생성 시작: userId=$userId, markerId=$markerId")
                
                // 쓰기 모드 타이머 재시작
                editModeManager.restartEditModeTimer()
                
                // MemoManager를 통해 메모 생성
                memoManager.createMemo(userId, markerId, content)
                Timber.d("메모 생성 요청 완료")
            } catch (e: Exception) {
                Timber.e(e, "메모 생성 중 예외 발생: ${e.message}")
                onError(e)
            }
        }
    }

    /**
     * 메모를 삭제합니다.
     * 
     * @param memo 삭제할 메모 객체
     * @param scope 코루틴 스코프
     * @param onError 실패 시 콜백
     * @return 작업 Job
     */
    fun deleteMemo(
        memo: Memo,
        scope: CoroutineScope,
        onError: (Exception) -> Unit
    ): Job {
        return deleteMemo(memo.id, memo.markerId, scope, onError)
    }

    /**
     * 메모를 삭제합니다.
     * 
     * @param memoId 메모 ID
     * @param markerId 마커 ID
     * @param scope 코루틴 스코프
     * @param onError 실패 시 콜백
     * @return 작업 Job
     */
    fun deleteMemo(
        memoId: String, 
        markerId: String,
        scope: CoroutineScope,
        onError: (Exception) -> Unit
    ): Job {
        return scope.launch {
            try {
                Timber.d("메모 삭제 시작: memoId=$memoId")
                
                // 편집 모드 확인
                if (!editModeManager.getCurrentMode()) {
                    val exception = IllegalStateException("편집 모드에서만 메모를 삭제할 수 있습니다")
                    Timber.w(exception, "메모 삭제 실패: 편집 모드 아님")
                    onError(exception)
                    return@launch
                }
                
                // markerId가 비어있으면 내부적으로 조회
                var actualMarkerId = markerId
                if (actualMarkerId.isEmpty()) {
                    val memo = memoManager.memos.value.find { it.id == memoId }
                    actualMarkerId = memo?.markerId ?: return@launch
                    Timber.d("메모 ID로부터 마커 ID 조회: $actualMarkerId")
                }
                
                // 쓰기 모드 타이머 재시작
                editModeManager.restartEditModeTimer()
                
                // MemoManager를 통해 메모 삭제
                memoManager.deleteMemo(memoId)
                Timber.d("메모 삭제 요청 완료")
            } catch (e: Exception) {
                Timber.e(e, "메모 삭제 중 예외 발생: ${e.message}")
                onError(e)
            }
        }
    }

    /**
     * 메모 다이얼로그를 표시합니다.
     * 
     * @param markerId 표시할 마커 ID
     */
    fun showMemoDialog(markerId: String) {
        memoManager.showMemoDialog(markerId)
    }

    /**
     * 메모 다이얼로그를 숨깁니다.
     */
    fun hideMemoDialog() {
        memoManager.hideMemoDialog()
    }

    /**
     * 선택된 마커를 초기화합니다.
     */
    fun clearSelectedMarker() {
        memoManager.clearSelectedMarker()
    }

    /**
     * 메모 목록을 초기화합니다.
     */
    fun clearMemos() {
        memoManager.clearMemos()
    }

    /**
     * 현재 사용자 ID를 반환합니다.
     * 
     * @return 사용자 ID
     */
    suspend fun getUserId(): String {
        return authRepository.getUserId()
    }

    /**
     * EditModeManager 인스턴스를 반환합니다.
     */
    fun getEditModeManager(): EditModeManager {
        return editModeManager
    }
} 