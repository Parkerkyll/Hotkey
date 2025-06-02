package com.parker.hotkey.util

import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * 프래그먼트의 뷰 생명주기에 맞춰 자동으로 참조를 해제하는 프로퍼티 델리게이트
 * 이를 통해 View Binding 객체의 메모리 누수 문제를 해결합니다.
 */
class AutoClearedValue<T : Any>(val fragment: Fragment) : ReadWriteProperty<Fragment, T> {
    private var _value: T? = null
    
    init {
        fragment.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                fragment.viewLifecycleOwnerLiveData.observe(fragment) { viewLifecycleOwner ->
                    viewLifecycleOwner?.lifecycle?.addObserver(object : DefaultLifecycleObserver {
                        override fun onDestroy(owner: LifecycleOwner) {
                            _value = null
                        }
                    })
                }
            }
        })
    }
    
    override fun getValue(thisRef: Fragment, property: KProperty<*>): T {
        return _value ?: throw IllegalStateException(
            "AutoClearedValue 값에 접근하기 전에 초기화해야 합니다."
        )
    }
    
    override fun setValue(thisRef: Fragment, property: KProperty<*>, value: T) {
        _value = value
    }
}

/**
 * Fragment에서 AutoClearedValue 생성을 위한 확장 함수
 */
fun <T : Any> Fragment.autoCleared() = AutoClearedValue<T>(this) 