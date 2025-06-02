package com.parker.hotkey.data.mapper

import com.parker.hotkey.data.local.entity.MemoEntity
import com.parker.hotkey.domain.model.LastSync
import com.parker.hotkey.domain.model.Memo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Memo 도메인 모델과 MemoEntity 간 변환을 처리하는 매퍼
 */
@Singleton
class MemoEntityMapper @Inject constructor() : SyncEntityMapper<Memo, MemoEntity> {
    
    override fun toEntity(domain: Memo): MemoEntity = MemoEntity(
        id = domain.id,
        userId = domain.userId,
        markerId = domain.markerId,
        content = domain.content,
        modifiedAt = domain.modifiedAt,
        syncStatus = domain.lastSync.status.ordinal,
        syncTimestamp = domain.lastSync.timestamp,
        syncError = null
    )
    
    override fun toDomain(entity: MemoEntity): Memo = Memo(
        id = entity.id,
        userId = entity.userId,
        markerId = entity.markerId,
        content = entity.content,
        lastSync = LastSync(
            status = LastSync.SyncStatus.values()[entity.syncStatus],
            timestamp = entity.syncTimestamp,
            serverVersion = null
        ),
        modifiedAt = entity.modifiedAt
    )
} 