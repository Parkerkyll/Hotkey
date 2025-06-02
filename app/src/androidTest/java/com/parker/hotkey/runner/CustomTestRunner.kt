package com.parker.hotkey.runner

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import timber.log.Timber

/**
 * 안드로이드 인스트루먼테이션 테스트를 위한 커스텀 테스트 러너
 * TestApplication을 사용하고 로그를 초기화합니다.
 */
class CustomTestRunner : AndroidJUnitRunner() {
    
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application {
        // 표준 안드로이드 로그로 정보 출력
        android.util.Log.d("CustomTestRunner", "테스트 애플리케이션 생성 중")
        
        return super.newApplication(
            cl,
            "com.parker.hotkey.TestApplication",
            context
        )
    }
    
    override fun onStart() {
        android.util.Log.d("CustomTestRunner", "테스트 시작됨")
        
        // 테스트 시작 전에 Timber 초기화
        if (Timber.forest().isEmpty()) {
            Timber.plant(object : Timber.DebugTree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    android.util.Log.println(priority, "Timber[$tag]", message)
                }
            })
        }
        
        Timber.d("테스트 시작 - CustomTestRunner")
        super.onStart()
    }
} 