package com.parker.hotkey

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.kakao.sdk.common.KakaoSdk
import com.naver.maps.map.NaverMapSdk
import com.parker.hotkey.data.local.HotKeyDatabase
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class HotKeyApplication : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    override fun onCreate() {
        super.onCreate()
        
        // Timber 초기화
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        // 앱 데이터 완전 삭제
        clearAllAppData()
        
        // 카카오 SDK 초기화
        KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        
        // 네이버 지도 SDK 초기화
        NaverMapSdk.getInstance(this).client = 
            NaverMapSdk.NaverCloudPlatformClient(BuildConfig.NAVER_CLIENT_ID)
    }

    private fun clearAllAppData() {
        try {
            // 데이터베이스 삭제
            HotKeyDatabase.deleteDatabase(this)
            
            // SharedPreferences 삭제
            getSharedPreferences("app_settings", MODE_PRIVATE).edit().clear().apply()
            
            // 캐시 삭제
            cacheDir.deleteRecursively()
            
            // 앱 내부 저장소 파일 삭제
            filesDir.deleteRecursively()
            
            // WorkManager 작업 취소
            androidx.work.WorkManager.getInstance(this).cancelAllWork()
            
            Timber.d("모든 앱 데이터가 삭제되었습니다.")
        } catch (e: Exception) {
            Timber.e(e, "앱 데이터 삭제 중 오류 발생")
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        // 데이터베이스 인스턴스 정리
        HotKeyDatabase.destroyInstance()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
} 