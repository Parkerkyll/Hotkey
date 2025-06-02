package com.parker.hotkey.data.remote.sync.dto.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 증분 동기화 요청을 위한 DTO 클래스
 * 1. geohash 기반 증분 동기화: lastSyncTimestamp 사용
 * 2. 개별 항목 기반 동기화: markerSyncInfo와 memoSyncInfo 사용
 */
@JsonClass(generateAdapter = true)
data class SyncRequestDto(
    @Json(name = "geohash") val geohash: String,
    @Json(name = "syncType") val syncType: String = "GEOHASH",  // "GEOHASH" 또는 "INDIVIDUAL"
    @Json(name = "markerSyncInfo") val markerSyncInfo: List<MarkerSyncInfo>? = null,
    @Json(name = "memoSyncInfo") val memoSyncInfo: List<MemoSyncInfo>? = null,
    @Json(name = "lastSyncTimestamp") val lastSyncTimestamp: Long? = null  // geohash 기반 동기화에 사용
)

/**
 * 마커 동기화 정보
 */
@JsonClass(generateAdapter = true)
data class MarkerSyncInfo(
    @Json(name = "id") val id: String,
    @Json(name = "lastSyncTimestamp") val lastSyncTimestamp: Long
)

/**
 * 메모 동기화 정보
 */
@JsonClass(generateAdapter = true)
data class MemoSyncInfo(
    @Json(name = "id") val id: String,
    @Json(name = "markerId") val markerId: String,
    @Json(name = "lastSyncTimestamp") val lastSyncTimestamp: Long
) 