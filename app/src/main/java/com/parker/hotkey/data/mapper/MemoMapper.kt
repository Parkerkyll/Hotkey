package com.parker.hotkey.data.mapper

import com.parker.hotkey.data.local.entity.MemoEntity
import com.parker.hotkey.domain.model.Memo

fun MemoEntity.toDomain(): Memo {
    return Memo(
        id = id,
        markerId = markerId,
        content = content,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        syncedAt = syncedAt,
        version = version
    )
}

fun Memo.toEntity(): MemoEntity {
    return MemoEntity(
        id = id,
        markerId = markerId,
        content = content,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        syncedAt = syncedAt,
        syncState = com.parker.hotkey.data.local.entity.SyncState.PENDING,
        version = version
    )
} 