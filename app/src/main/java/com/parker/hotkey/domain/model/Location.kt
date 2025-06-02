package com.parker.hotkey.domain.model

data class Location(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val provider: String = "unknown" // 위치 정보 제공자 (network 또는 gps)
) 