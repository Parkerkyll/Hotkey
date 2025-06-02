package com.parker.hotkey.data.remote.sync.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 메모 API DTO 클래스
 */
@JsonClass(generateAdapter = true)
data class MemoDto(
    @Json(name = "id") val id: String,
    @Json(name = "markerId") val markerId: String,
    @Json(name = "content") val content: String,
    @Json(name = "modifiedAt") val modifiedAt: Long
) 