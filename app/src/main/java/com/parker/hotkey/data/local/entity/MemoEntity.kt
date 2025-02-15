package com.parker.hotkey.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
        Index(value = ["markerId"]),
        Index(value = ["syncState"]),
        Index(value = ["modifiedAt"])
    ]
)
data class MemoEntity(
    @PrimaryKey
    val id: String,              // 메모 고유 ID
    
    val markerId: String,        // 연결된 마커 ID
    val content: String,         // 메모 내용
    
    val createdAt: Long,         // 생성 시간
    val modifiedAt: Long,        // 수정 시간
    val syncedAt: Long?,         // 마지막 동기화 시간
    
    val syncState: SyncState,    // 동기화 상태
    val version: Long,           // 버전 관리용
    val isDeleted: Boolean = false  // 소프트 삭제 플래그
) 