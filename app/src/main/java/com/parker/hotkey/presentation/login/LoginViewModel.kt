package com.parker.hotkey.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parker.hotkey.data.model.AuthToken
import com.parker.hotkey.data.remote.util.ApiRequestManager
import com.parker.hotkey.di.ServiceLocator
import com.parker.hotkey.domain.repository.AuthRepository
import com.parker.hotkey.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Initial)
    val loginState: StateFlow<LoginState> = _loginState

    /**
     * 자동 로그인 상태를 체크하고 필요시 토큰을 갱신합니다.
     */
    fun checkAutoLogin() {
        Timber.d("자동 로그인 체크 시작")
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                Timber.d("로그인 상태 확인 중...")
                
                // 토큰이 있는지 & 만료되지 않았는지 체크
                if (!authRepository.isLoggedIn()) {
                    Timber.d("로그인 상태가 아님 - 로그인 화면으로 이동")
                    _loginState.value = LoginState.NotLoggedIn
                    return@launch
                }

                Timber.d("로그인 상태 확인됨 - 토큰 갱신 시도")
                // 토큰 갱신 시도
                when (val result = authRepository.refreshToken()) {
                    is Result.Success<AuthToken> -> {
                        Timber.d("토큰 갱신 성공: ${result.data.accessToken.take(10)}...")
                        _loginState.value = LoginState.Success(result.data)
                        // 토큰 갱신 성공 시 API 요청 캐시 초기화
                        clearApiRequestCache()
                    }
                    is Result.Failure -> {
                        Timber.e(result.exception, "토큰 갱신 실패 - 로그인 화면으로 이동")
                        // 토큰 갱신 실패 시 로그아웃 처리
                        authRepository.logout()
                        _loginState.value = LoginState.Error(result.exception)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "자동 로그인 체크 중 오류 발생")
                // 예외 발생 시 로그아웃 처리
                authRepository.logout()
                _loginState.value = LoginState.Error(e)
            }
        }
    }
    
    /**
     * 앱이 포그라운드로 돌아올 때 토큰 유효성을 다시 확인합니다.
     */
    fun checkTokenOnForeground() {
        Timber.d("앱 포그라운드 전환: 토큰 유효성 확인")
        viewModelScope.launch {
            try {
                // 현재 로그인 상태가 아니면 확인 불필요
                if (_loginState.value !is LoginState.Success) {
                    return@launch
                }
                
                // 토큰 유효성 확인
                if (!authRepository.isLoggedIn()) {
                    Timber.d("포그라운드 전환 시 토큰 만료 확인 - 로그인 화면으로 이동")
                    _loginState.value = LoginState.NotLoggedIn
                }
            } catch (e: Exception) {
                Timber.e(e, "포그라운드 전환 시 토큰 확인 오류")
                _loginState.value = LoginState.Error(e)
            }
        }
    }
    
    /**
     * API 요청 캐시를 초기화합니다.
     */
    private fun clearApiRequestCache() {
        viewModelScope.launch {
            try {
                val apiRequestManager = ServiceLocator.getApiRequestManager()
                apiRequestManager.clearRequestCache()
                Timber.d("토큰 갱신 후 API 요청 캐시 초기화 완료")
            } catch (e: Exception) {
                Timber.e(e, "토큰 갱신 후 API 요청 캐시 초기화 실패: ${e.message}")
            }
        }
    }
    
    /**
     * 로그인 상태를 나타내는 sealed class
     */
    sealed class LoginState {
        object Initial : LoginState()
        object Loading : LoginState()
        object NotLoggedIn : LoginState()
        data class Success(val token: AuthToken) : LoginState()
        data class Error(val exception: Throwable) : LoginState()
    }
} 