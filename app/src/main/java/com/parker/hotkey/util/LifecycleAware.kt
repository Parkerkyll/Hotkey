package com.parker.hotkey.util

/**
 * 생명주기 인식 인터페이스
 * 이 인터페이스를 구현하는 클래스는 생명주기 이벤트에 반응할 수 있습니다.
 */
interface LifecycleAware {
    /**
     * 컴포넌트가 시작될 때 호출됩니다.
     */
    fun onStart() {}
    
    /**
     * 컴포넌트가 중지될 때 호출됩니다.
     */
    fun onStop() {}
    
    /**
     * 컴포넌트가 파괴될 때 호출됩니다.
     * 이 메서드에서 모든 리소스를 정리해야 합니다.
     */
    fun onDestroy() {}
} 