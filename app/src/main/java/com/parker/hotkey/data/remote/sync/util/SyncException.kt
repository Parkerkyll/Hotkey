package com.parker.hotkey.data.remote.sync.util

/**
 * 동기화 관련 예외 클래스
 */
sealed class SyncException(message: String) : Exception(message) {
    /**
     * 네트워크 연결 오류
     */
    class NetworkError(message: String = "네트워크 연결 오류가 발생했습니다.") : SyncException(message)
    
    /**
     * 서버 오류
     */
    class ServerError(message: String = "서버 오류가 발생했습니다.") : SyncException(message)
    
    /**
     * 리소스를 찾을 수 없음
     */
    class NotFoundError(message: String = "요청한 리소스를 찾을 수 없습니다.") : SyncException(message)
    
    /**
     * 마커를 찾을 수 없음 (특수 케이스)
     */
    class MarkerNotFoundError(message: String, val markerId: String? = null) : SyncException(message)
    
    /**
     * 잘못된 요청
     */
    class BadRequestError(message: String = "잘못된 요청입니다.") : SyncException(message)
    
    /**
     * 충돌 발생
     */
    class ConflictError(message: String = "데이터 충돌이 발생했습니다.") : SyncException(message)
    
    /**
     * 기타 오류
     */
    class UnknownError(message: String = "알 수 없는 오류가 발생했습니다.") : SyncException(message)
}