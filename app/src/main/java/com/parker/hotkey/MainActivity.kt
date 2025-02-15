package com.parker.hotkey

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import timber.log.Timber
import com.parker.hotkey.presentation.login.LoginTestActivity
import com.parker.hotkey.domain.repository.AuthRepository
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var authRepository: AuthRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Edge-to-edge 설정
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun refreshTokenIfNeeded() {
        lifecycleScope.launch {
            try {
                val result = authRepository.refreshToken()
                if (!result.isSuccess) {
                    startLoginActivity()
                }
            } catch (e: Exception) {
                Timber.e(e, "토큰 갱신 실패")
                startLoginActivity()
            }
        }
    }
    
    private fun startLoginActivity() {
        val intent = Intent(this, LoginTestActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}