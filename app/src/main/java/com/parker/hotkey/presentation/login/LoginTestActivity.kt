package com.parker.hotkey.presentation.login

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.user.UserApiClient
import com.parker.hotkey.MainActivity
import com.parker.hotkey.R
import timber.log.Timber

class LoginTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_test)
        
        // Edge-to-edge 설정
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 카카오 로그인 버튼 설정
        findViewById<ImageButton>(R.id.btnKakaoLogin).setOnClickListener {
            handleKakaoLogin()
        }
    }

    private fun handleKakaoLogin() {
        // 카카오톡이 설치되어 있으면 카카오톡으로 로그인, 아니면 카카오계정으로 로그인
        if (UserApiClient.instance.isKakaoTalkLoginAvailable(this)) {
            UserApiClient.instance.loginWithKakaoTalk(this) { token, error ->
                handleLoginResult(token, error)
            }
        } else {
            UserApiClient.instance.loginWithKakaoAccount(this) { token, error ->
                handleLoginResult(token, error)
            }
        }
    }

    private fun handleLoginResult(token: OAuthToken?, error: Throwable?) {
        if (error != null) {
            Timber.e(error, "카카오 로그인 실패")
            Toast.makeText(this, "로그인에 실패했습니다: ${error.message}", Toast.LENGTH_SHORT).show()
        } else if (token != null) {
            Timber.d("카카오 로그인 성공: ${token.accessToken}")
            
            // 사용자 정보 요청
            UserApiClient.instance.me { user, error ->
                if (error != null) {
                    Timber.e(error, "사용자 정보 요청 실패")
                    Toast.makeText(this, "사용자 정보를 가져오는데 실패했습니다.", Toast.LENGTH_SHORT).show()
                } else if (user != null) {
                    Timber.d("사용자 정보 요청 성공: ${user.id}")
                    
                    // MainActivity로 이동
                    val intent = Intent(this, MainActivity::class.java).apply {
                        putExtra("user_id", user.id.toString())
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                }
            }
        }
    }
} 