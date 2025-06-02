package com.parker.hotkey.domain.manager

interface TokenManager {
    suspend fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Long)
    fun saveAccessToken(token: String)
    fun saveRefreshToken(token: String)
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun clearTokens()
    suspend fun updateAccessToken(newToken: String)
    suspend fun hasValidTokens(): Boolean
    suspend fun isTokenExpired(): Boolean
} 