package com.parker.hotkey.domain.model.state

/**
 * 모든 상태 클래스가 구현해야 하는 기본 인터페이스
 */
interface BaseState {
    val isLoading: Boolean
    val error: String?
}

/**
 * 동기화 가능한 상태를 나타내는 인터페이스
 */
interface SyncableState : BaseState {
    val syncState: SyncState
}

/**
 * 선택 가능한 상태를 나타내는 인터페이스
 */
interface SelectableState : BaseState {
    val selectedId: String?
} 