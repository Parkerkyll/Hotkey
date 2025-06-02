package com.parker.hotkey.data.remote.sync.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 마커 DTO 클래스
 */
@JsonClass(generateAdapter = true)
data class MarkerDto(
    @Json(name = "id") val id: String,
    @Json(name = "userId") val userId: String? = null,
    @Json(name = "geohash") val geohash: String,
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "modifiedAt") val modifiedAt: Long
) 