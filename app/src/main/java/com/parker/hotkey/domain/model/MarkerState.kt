package com.parker.hotkey.domain.model

/**
 * 마커의 상태를 표현하는 열거형
 * 
 * TEMPORARY: 임시 마커 (서버에 저장되지 않음)
 * PERSISTED: 영구 저장된 마커 (서버에 저장됨)
 * DELETED: 삭제된 마커 (로컬/서버 동기화 필요)
 */
enum class MarkerState {
    TEMPORARY,   // 임시 마커 (서버에 저장되지 않음)
    PERSISTED,   // 영구 저장된 마커 (서버에 저장됨)
    DELETED      // 삭제된 마커 (로컬/서버 동기화 필요)
} 