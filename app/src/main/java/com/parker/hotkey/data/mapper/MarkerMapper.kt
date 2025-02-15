package com.parker.hotkey.data.mapper

import com.naver.maps.geometry.LatLng
import com.parker.hotkey.data.local.entity.MarkerEntity
import com.parker.hotkey.domain.model.Marker

fun MarkerEntity.toDomain(): Marker {
    return Marker(
        id = id,
        position = LatLng(latitude, longitude),
        geohash = geohash,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        syncedAt = syncedAt,
        version = version
    )
}

fun Marker.toEntity(): MarkerEntity {
    return MarkerEntity(
        id = id,
        geohash = geohash,
        latitude = position.latitude,
        longitude = position.longitude,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        syncedAt = syncedAt,
        syncState = com.parker.hotkey.data.local.entity.SyncState.PENDING,
        version = version
    )
} 