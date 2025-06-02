package com.parker.hotkey

import timber.log.Timber

/**
 * 테스트용 Timber 로그 트리
 * 테스트 환경에서 로그를 콘솔에 출력합니다.
 */
class TestTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // 테스트 시에는 콘솔에만 출력
        println("[$tag] $message")
        t?.let { println("[$tag] Exception: ${it.message}") }
    }
} 