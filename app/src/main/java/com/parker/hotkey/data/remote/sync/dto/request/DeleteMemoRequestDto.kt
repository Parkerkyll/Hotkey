package com.parker.hotkey.data.remote.sync.dto.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 메모 삭제 요청 DTO 클래스
 * 사용자 인증을 위해 해시된 userId도 포함합니다.
 */
@JsonClass(generateAdapter = true)
data class DeleteMemoRequestDto(
    @Json(name = "id") val id: String,
    @Json(name = "userId") val userId: String,
    @Json(name = "markerId") val markerId: String
) 