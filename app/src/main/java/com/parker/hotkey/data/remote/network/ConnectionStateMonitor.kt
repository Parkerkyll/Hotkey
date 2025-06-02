package com.parker.hotkey.data.remote.network

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.fragment.app.Fragment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * 네트워크 연결 상태를 모니터링하는 클래스
 */
@Singleton
class ConnectionStateMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    // Activity와 Fragment 약한 참조
    private var weakActivity: WeakReference<Activity>? = null
    private var weakFragment: WeakReference<Fragment>? = null
    
    // 상태 변경 콜백 약한 참조
    private var connectionChangedCallback: WeakReference<((Boolean) -> Unit)>? = null
    
    /**
     * 애플리케이션 컨텍스트 반환
     * @return 애플리케이션 컨텍스트
     */
    fun getContext(): Context {
        return context
    }
    
    /**
     * Activity 참조 설정
     * @param activity 설정할 Activity 인스턴스
     */
    fun setActivity(activity: Activity) {
        this.weakActivity = WeakReference(activity)
        Timber.d("Activity 참조가 설정됨")
    }
    
    /**
     * Fragment 참조 설정
     * @param fragment 설정할 Fragment 인스턴스
     */
    fun setFragment(fragment: Fragment) {
        this.weakFragment = WeakReference(fragment)
        Timber.d("Fragment 참조가 설정됨")
    }
    
    /**
     * 네트워크 상태 변경 콜백 설정
     * @param callback 상태 변경 시 호출될 콜백
     */
    fun setConnectionChangedCallback(callback: (Boolean) -> Unit) {
        this.connectionChangedCallback = WeakReference(callback)
        // 초기 상태 즉시 알림
        callback(isConnected())
        Timber.d("연결 상태 콜백이 설정됨, 초기 상태: ${isConnected()}")
    }
    
    /**
     * 현재 네트워크 연결 상태를 확인
     * 
     * @return 연결 상태 (true: 연결됨, false: 연결 안됨)
     */
    fun isConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * 네트워크 연결 상태 변화를 Flow로 제공
     * 
     * @return 네트워크 연결 상태 Flow (true: 연결됨, false: 연결 안됨)
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeNetworkState(): Flow<Boolean> = callbackFlow {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Channel.trySend()는 실험적인 API이므로 @OptIn 사용
                trySend(true)
            }
            
            override fun onLost(network: Network) {
                trySend(false)
            }
            
            override fun onUnavailable() {
                trySend(false)
            }
        }
        
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        try {
            // 초기 상태 전송
            trySend(isConnected())
            
            // 안전하게 콜백 등록
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            
            // 채널이 닫힐 때 콜백 해제
            awaitClose { 
                try {
                    connectivityManager.unregisterNetworkCallback(networkCallback)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        Timber.d("네트워크 모니터링 취소됨")
                    } else {
                        Timber.w(e, "네트워크 콜백 해제 중 오류 발생")
                    }
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                Timber.d("네트워크 모니터링 시작 중 취소됨")
                throw e
            } else {
                Timber.e(e, "네트워크 모니터링 초기화 중 오류 발생")
                close(e)
            }
        }
    }
    
    /**
     * 간단한 네트워크 상태 변경 리스너 설정
     * observeNetworkState()보다 가벼운 대안
     */
    fun setupNetworkListener() {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                notifyConnectionChanged(true)
            }
            
            override fun onLost(network: Network) {
                notifyConnectionChanged(false)
            }
            
            override fun onUnavailable() {
                notifyConnectionChanged(false)
            }
        }
        
        try {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            
            // 연결 모니터링 시작
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            
            // 초기 상태 통지
            notifyConnectionChanged(isConnected())
        } catch (e: Exception) {
            Timber.e(e, "네트워크 모니터링 시작 중 오류 발생")
        }
    }
    
    /**
     * 저장된 콜백에 네트워크 상태 변경 알림
     */
    private fun notifyConnectionChanged(isConnected: Boolean) {
        connectionChangedCallback?.get()?.invoke(isConnected)
    }
    
    /**
     * 리소스 정리
     */
    fun cleanup() {
        Timber.d("ConnectionStateMonitor 리소스 정리 중")
        weakActivity = null
        weakFragment = null
        connectionChangedCallback = null
    }
} 