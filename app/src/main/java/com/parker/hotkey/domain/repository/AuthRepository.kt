package com.parker.hotkey.domain.repository

import com.parker.hotkey.data.model.AuthToken
import com.parker.hotkey.util.Result

interface AuthRepository {
    suspend fun login(kakaoToken: String): Result<AuthToken>
    suspend fun refreshToken(): Result<AuthToken>
    suspend fun logout(): Result<Unit>
    suspend fun isLoggedIn(): Boolean
} 