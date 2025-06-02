package com.parker.hotkey.domain.util

import com.parker.hotkey.domain.model.Memo
import com.parker.hotkey.domain.repository.MemoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * 메모 업데이트 지원 유틸리티
 * 
 * 여러 컴포넌트에서 동시에 메모를 업데이트하는 경우 충돌을 방지하고
 * 효율적인 처리를 위한 유틸리티입니다.
 */
class MemoUpdateHelper(
    private val memoRepository: MemoRepository,
    private val coroutineScope: CoroutineScope,
    private val weakReferenceManager: WeakReferenceManager<Memo> = WeakReferenceManager()
) {
    private val TAG = "MemoUpdateHelper"
    private val pendingUpdates = ConcurrentHashMap<String, Memo>()
    private var batchUpdateJob: Job? = null
    
    /**
     * 메모를 등록합니다.
     * 
     * @param memoId 메모 ID
     * @param memo 메모 객체
     */
    fun registerMemo(memoId: String, memo: Memo) {
        weakReferenceManager.register(memoId, memo)
    }
    
    /**
     * 메모 업데이트를 예약합니다.
     * 
     * @param memo 업데이트할 메모
     */
    fun scheduleMemoUpdate(memo: Memo) {
        val memoId = memo.id
        pendingUpdates[memoId] = memo
        
        // 현재 실행 중인 배치 업데이트가 없으면 시작
        if (batchUpdateJob == null || batchUpdateJob?.isActive == false) {
            startBatchUpdate()
        }
    }
    
    /**
     * 배치 업데이트를 시작합니다.
     */
    private fun startBatchUpdate() {
        batchUpdateJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                // 약간의 지연 후 배치 처리
                kotlinx.coroutines.delay(300)
                
                // 대기 중인 업데이트가 있는지 확인
                if (pendingUpdates.isEmpty()) {
                    return@launch
                }
                
                // 현재 대기 중인 업데이트를 복사하고 초기화
                val updates = HashMap(pendingUpdates)
                pendingUpdates.clear()
                
                Timber.d("$TAG - 배치 메모 업데이트 시작 (${updates.size}개)")
                
                // 데이터베이스에 저장
                updates.values.forEach { memo ->
                    try {
                        memoRepository.update(memo)
                    } catch (e: Exception) {
                        Timber.e(e, "$TAG - 메모 저장 실패: ID=${memo.id}")
                    }
                }
                
                // 참조 업데이트
                coroutineScope.launch {
                    val count = weakReferenceManager.forEachObject { oldMemo ->
                        val memoId = oldMemo.id
                        val updatedMemo = updates[memoId]
                        if (updatedMemo != null) {
                            // 약 참조로 유지되는 객체도 업데이트
                            // (현재는 데이터 클래스이므로 불변 - 실제로는 업데이트 메커니즘 필요)
                            registerMemo(memoId, updatedMemo)
                            Timber.v("$TAG - 약 참조 메모 갱신됨: ID=$memoId")
                        }
                    }
                    Timber.d("$TAG - 약 참조 메모 업데이트 처리됨: ${count}개")
                }
                
                Timber.d("$TAG - 배치 메모 업데이트 완료")
            } catch (e: Exception) {
                Timber.e(e, "$TAG - 배치 업데이트 중 오류 발생")
            }
        }
    }
    
    /**
     * 등록된 모든 메모를 초기화합니다.
     */
    fun clear() {
        batchUpdateJob?.cancel()
        batchUpdateJob = null
        pendingUpdates.clear()
        weakReferenceManager.clear()
    }
} 