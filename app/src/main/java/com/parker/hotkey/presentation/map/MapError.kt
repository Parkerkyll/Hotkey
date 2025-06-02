package com.parker.hotkey.presentation.map

sealed class MapError(val message: String) {
    class LocationError(message: String) : MapError(message)
    class NetworkError(message: String) : MapError(message)
    class WriteModeLocked(message: String) : MapError(message)
    class UnknownError(message: String) : MapError(message)
    class PermissionError(message: String) : MapError(message)
    class GenericError(message: String) : MapError(message)
    class MarkerLoadingError(message: String) : MapError(message)
    
    companion object {
        fun fromException(e: Throwable): MapError {
            return when (e) {
                is SecurityException -> PermissionError(e.message ?: "권한이 없습니다")
                is IllegalStateException -> WriteModeLocked(e.message ?: "쓰기 모드로 전환해주세요")
                else -> UnknownError(e.message ?: "알 수 없는 오류가 발생했습니다")
            }
        }
    }
} 