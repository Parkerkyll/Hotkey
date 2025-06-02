package com.parker.hotkey.di

import android.content.Context
import com.parker.hotkey.BuildConfig
import com.parker.hotkey.data.remote.network.ConnectionStateMonitor
import com.parker.hotkey.data.remote.network.SyncConfig
import com.parker.hotkey.data.remote.sync.api.HotkeyApi
import com.parker.hotkey.data.remote.sync.interceptor.ApiCallMonitorInterceptor
import com.parker.hotkey.data.remote.sync.interceptor.AuthInterceptor
import com.parker.hotkey.data.remote.sync.interceptor.SyncInterceptor
import com.parker.hotkey.data.remote.sync.util.SyncApiLogger
import com.parker.hotkey.data.repository.SyncRepositoryImpl
import com.parker.hotkey.domain.repository.AuthRepository
import com.parker.hotkey.domain.repository.SyncRepository
import com.parker.hotkey.domain.util.AppStateManager
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * 네트워크 관련 의존성 주입을 위한 Dagger Hilt 모듈
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    /**
     * Moshi 인스턴스 제공
     * KSP에 의해 생성된 JsonAdapter 클래스들이 자동으로 사용됩니다.
     */
    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .build()
    }
    
    /**
     * ApiCallMonitorInterceptor 인스턴스 제공
     * API 호출의 요청 및 응답을 상세하게 로깅합니다.
     * 네비게이션 복귀 상태 확인을 위해 AppStateManager에 의존합니다.
     */
    @Provides
    @Singleton
    fun provideApiCallMonitorInterceptor(
        appStateManager: AppStateManager
    ): ApiCallMonitorInterceptor {
        return ApiCallMonitorInterceptor(appStateManager)
    }
    
    /**
     * OkHttpClient 인스턴스 제공
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        syncInterceptor: SyncInterceptor,
        apiCallMonitorInterceptor: ApiCallMonitorInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            // API 호출 모니터링 인터셉터를 맨 앞에 추가 (요청 시작부터 응답까지 모니터링)
            .addInterceptor(apiCallMonitorInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(syncInterceptor)
            .addInterceptor(SyncApiLogger.createLoggingInterceptor(BuildConfig.DEBUG))
            .connectTimeout(SyncConfig.CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(SyncConfig.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(SyncConfig.WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }
    
    /**
     * Retrofit 인스턴스 제공
     */
    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(SyncConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
    
    /**
     * HotkeyApi 인스턴스 제공
     */
    @Provides
    @Singleton
    fun provideHotkeyApi(retrofit: Retrofit): HotkeyApi {
        return retrofit.create(HotkeyApi::class.java)
    }
    
    /**
     * ConnectionStateMonitor 인스턴스 제공
     */
    @Provides
    @Singleton
    fun provideConnectionStateMonitor(
        @ApplicationContext context: Context
    ): ConnectionStateMonitor {
        return ConnectionStateMonitor(context)
    }
    
    /**
     * SyncRepository 인스턴스 제공
     */
    @Provides
    @Singleton
    fun provideSyncRepository(
        repository: SyncRepositoryImpl
    ): SyncRepository {
        return repository
    }
} 