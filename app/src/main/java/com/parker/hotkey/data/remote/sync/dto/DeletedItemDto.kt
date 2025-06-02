package com.parker.hotkey.data.remote.sync.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 삭제된 항목(마커, 메모) 정보를 표현하는 DTO 클래스
 */
@JsonClass(generateAdapter = true)
data class DeletedItemDto(
    @Json(name = "id") val id: String,
    @Json(name = "type") val type: String,  // "MARKER" 또는 "MEMO"
    @Json(name = "deletedAt") val deletedAt: Long,
    @Json(name = "geohash") val geohash: String? = null,
    @Json(name = "markerId") val markerId: String? = null  // 메모인 경우에만 사용
) 