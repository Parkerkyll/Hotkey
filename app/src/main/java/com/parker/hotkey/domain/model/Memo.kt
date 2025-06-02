package com.parker.hotkey.domain.model

data class Memo(
    override val id: String,
    val userId: String,
    val markerId: String,
    val content: String,
    override val lastSync: LastSync = LastSync.createInitial(),
    override val modifiedAt: Long = System.currentTimeMillis()
) : Synchronizable {
    
    override fun markAsSynced(serverVersion: Long): Memo {
        return copy(
            lastSync = LastSync.createSuccess(serverVersion)
        )
    }
    
    override fun markAsFailed(): Memo {
        return copy(
            lastSync = LastSync.createError()
        )
    }
    
    override fun markAsConflict(serverVersion: Long): Memo {
        return copy(
            lastSync = LastSync.createConflict(serverVersion)
        )
    }
} 