package com.parker.hotkey.data.remote.sync.dto.response

import com.parker.hotkey.data.remote.sync.dto.MarkerDto
import com.parker.hotkey.data.remote.sync.dto.MemoDto
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 지역 데이터 로딩 응답 DTO 클래스
 */
@JsonClass(generateAdapter = true)
data class GeohashResponseDto(
    @Json(name = "success") val success: Boolean,
    @Json(name = "serverTime") val serverTime: Long? = null,
    @Json(name = "lastSync") val lastSync: Long? = null,
    @Json(name = "markers") val markers: List<MarkerDto> = emptyList(),
    @Json(name = "memos") val memos: List<MemoDto> = emptyList()
) {
    /**
     * 서버 시간을 가져오는 함수
     * serverTime 또는 lastSync 중 하나가 있으면 해당 값을 반환하고,
     * 둘 다 없으면 현재 시간을 반환합니다.
     */
    fun getEffectiveServerTime(): Long {
        return serverTime ?: lastSync ?: System.currentTimeMillis()
    }
} 