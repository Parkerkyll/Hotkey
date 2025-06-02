package com.parker.hotkey.domain.model.state

import com.parker.hotkey.domain.model.Memo

/**
 * 메모 관련 상태를 관리하는 데이터 클래스
 * 
 * Phase 5 최적화:
 * - equals/hashCode 최적화
 * - List<Memo>를 캐싱하여 불필요한 리스트 복사 방지
 * - 상태 변화 감지 최적화
 */
data class MemoState(
    val memos: List<Memo> = emptyList(),
    override val selectedId: String? = null,
    val dialogState: DialogState = DialogState(),
    override val syncState: SyncState = SyncState(),
    override val isLoading: Boolean = false,
    override val error: String? = null
) : SyncableState, SelectableState {
    /**
     * 캐시 키 계산 (상태 변화 감지에 사용)
     */
    private val cacheKey: Int by lazy {
        // 캐시 키는 불변 필드만 사용하여 계산
        var result = selectedId?.hashCode() ?: 0
        result = 31 * result + dialogState.hashCode()
        result = 31 * result + isLoading.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        result
    }
    
    /**
     * 기본 equals 구현 최적화
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemoState) return false
        
        // 캐시 키 비교로 빠른 불일치 감지
        if (cacheKey != other.cacheKey) return false
        
        // 메모 목록 비교 - 가장 비용이 큰 작업이므로 마지막에 수행
        if (memos != other.memos) return false
        if (syncState != other.syncState) return false
        
        return true
    }
    
    /**
     * 기본 hashCode 구현 최적화
     */
    override fun hashCode(): Int {
        var result = cacheKey
        result = 31 * result + memos.hashCode()
        result = 31 * result + syncState.hashCode()
        return result
    }
    
    /**
     * 메모 목록에서 특정 ID의 메모를 찾습니다.
     * 자주 사용되는 연산이므로 메서드로 제공합니다.
     */
    fun findMemoById(memoId: String): Memo? {
        return memos.find { it.id == memoId }
    }
    
    /**
     * 특정 마커의 메모 목록을 반환합니다.
     * 자주 사용되는 연산이므로 메서드로 제공합니다.
     */
    fun getMemosByMarkerId(markerId: String): List<Memo> {
        return memos.filter { it.markerId == markerId }
    }
}

/**
 * 다이얼로그 상태를 관리하는 데이터 클래스
 */
data class DialogState(
    val isVisible: Boolean = false,
    val markerId: String? = null,
    val isTemporary: Boolean = false
)

/**
 * 동기화 상태를 관리하는 데이터 클래스
 * 
 * Phase 5 최적화:
 * - 불필요한 객체 생성 방지
 * - equals/hashCode 최적화
 */
data class SyncState(
    val pendingMemos: Set<String> = emptySet(), // 동기화 대기 중인 메모 ID 목록
    val failedMemos: Map<String, String> = emptyMap(), // 실패한 메모 ID와 에러 메시지
    val lastSyncTimestamp: Long = 0L,
    val isSyncing: Boolean = false
) {
    // 빈 상태를 싱글톤으로 제공하여 불필요한 객체 생성 방지
    companion object {
        val EMPTY = SyncState()
    }
    
    /**
     * 동기화 상태가 기본 상태인지 확인합니다.
     */
    val isEmpty: Boolean
        get() = pendingMemos.isEmpty() && failedMemos.isEmpty() && !isSyncing && lastSyncTimestamp == 0L
    
    /**
     * 기본 equals 구현 최적화
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SyncState) return false
        
        // 비용이 낮은 비교부터 수행
        if (lastSyncTimestamp != other.lastSyncTimestamp) return false
        if (isSyncing != other.isSyncing) return false
        
        // 컬렉션 비교는 비용이 크므로 마지막에 수행
        if (pendingMemos != other.pendingMemos) return false
        if (failedMemos != other.failedMemos) return false
        
        return true
    }
    
    /**
     * 기본 hashCode 구현 최적화
     */
    override fun hashCode(): Int {
        var result = pendingMemos.hashCode()
        result = 31 * result + failedMemos.hashCode()
        result = 31 * result + lastSyncTimestamp.hashCode()
        result = 31 * result + isSyncing.hashCode()
        return result
    }
} 