package com.parker.hotkey.domain.model

import androidx.room.Entity

@Entity
data class LastSync(
    val timestamp: Long = System.currentTimeMillis(),
    val status: SyncStatus = SyncStatus.NONE,
    val serverVersion: Long? = null
) {
    enum class SyncStatus {
        NONE,           // 한번도 동기화되지 않음
        SUCCESS,        // 동기화 성공
        CONFLICT,       // 충돌 발생
        ERROR          // 동기화 실패
    }
    
    fun needsSync(modifiedAt: Long): Boolean = 
        status != SyncStatus.SUCCESS || 
        timestamp < modifiedAt
        
    companion object {
        fun createInitial() = LastSync()
        
        fun createSuccess(serverVersion: Long) = LastSync(
            timestamp = System.currentTimeMillis(),
            status = SyncStatus.SUCCESS,
            serverVersion = serverVersion
        )
        
        fun createError() = LastSync(
            timestamp = System.currentTimeMillis(),
            status = SyncStatus.ERROR
        )
        
        fun createConflict(serverVersion: Long) = LastSync(
            timestamp = System.currentTimeMillis(),
            status = SyncStatus.CONFLICT,
            serverVersion = serverVersion
        )
    }
} 