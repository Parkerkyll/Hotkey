package com.parker.hotkey.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.parker.hotkey.domain.model.LastSync
import com.parker.hotkey.domain.model.Memo

/**
 * 메모 데이터베이스 엔티티
 */
@Entity(
    tableName = "memos",
    foreignKeys = [
        ForeignKey(
            entity = MarkerEntity::class,
            parentColumns = ["id"],
            childColumns = ["markerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["markerId"])
    ]
)
data class MemoEntity(
    @PrimaryKey
    val id: String,                // 메모 고유 ID
    
    val userId: String,           // 카카오 사용자 ID (String)
    val markerId: String,          // 연결된 마커 ID
    val content: String,         // 메모 내용
    
    val modifiedAt: Long,
    
    // 동기화 관련 필드
    val syncStatus: Int = LastSync.SyncStatus.NONE.ordinal,
    val syncTimestamp: Long = 0L,
    val syncError: String? = null
) {
    fun toDomain(): Memo = Memo(
        id = id,
        userId = userId,
        markerId = markerId,
        content = content,
        lastSync = LastSync(
            status = LastSync.SyncStatus.values()[syncStatus],
            timestamp = syncTimestamp,
            serverVersion = null
        ),
        modifiedAt = modifiedAt
    )
    
    companion object {
        fun fromDomain(memo: Memo): MemoEntity = MemoEntity(
            id = memo.id,
            userId = memo.userId,
            markerId = memo.markerId,
            content = memo.content,
            modifiedAt = memo.modifiedAt,
            syncStatus = memo.lastSync.status.ordinal,
            syncTimestamp = memo.lastSync.timestamp,
            syncError = null
        )
    }
} 