package com.parker.hotkey.domain.constants

/**
 * 메모 관련 상수들을 관리하는 객체
 *
 * 주요 기능:
 * - 메모 갯수 제한 관련 상수 관리
 * - 메모 표시 관련 상수 관리
 */
object MemoConstants {
    /** 마커당 최대 메모 갯수 */
    const val MAX_MEMO_COUNT = 10
    
    /** UI에 표시할 최대 메모 갯수 */
    const val MAX_VISIBLE_MEMO_COUNT = 10
    
    /** 메모 초과 시 표시할 메시지 */
    const val MEMO_LIMIT_EXCEEDED_MESSAGE = "메모는 마커당 최대 10개까지 작성할 수 있습니다."
} 