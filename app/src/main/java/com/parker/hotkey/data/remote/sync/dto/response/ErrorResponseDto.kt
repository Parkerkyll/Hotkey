package com.parker.hotkey.data.remote.sync.dto.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * API 오류 응답 DTO 클래스
 */
@JsonClass(generateAdapter = true)
data class ErrorResponseDto(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "message") val message: String,
    @Json(name = "errorCode") val errorCode: String? = null
) 