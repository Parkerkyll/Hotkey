package com.parker.hotkey.data.remote.sync.interceptor

import com.parker.hotkey.data.remote.network.ConnectionStateMonitor
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * 동기화 요청에 대한 네트워크 상태 확인 및 오류 처리를 담당하는 인터셉터
 */
class SyncInterceptor @Inject constructor(
    private val connectionStateMonitor: ConnectionStateMonitor
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // 네트워크 상태 확인
        if (!connectionStateMonitor.isConnected()) {
            throw NoConnectivityException()
        }
        
        val request = chain.request()
        return chain.proceed(request)
    }
    
    /**
     * 네트워크 연결 없음 예외
     */
    class NoConnectivityException : Exception("네트워크 연결이 없습니다. 인터넷 연결을 확인해주세요.")
}