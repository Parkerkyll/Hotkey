package com.parker.hotkey

import android.app.Application
import android.os.Looper
import android.os.MessageQueue
import androidx.multidex.MultiDex
import androidx.multidex.MultiDexApplication
import androidx.work.Configuration
import com.kakao.sdk.common.KakaoSdk
import com.naver.maps.map.NaverMapSdk
import com.parker.hotkey.BuildConfig
import com.parker.hotkey.data.manager.UserPreferencesManager
import com.parker.hotkey.domain.util.AppStateManager
import com.parker.hotkey.work.ApiSyncWorker
import com.parker.hotkey.domain.manager.LocationTracker
import com.parker.hotkey.data.remote.util.ApiRequestManager
import com.parker.hotkey.di.ServiceLocator
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject
import androidx.hilt.work.HiltWorkerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 앱 Application 클래스
 * Hilt DI를 위한 엔트리포인트
 */
@HiltAndroidApp
class HotKeyApplication : MultiDexApplication(), Configuration.Provider {

    @Inject
    lateinit var userPreferencesManager: UserPreferencesManager
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    @Inject
    lateinit var appStateManager: AppStateManager
    
    @Inject
    lateinit var locationTracker: LocationTracker
    
    // 네이버 지도 SDK 초기화 여부 추적
    private val isNaverMapSdkInitialized = AtomicBoolean(false)
    
    // IdleHandler 참조 유지
    private val idleHandler = MessageQueue.IdleHandler {
        Timber.d("IdleHandler 호출됨: UI 스레드 유휴 상태")
        // 필요한 백그라운드 초기화 작업 수행
        
        true // true 반환 시 계속 유지됨
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Timber 초기화
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.d("앱 초기화 시작")
        }
        
        // ServiceLocator 초기화
        ServiceLocator.init(this)
        Timber.d("ServiceLocator 초기화 완료")
        
        // AppStateManager 등록
        registerActivityLifecycleCallbacks(appStateManager)
        Timber.d("AppStateManager 등록 완료")
        
        // 앱 버전 체크 및 업데이트 감지
        val currentVersion = BuildConfig.VERSION_CODE
        val isAppUpdated = appStateManager.checkIfAppUpdated(currentVersion)
        if (isAppUpdated) {
            Timber.d("앱 업데이트가 감지되었습니다. 버전: $currentVersion")
            
            // 백그라운드에서 위치 정보 가져오고 ApiSyncWorker 실행
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // API 요청 캐시 초기화 추가
                    try {
                        val apiRequestManager = ServiceLocator.getApiRequestManager()
                        apiRequestManager.clearRequestCache()
                        Timber.d("앱 업데이트 후 API 요청 캐시 초기화 완료")
                    } catch (e: Exception) {
                        Timber.e(e, "앱 업데이트 후 API 요청 캐시 초기화 실패")
                    }
                    
                    // 위치 추적기 초기화 (필요시)
                    if (!locationTracker.initialized.value) {
                        locationTracker.initialize()
                        Timber.d("앱 업데이트 이후 LocationTracker 초기화 완료")
                    }
                    
                    // 현재 위치의 geohash 가져오기
                    val currentGeohash = locationTracker.currentGeohash.value
                    
                    if (currentGeohash != null) {
                        // ApiSyncWorker를 통해 백그라운드에서만 데이터 동기화
                        Timber.d("앱 업데이트 후 WorkManager를 통한 백그라운드 동기화 시작: $currentGeohash")
                        ApiSyncWorker.schedulePostUpdateSync(applicationContext, currentGeohash)
                    } else {
                        Timber.w("현재 위치 정보를 얻을 수 없어 업데이트 후 동기화를 진행할 수 없습니다.")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "업데이트 후 백그라운드 동기화 설정 중 오류 발생")
                }
            }
        } else {
            // 현재 버전 저장 (최초 설치 시에도 필요)
            appStateManager.saveCurrentAppVersion(currentVersion)
        }
        
        // MessageQueue 메모리 누수 방지를 위한 IdleHandler 등록
        setupMessageQueueIdleHandler()
        
        // 앱 설치일 저장
        userPreferencesManager.saveInstallDateIfNotExists()
        Timber.d("앱 설치일 확인 완료")
        
        // 테스트용 카카오 데이터 설정
        if (userPreferencesManager.getKakaoId() == null) {
            userPreferencesManager.saveKakaoUserInfo(
                "user@kakao.com", 
                "카카오 사용자", 
                "https://k.kakaocdn.net/dn/dpk9l1/btqmGhA2lKL/Oz0wDuJn1YV2DIn92f6DVK/img_640x640.jpg"
            )
            Timber.d("테스트용 카카오 사용자 정보 저장 완료")
        }
        
        // 카카오 SDK 초기화
        KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        Timber.d("카카오 SDK 초기화 완료")
        
        // 네이버 지도 SDK는 지연 초기화로 변경 (실제 사용 시점에 초기화)
        Timber.d("네이버 지도 SDK 지연 초기화 설정 완료")
    }
    
    /**
     * 네이버 지도 SDK 초기화 (필요한 시점에 호출)
     * 최소한의 필수 기능만 활성화하여 용량 최적화
     */
    fun initializeNaverMapSdkIfNeeded() {
        // 이미 초기화되었는지 확인 (멀티스레드 환경 고려)
        if (isNaverMapSdkInitialized.get()) {
            Timber.d("네이버 지도 SDK가 이미 초기화됨")
            return
        }
        
        // 아직 초기화되지 않은 경우에만 초기화 (스레드 안전 방식)
        if (isNaverMapSdkInitialized.compareAndSet(false, true)) {
            try {
                // 네이버 지도 SDK 초기화 - 최소 설정으로 
                @Suppress("DEPRECATION")
                NaverMapSdk.getInstance(this).apply {
                    client = NaverMapSdk.NaverCloudPlatformClient(BuildConfig.NAVER_CLIENT_ID)
                    // 필요한 최소 기능만 초기화하도록 설정
                    // 캐시 크기를 직접 설정하는 대신 SDK 기본값 사용
                }
                Timber.d("네이버 지도 SDK 초기화 완료 (지연 로딩)")
            } catch (e: Exception) {
                Timber.e(e, "네이버 지도 SDK 초기화 중 오류 발생")
                // 오류 발생 시 초기화 상태 리셋
                isNaverMapSdkInitialized.set(false)
            }
        }
    }
    
    /**
     * MessageQueue 메모리 누수 방지를 위한 설정
     * IdleHandler를 등록하여 유휴 시간에 메모리 정리 유도
     */
    private fun setupMessageQueueIdleHandler() {
        try {
            Looper.myQueue().addIdleHandler(idleHandler)
            Timber.d("IdleHandler 등록 완료")
        } catch (e: Exception) {
            Timber.e(e, "IdleHandler 등록 중 오류 발생")
        }
    }

    override fun onTerminate() {
        try {
            Timber.d("앱 종료 시작")
            
            // MessageQueue IdleHandler 제거
            Looper.myQueue().removeIdleHandler(idleHandler)
            
            // AppStateManager 해제
            unregisterActivityLifecycleCallbacks(appStateManager)
            
            Timber.d("앱 종료 리소스 정리 완료")
        } catch (e: Exception) {
            Timber.e(e, "앱 종료 리소스 정리 중 오류 발생")
        } finally {
            super.onTerminate()
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
} 