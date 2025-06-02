package com.parker.hotkey.data.mapper

import com.parker.hotkey.data.local.entity.MarkerEntity
import com.parker.hotkey.domain.model.LastSync
import com.parker.hotkey.domain.model.Marker
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Marker 도메인 모델과 MarkerEntity 간 변환을 처리하는 매퍼
 */
@Singleton
class MarkerEntityMapper @Inject constructor() : SyncEntityMapper<Marker, MarkerEntity> {
    
    override fun toEntity(domain: Marker): MarkerEntity = MarkerEntity(
        id = domain.id,
        userId = domain.userId,
        geohash = domain.geohash,
        latitude = domain.latitude,
        longitude = domain.longitude,
        modifiedAt = domain.modifiedAt,
        syncStatus = domain.lastSync.status.ordinal,
        syncTimestamp = domain.lastSync.timestamp,
        syncError = null
    )
    
    override fun toDomain(entity: MarkerEntity): Marker = Marker(
        id = entity.id,
        userId = entity.userId,
        latitude = entity.latitude,
        longitude = entity.longitude,
        geohash = entity.geohash,
        lastSync = LastSync(
            status = LastSync.SyncStatus.values()[entity.syncStatus], 
            timestamp = entity.syncTimestamp,
            serverVersion = null
        ),
        modifiedAt = entity.modifiedAt
    )
} 