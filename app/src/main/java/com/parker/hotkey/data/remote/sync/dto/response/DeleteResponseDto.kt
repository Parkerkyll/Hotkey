package com.parker.hotkey.data.remote.sync.dto.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 마커 및 메모 삭제 응답 DTO 클래스
 * API 문서에 따라 응답 형식이 다를 수 있으므로 필드를 유연하게 처리합니다.
 */
@JsonClass(generateAdapter = true)
data class DeleteResponseDto(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String,
    @Json(name = "deletedAt") val deletedAt: Long? = null,  // nullable로 변경
    @Json(name = "deletedMemos") val deletedMemos: Int? = null, // 이전 버전 호환성 유지
    @Json(name = "deletedMemoCount") val deletedMemoCount: Int? = null // 새로운 서버 응답 대응
) 