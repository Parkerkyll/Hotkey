package com.parker.hotkey.domain.model

/**
 * 동기화 가능한 엔티티를 위한 인터페이스
 */
interface Synchronizable {
    val id: String
    val lastSync: LastSync
    val modifiedAt: Long
    
    /**
     * 동기화가 필요한지 확인
     */
    fun needsSync(): Boolean = lastSync.needsSync(modifiedAt)
    
    /**
     * 서버와 동기화 완료 후 호출
     */
    fun markAsSynced(serverVersion: Long): Synchronizable
    
    /**
     * 동기화 실패 시 호출
     */
    fun markAsFailed(): Synchronizable
    
    /**
     * 동기화 충돌 발생 시 호출
     */
    fun markAsConflict(serverVersion: Long): Synchronizable
} 