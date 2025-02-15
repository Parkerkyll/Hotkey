package com.parker.hotkey.data.model

data class AuthToken(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long
) 