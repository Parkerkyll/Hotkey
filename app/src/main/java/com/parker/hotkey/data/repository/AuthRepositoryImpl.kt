package com.parker.hotkey.data.repository

import com.parker.hotkey.data.model.AuthToken
import com.parker.hotkey.data.manager.UserPreferencesManager
import com.parker.hotkey.data.remote.util.ApiRequestManager
import com.parker.hotkey.di.ServiceLocator
import com.parker.hotkey.domain.manager.TokenManager
import com.parker.hotkey.domain.repository.AuthRepository
import com.parker.hotkey.util.HashUtil
import com.parker.hotkey.util.Result
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.kakao.sdk.user.UserApiClient
import com.kakao.sdk.user.model.User
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.auth.AuthApiClient
import java.util.Date

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val tokenManager: TokenManager,
    private val userPreferencesManager: UserPreferencesManager
) : AuthRepository {
    
    override suspend fun login(kakaoToken: String): Result<AuthToken> {
        return try {
            // 카카오 토큰을 AuthToken 형태로 변환하여 저장
            val authToken = AuthToken(
                accessToken = kakaoToken,
                refreshToken = kakaoToken,  // 카카오 토큰을 refresh token으로도 사용
                expiresIn = 43200L  // 12시간 (카카오 토큰 기본 만료 시간 확장)
            )
            
            tokenManager.saveTokens(
                accessToken = authToken.accessToken,
                refreshToken = authToken.refreshToken,
                expiresIn = authToken.expiresIn
            )
            
            // 로그인 성공 후 API 요청 캐시 초기화
            try {
                val apiRequestManager = ServiceLocator.getApiRequestManager()
                apiRequestManager.clearRequestCache()
                Timber.d("로그인 성공 후 API 요청 캐시 초기화 완료")
            } catch (e: Exception) {
                Timber.e(e, "로그인 성공 후 API 요청 캐시 초기화 실패")
            }
            
            Result.Success(authToken)
        } catch (e: Exception) {
            Timber.e(e, "Login failed")
            Result.Failure(e)
        }
    }

    override suspend fun refreshToken(): Result<AuthToken> {
        return try {
            // 기존 리프레시 토큰이 없으면 에러 반환
            val refreshToken = tokenManager.getRefreshToken()
                ?: return Result.Failure(Exception("No refresh token found"))
            
            // 카카오 SDK를 통해 토큰 갱신 (suspendCancellableCoroutine으로 콜백 처리)
            val newToken = suspendCancellableCoroutine<OAuthToken> { continuation ->
                Timber.d("카카오 토큰 갱신 시작")
                // 토큰 갱신이 필요한지 확인하고 필요하면 갱신
                if (AuthApiClient.instance.hasToken()) {
                    UserApiClient.instance.accessTokenInfo { _, error ->
                        if (error != null) {
                            // 토큰 만료 등의 이유로 오류 발생 시 로그인 필요
                            Timber.e(error, "토큰 정보 확인 실패")
                            continuation.resumeWithException(error)
                        } else {
                            // 토큰이 유효하면 현재 토큰 사용
                            // 만료 시간 설정 (현재 시간 + 6시간)
                            val currentTime = Date()
                            val accessTokenExpiresAt = Date(currentTime.time + 6L * 60L * 60L * 1000L) // 6시간 후
                            val refreshTokenExpiresAt = Date(currentTime.time + 30L * 24L * 60L * 60L * 1000L) // 30일 후
                            
                            val currentToken = OAuthToken(
                                accessToken = tokenManager.getAccessToken() ?: "",
                                refreshToken = refreshToken,
                                accessTokenExpiresAt = accessTokenExpiresAt,
                                refreshTokenExpiresAt = refreshTokenExpiresAt,
                                scopes = listOf()
                            )
                            Timber.d("현재 토큰이 유효함: ${currentToken.accessToken.take(10)}...")
                            continuation.resume(currentToken)
                        }
                    }
                } else {
                    // 토큰이 없으면 오류 반환
                    Timber.e("카카오 토큰이 없음")
                    continuation.resumeWithException(Exception("카카오 토큰이 없습니다"))
                }
                
                continuation.invokeOnCancellation {
                    Timber.d("토큰 갱신 코루틴 취소됨")
                }
            }
            
            // 새로 확인한 토큰으로 AuthToken 생성
            val authToken = AuthToken(
                accessToken = newToken.accessToken,
                refreshToken = newToken.refreshToken.takeIf { !it.isNullOrEmpty() } 
                    ?: tokenManager.getRefreshToken().takeIf { !it.isNullOrEmpty() } 
                    ?: "",
                expiresIn = 21600L  // 카카오 토큰 기본 만료 시간 (6시간)
            )
            
            // 새 토큰 정보 저장
            tokenManager.saveTokens(
                accessToken = authToken.accessToken,
                refreshToken = authToken.refreshToken,
                expiresIn = authToken.expiresIn
            )
            
            Result.Success(authToken)
        } catch (e: Exception) {
            Timber.e(e, "Token refresh failed")
            // 토큰 만료 시 로컬 토큰 정보도 클리어
            tokenManager.clearTokens()
            Result.Failure(e)
        }
    }

    override suspend fun withdraw(): Result<Unit> {
        return try {
            // 카카오 서비스에서 연결 끊기 (suspendCancellableCoroutine으로 콜백 처리)
            suspendCancellableCoroutine<Unit> { continuation ->
                UserApiClient.instance.unlink { error ->
                    if (error != null) {
                        Timber.e(error, "카카오 연결 해제 실패")
                        continuation.resumeWithException(error)
                    } else {
                        Timber.d("카카오 연결 해제 성공")
                        continuation.resume(Unit)
                    }
                }
                
                continuation.invokeOnCancellation {
                    Timber.d("회원 탈퇴 코루틴 취소됨")
                }
            }
            
            // 로컬 토큰 정보 삭제
            tokenManager.clearTokens()
            
            // 사용자 정보 삭제
            userPreferencesManager.clearKakaoUserInfo()
            
            // 캐시된 사용자 ID 초기화
            cachedUserId = null
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "회원 탈퇴 실패")
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

    private var cachedUserId: String? = null

    override suspend fun getUserId(): String = suspendCancellableCoroutine { continuation ->
        try {
            // 캐시된 사용자 ID가 있으면 반환
            cachedUserId?.let { return@suspendCancellableCoroutine continuation.resume(it) }

            UserApiClient.instance.me { user: User?, error: Throwable? ->
                when {
                    error != null -> {
                        continuation.resumeWithException(error)
                    }
                    user != null -> {
                        val userId = user.id.toString()
                        cachedUserId = userId
                        continuation.resume(userId)
                    }
                    else -> {
                        continuation.resumeWithException(IllegalStateException("사용자 정보를 가져올 수 없습니다."))
                    }
                }
            }
            
            continuation.invokeOnCancellation {
                // 코루틴이 취소되었을 때 특별한 정리 작업이 필요 없음
                Timber.d("getUserId 코루틴이 취소됨")
            }
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }

    private var cachedHashedUserId: String? = null

    override suspend fun getHashedUserId(): String {
        val userId = getUserId()
        // 캐시된 해시 ID가 있으면 반환
        cachedHashedUserId?.let { return it }
        
        // 해시 ID 계산 및 캐싱
        val hashedId = HashUtil.hashKakaoId(userId)
        cachedHashedUserId = hashedId
        return hashedId
    }
} 