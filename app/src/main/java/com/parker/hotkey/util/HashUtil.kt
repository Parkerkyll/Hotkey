package com.parker.hotkey.util

import java.security.MessageDigest

object HashUtil {
    /**
     * 카카오 ID를 SHA-256 해시로 변환합니다.
     * 항상 동일한 입력에 대해 동일한 해시를 반환합니다.
     * 
     * @param kakaoId 변환할 카카오 ID 문자열
     * @return 16진수 문자열 형태의 해시값
     */
    fun hashKakaoId(kakaoId: String): String {
        val bytes = MessageDigest
            .getInstance("SHA-256")
            .digest(kakaoId.toByteArray())
        
        return bytes.joinToString("") { "%02x".format(it) }
    }
} 