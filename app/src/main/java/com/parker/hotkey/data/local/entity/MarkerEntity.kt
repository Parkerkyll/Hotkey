package com.parker.hotkey.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Embedded
import androidx.room.Relation
import com.parker.hotkey.domain.model.LastSync
import com.parker.hotkey.domain.model.Marker

/**
 * 마커 데이터베이스 엔티티
 */
@Entity(
    tableName = "markers",
    indices = [
        Index("geohash"),
        Index("modifiedAt"),
        // 줌 레벨에 따른 geohash 쿼리 최적화를 위한 복합 인덱스 추가
        Index(value = ["geohash", "userId"], name = "index_marker_geohash_userId"),
        // 사용자별, 수정 시간별 정렬 최적화 인덱스
        Index(value = ["userId", "modifiedAt"], name = "index_marker_userId_modifiedAt"),
        // 마커 로딩 성능 최적화를 위한 복합 인덱스 추가
        Index(value = ["geohash", "modifiedAt"], name = "index_marker_geohash_modifiedAt")
    ]
)
data class MarkerEntity(
    @PrimaryKey
    val id: String,                // 마커 고유 ID
    
    val userId: String,           // 카카오 사용자 ID (String)
    val geohash: String,          // geohash6 기반 위치 인덱스
    val latitude: Double,         // 위도
    val longitude: Double,        // 경도
    
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long,
    
    // 동기화 관련 필드
    val syncStatus: Int = LastSync.SyncStatus.NONE.ordinal,
    val syncTimestamp: Long = 0L,
    val syncError: String? = null
) {
    fun toDomain(): Marker = Marker(
        id = id,
        userId = userId,
        latitude = latitude,
        longitude = longitude,
        geohash = geohash,
        lastSync = LastSync(
            status = LastSync.SyncStatus.values()[syncStatus], 
            timestamp = syncTimestamp,
            serverVersion = null
        ),
        modifiedAt = modifiedAt
    )
    
    companion object {
        fun fromDomain(marker: Marker): MarkerEntity = MarkerEntity(
            id = marker.id,
            userId = marker.userId,
            geohash = marker.geohash,
            latitude = marker.latitude,
            longitude = marker.longitude,
            modifiedAt = marker.modifiedAt,
            syncStatus = marker.lastSync.status.ordinal,
            syncTimestamp = marker.lastSync.timestamp,
            syncError = null
        )
    }
}

data class MarkerWithMemos(
    @Embedded 
    val marker: MarkerEntity,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "markerId"
    )
    val memos: List<MemoEntity>
) {
    fun toDomain(): Marker = marker.toDomain().copy(
        memos = memos.map { it.toDomain() }
    )

    companion object {
        fun fromDomain(marker: Marker): MarkerWithMemos = MarkerWithMemos(
            marker = MarkerEntity.fromDomain(marker),
            memos = marker.memos.map { MemoEntity.fromDomain(it) }
        )
    }
}