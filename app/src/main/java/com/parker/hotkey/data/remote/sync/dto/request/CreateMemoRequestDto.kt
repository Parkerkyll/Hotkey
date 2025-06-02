package com.parker.hotkey.data.remote.sync.dto.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 메모 생성 요청 DTO 클래스
 * API 설계 문서에 맞게 최소 필드만 포함
 */
@JsonClass(generateAdapter = true)
data class CreateMemoRequestDto(
    @Json(name = "id") val id: String,
    @Json(name = "userId") val userId: String,
    @Json(name = "markerId") val markerId: String,
    @Json(name = "content") val content: String
) 