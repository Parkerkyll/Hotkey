package com.parker.hotkey.data.mapper

import com.parker.hotkey.data.local.entity.MarkerEntity
import com.parker.hotkey.data.remote.sync.dto.MarkerDto
import com.parker.hotkey.domain.model.LastSync
import com.parker.hotkey.domain.model.Marker
import com.naver.maps.geometry.LatLng
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Marker 모델과 DTO, Entity 간 변환을 담당하는 Mapper 클래스
 */
@Singleton
class MarkerMapper @Inject constructor() {
    
    /**
     * MarkerDto를 도메인 모델로 변환
     */
    fun fromDto(dto: MarkerDto): Marker {
        return Marker(
            id = dto.id,
            userId = "", // API 응답에는 userId가 없으므로 빈 값으로 설정
            modifiedAt = dto.modifiedAt,
            latitude = dto.latitude,
            longitude = dto.longitude,
            geohash = dto.geohash,
            position = LatLng(dto.latitude, dto.longitude),
            lastSync = LastSync.createSuccess(dto.modifiedAt) // 서버에서 받은 데이터는 동기화된 상태로 간주
        )
    }
    
    /**
     * 도메인 모델을 MarkerDTO로 변환
     */
    fun toDto(marker: Marker): MarkerDto {
        return MarkerDto(
            id = marker.id,
            geohash = marker.geohash,
            latitude = marker.latitude,
            longitude = marker.longitude,
            modifiedAt = marker.modifiedAt
        )
    }
    
    /**
     * MarkerEntity를 도메인 모델로 변환
     */
    fun fromEntity(entity: MarkerEntity): Marker {
        return Marker(
            id = entity.id,
            userId = entity.userId,
            lastSync = LastSync(
                status = LastSync.SyncStatus.values()[entity.syncStatus],
                timestamp = entity.syncTimestamp,
                serverVersion = null
            ),
            modifiedAt = entity.modifiedAt,
            latitude = entity.latitude,
            longitude = entity.longitude,
            geohash = entity.geohash,
            position = LatLng(entity.latitude, entity.longitude)
        )
    }
    
    /**
     * 도메인 모델을 MarkerEntity로 변환
     */
    fun toEntity(marker: Marker): MarkerEntity {
        return MarkerEntity(
            id = marker.id,
            userId = marker.userId,
            modifiedAt = marker.modifiedAt,
            latitude = marker.latitude,
            longitude = marker.longitude,
            geohash = marker.geohash,
            syncStatus = marker.lastSync.status.ordinal,
            syncTimestamp = marker.lastSync.timestamp,
            syncError = null
        )
    }
    
    /**
     * 새로운 Marker 생성
     */
    fun createNewMarker(
        userId: String,
        latitude: Double,
        longitude: Double,
        geohash: String
    ): Marker {
        return Marker(
            id = UUID.randomUUID().toString(),
            userId = userId,
            lastSync = LastSync.createInitial(),
            modifiedAt = System.currentTimeMillis(),
            latitude = latitude,
            longitude = longitude,
            geohash = geohash,
            position = LatLng(latitude, longitude)
        )
    }
} 