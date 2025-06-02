package com.parker.hotkey

import android.app.Application
import android.util.Log
import timber.log.Timber

/**
 * 테스트 전용 Application 클래스
 * 테스트 환경에서 필요한 초기화 작업을 수행합니다.
 */
class TestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 테스트 환경에서 Timber 초기화
        Timber.plant(object : Timber.DebugTree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                // 로그가 손실되지 않도록 표준 안드로이드 로깅으로도 출력
                when (priority) {
                    Log.VERBOSE -> Log.v(tag, message, t)
                    Log.DEBUG -> Log.d(tag, message, t)
                    Log.INFO -> Log.i(tag, message, t)
                    Log.WARN -> Log.w(tag, message, t)
                    Log.ERROR -> Log.e(tag, message, t)
                    Log.ASSERT -> Log.wtf(tag, message, t)
                }
            }
        })
        
        Log.d("TestApplication", "테스트 애플리케이션 초기화 완료")
        Timber.d("테스트 애플리케이션 초기화 완료")
    }
} 