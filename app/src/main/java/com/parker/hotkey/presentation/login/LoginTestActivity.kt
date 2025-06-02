package com.parker.hotkey.presentation.login

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.user.UserApiClient
import com.parker.hotkey.MainActivity
import com.parker.hotkey.R
import com.parker.hotkey.data.manager.UserPreferencesManager
import com.parker.hotkey.domain.repository.AuthRepository
import com.parker.hotkey.util.Result
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject
import kotlinx.coroutines.launch
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.core.content.ContextCompat
import android.widget.TextView

@AndroidEntryPoint
class LoginTestActivity : AppCompatActivity() {
    @Inject
    lateinit var authRepository: AuthRepository
    
    @Inject
    lateinit var userPreferencesManager: UserPreferencesManager
    
    // 카카오 콜백 참조 저장
    private var kakaoLoginCallback: ((OAuthToken?, Throwable?) -> Unit)? = null
    private var userInfoCallback: ((com.kakao.sdk.user.model.User?, Throwable?) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_test)
        
        // Edge-to-edge 설정
        enableEdgeToEdge()
        
        // Edge-to-edge 설정 후 상태 표시줄 색상을 흰색으로 설정
        window.statusBarColor = ContextCompat.getColor(this, R.color.white)
        
        // 상태 표시줄 아이콘 색상을 어두운 색으로 설정 (흰색 배경에서 보이게)
        setStatusBarColor()
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 카카오 로그인 버튼 설정
        findViewById<ImageButton>(R.id.btnKakaoLogin).setOnClickListener {
            handleKakaoLogin()
        }
        
        // 콜백 초기화
        initCallbacks()
    }
    
    private fun initCallbacks() {
        // 카카오 로그인 콜백 초기화
        kakaoLoginCallback = { token, error ->
            handleLoginResult(token, error)
        }
        
        // 사용자 정보 요청 콜백 초기화
        userInfoCallback = { user, error ->
            if (error != null) {
                Timber.e(error, "사용자 정보 요청 실패")
                Toast.makeText(this, "사용자 정보를 가져오는데 실패했습니다.", Toast.LENGTH_SHORT).show()
            } else if (user != null) {
                Timber.d("사용자 정보 요청 성공: ${user.id}")
                
                // 카카오 사용자 정보 저장
                user.kakaoAccount?.let { account ->
                    val id = user.id.toString()
                    val nickname = account.profile?.nickname ?: "사용자"
                    val profileUrl = account.profile?.profileImageUrl
                    
                    // UserPreferencesManager에 카카오 정보 저장
                    userPreferencesManager.saveKakaoUserInfo(id, nickname, profileUrl)
                    Timber.d("카카오 사용자 정보 저장 완료: $id, $nickname")
                }
                
                // MainActivity로 이동
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("user_id", user.id)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
        }
    }

    private fun handleKakaoLogin() {
        // 저장된 콜백 사용
        val callback = kakaoLoginCallback ?: return
        
        // 카카오톡이 설치되어 있으면 카카오톡으로 로그인, 아니면 카카오계정으로 로그인
        if (UserApiClient.instance.isKakaoTalkLoginAvailable(this)) {
            UserApiClient.instance.loginWithKakaoTalk(this, callback = callback)
        } else {
            UserApiClient.instance.loginWithKakaoAccount(this, callback = callback)
        }
    }

    private fun handleLoginResult(token: OAuthToken?, error: Throwable?) {
        if (error != null) {
            Timber.e(error, "카카오 로그인 실패")
            Toast.makeText(this, "로그인에 실패했습니다: ${error.message}", Toast.LENGTH_SHORT).show()
        } else if (token != null) {
            Timber.d("카카오 로그인 성공: ${token.accessToken}")
            
            // 토큰 저장
            lifecycleScope.launch {
                try {
                    when (val result = authRepository.login(token.accessToken)) {
                        is Result.Success -> {
                            Timber.d("토큰 저장 성공")
                            // 사용자 정보 요청
                            requestUserInfo()
                        }
                        is Result.Failure -> {
                            Timber.e(result.exception, "토큰 저장 실패")
                            Toast.makeText(this@LoginTestActivity, "로그인 처리 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "토큰 저장 중 오류 발생")
                    Toast.makeText(this@LoginTestActivity, "로그인 처리 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun requestUserInfo() {
        // 저장된 콜백 사용
        val callback = userInfoCallback ?: return
        
        // 사용자 정보 요청
        UserApiClient.instance.me(callback = callback)
    }
    
    override fun onDestroy() {
        // 모든 콜백 참조 해제
        kakaoLoginCallback = null
        userInfoCallback = null
        
        // 버튼 리스너 해제
        findViewById<ImageButton>(R.id.btnKakaoLogin)?.setOnClickListener(null)
        
        // 윈도우 인셋 리스너 해제
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), null)
        
        // 메모리 릭 수정: tvPrivacyInfo의 모든 리스너 명시적으로 제거
        findViewById<TextView>(R.id.tvPrivacyInfo)?.let { textView ->
            textView.setOnTouchListener(null)
            textView.setOnHoverListener(null)
            textView.setOnLongClickListener(null)
            textView.setOnClickListener(null)
            textView.setOnDragListener(null)
            // TextView와 관련된 모든 리스너 제거
            textView.isFocusable = false
            textView.isHovered = false
            textView.isLongClickable = false
            // 핸들러 메시지 및 콜백 제거
            textView.handler?.removeCallbacksAndMessages(null)
        }
        
        // 뷰의 모든 핸들러 메시지와 콜백 제거
        findViewById<View>(R.id.main)?.let { 
            it.handler?.removeCallbacksAndMessages(null)
        }
        
        // MemoryLeakHelper를 사용하여 모든 뷰 리스너 정리
        // 이 부분은 이미 구현되어 있다면 유지, 없다면 다른 방식으로 구현
        try {
            com.parker.hotkey.util.MemoryLeakHelper.clearViewListeners(findViewById(R.id.main))
        } catch (e: Exception) {
            Timber.e(e, "MemoryLeakHelper 호출 중 오류 발생")
        }
        
        super.onDestroy()
    }

    private fun setStatusBarColor() {
        // 상태 바 색상 설정
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }
} 