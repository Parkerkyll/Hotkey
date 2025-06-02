package com.parker.hotkey.data.remote.sync.dto.response

import com.parker.hotkey.data.remote.sync.dto.MarkerDto
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 마커 생성 응답 DTO 클래스
 */
@JsonClass(generateAdapter = true)
data class CreateMarkerResponseDto(
    @Json(name = "success") val success: Boolean,
    @Json(name = "marker") val marker: MarkerDto
) 