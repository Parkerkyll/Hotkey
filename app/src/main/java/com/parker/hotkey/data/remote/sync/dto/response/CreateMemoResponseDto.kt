package com.parker.hotkey.data.remote.sync.dto.response

import com.parker.hotkey.data.remote.sync.dto.MemoDto
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 메모 생성 응답 DTO 클래스
 */
@JsonClass(generateAdapter = true)
data class CreateMemoResponseDto(
    @Json(name = "success") val success: Boolean,
    @Json(name = "memo") val memo: MemoDto
) 