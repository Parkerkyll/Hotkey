package com.parker.hotkey.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "markers",
    indices = [
        Index(value = ["geohash"]),
        Index(value = ["syncState"]),
        Index(value = ["modifiedAt"])
    ]
)
data class MarkerEntity(
    @PrimaryKey
    val id: String,                // 마커 고유 ID
    
    val geohash: String,          // geohash6 기반 위치 인덱스
    val latitude: Double,         // 위도
    val longitude: Double,        // 경도
    
    val createdAt: Long,         // 생성 시간
    val modifiedAt: Long,        // 수정 시간
    val syncedAt: Long?,         // 마지막 동기화 시간
    
    val syncState: SyncState,    // 동기화 상태
    val version: Long,           // 버전 관리용
    val isDeleted: Boolean = false  // 소프트 삭제 플래그
)

enum class SyncState {
    SYNCED,     // 동기화 완료
    PENDING,    // 동기화 대기
    ERROR       // 동기화 오류
} 