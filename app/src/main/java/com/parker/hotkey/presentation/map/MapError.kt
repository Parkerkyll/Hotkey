package com.parker.hotkey.presentation.map

sealed class MapError(val message: String) {
    class PermissionError(message: String) : MapError(message)
    class LocationError(message: String) : MapError(message)
    class NetworkError(message: String) : MapError(message)
    class UnknownError(message: String) : MapError(message)
    
    companion object {
        fun fromException(e: Exception): MapError {
            return when (e) {
                is SecurityException -> PermissionError(e.message ?: "권한이 없습니다")
                else -> UnknownError(e.message ?: "알 수 없는 오류가 발생했습니다")
            }
        }
    }
} 