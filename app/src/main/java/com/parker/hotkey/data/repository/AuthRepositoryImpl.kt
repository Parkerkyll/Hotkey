package com.parker.hotkey.data.repository

import com.parker.hotkey.data.model.AuthToken
import com.parker.hotkey.domain.manager.TokenManager
import com.parker.hotkey.domain.repository.AuthRepository
import com.parker.hotkey.util.Result
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val tokenManager: TokenManager
) : AuthRepository {
    
    override suspend fun login(kakaoToken: String): Result<AuthToken> {
        return try {
            // 카카오 토큰을 AuthToken 형태로 변환하여 저장
            val authToken = AuthToken(
                accessToken = kakaoToken,
                refreshToken = kakaoToken,  // 카카오 토큰을 refresh token으로도 사용
                expiresIn = 21600L  // 6시간 (카카오 토큰 기본 만료 시간)
            )
            
            tokenManager.saveTokens(
                accessToken = authToken.accessToken,
                refreshToken = authToken.refreshToken,
                expiresIn = authToken.expiresIn
            )
            
            Result.Success(authToken)
        } catch (e: Exception) {
            Timber.e(e, "Login failed")
            Result.Failure(e)
        }
    }

    override suspend fun refreshToken(): Result<AuthToken> {
        return try {
            val refreshToken = tokenManager.getRefreshToken()
                ?: return Result.Failure(Exception("No refresh token found"))
            
            // 실제로는 카카오 SDK를 통해 토큰 갱신
            val authToken = AuthToken(
                accessToken = refreshToken,
                refreshToken = refreshToken,
                expiresIn = 21600L
            )
            
            tokenManager.updateAccessToken(authToken.accessToken)
            Result.Success(authToken)
        } catch (e: Exception) {
            Timber.e(e, "Token refresh failed")
            Result.Failure(e)
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            tokenManager.clearTokens()
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Logout failed")
            Result.Failure(e)
        }
    }

    override suspend fun isLoggedIn(): Boolean {
        return tokenManager.hasValidTokens() && !tokenManager.isTokenExpired()
    }
} 