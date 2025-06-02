package com.parker.hotkey.data.remote.sync.dto.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 마커 삭제 요청 DTO 클래스
 * 마커 ID와 함께 geohash 정보도 함께 전송합니다.
 * 사용자 인증을 위해 해시된 userId도 포함합니다.
 */
@JsonClass(generateAdapter = true)
data class DeleteMarkerRequestDto(
    @Json(name = "id") val id: String,
    @Json(name = "userId") val userId: String,
    @Json(name = "geohash") val geohash: String? = null
) 