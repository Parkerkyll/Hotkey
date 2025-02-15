package com.parker.hotkey.domain.model

import com.naver.maps.geometry.LatLng

data class Marker(
    val id: String,
    val position: LatLng,
    val geohash: String,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val syncedAt: Long? = null,
    val version: Long = 1
) 