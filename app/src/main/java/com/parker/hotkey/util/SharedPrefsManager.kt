package com.parker.hotkey.util

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SharedPreferences를 사용하여 앱의 설정 및 상태를 관리하는 클래스
 */
@Singleton
class SharedPrefsManager @Inject constructor(
    @ApplicationContext context: Context
) {
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    
    // 콜백을 약한 참조로 저장
    private var preferenceChangedCallbacks: MutableMap<String, WeakReference<() -> Unit>> = mutableMapOf()
    
    /**
     * 설정 변경 콜백 등록
     * @param key 감시할 키
     * @param callback 설정 변경 시 호출될 콜백
     */
    fun registerPreferenceChangedCallback(key: String, callback: () -> Unit) {
        preferenceChangedCallbacks[key] = WeakReference(callback)
    }
    
    /**
     * 설정 변경 콜백 제거
     * @param key 콜백을 제거할 키
     */
    fun unregisterPreferenceChangedCallback(key: String) {
        preferenceChangedCallbacks.remove(key)
    }
    
    /**
     * 특정 키에 대한 콜백 호출
     * @param key 콜백을 호출할 키
     */
    private fun notifyPreferenceChanged(key: String) {
        preferenceChangedCallbacks[key]?.get()?.invoke()
    }
    
    /**
     * Long 값을 저장합니다.
     * 
     * @param key 저장할 키
     * @param value 저장할 값
     */
    fun setLongPreference(key: String, value: Long) {
        sharedPreferences.edit().putLong(key, value).apply()
        notifyPreferenceChanged(key)
    }
    
    /**
     * Long 값을 가져옵니다.
     * 
     * @param key 가져올 키
     * @param defaultValue 기본값
     * @return 저장된 값 또는 기본값
     */
    fun getLongPreference(key: String, defaultValue: Long): Long {
        return sharedPreferences.getLong(key, defaultValue)
    }
    
    /**
     * String 값을 저장합니다.
     * 
     * @param key 저장할 키
     * @param value 저장할 값
     */
    fun setStringPreference(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
        notifyPreferenceChanged(key)
    }
    
    /**
     * String 값을 가져옵니다.
     * 
     * @param key 가져올 키
     * @param defaultValue 기본값
     * @return 저장된 값 또는 기본값
     */
    fun getStringPreference(key: String, defaultValue: String): String {
        return sharedPreferences.getString(key, defaultValue) ?: defaultValue
    }
    
    /**
     * Boolean 값을 저장합니다.
     * 
     * @param key 저장할 키
     * @param value 저장할 값
     */
    fun setBooleanPreference(key: String, value: Boolean) {
        sharedPreferences.edit().putBoolean(key, value).apply()
        notifyPreferenceChanged(key)
    }
    
    /**
     * Boolean 값을 가져옵니다.
     * 
     * @param key 가져올 키
     * @param defaultValue 기본값
     * @return 저장된 값 또는 기본값
     */
    fun getBooleanPreference(key: String, defaultValue: Boolean): Boolean {
        return sharedPreferences.getBoolean(key, defaultValue)
    }
    
    /**
     * 특정 키에 해당하는 값을 삭제합니다.
     * 
     * @param key 삭제할 키
     */
    fun removePreference(key: String) {
        sharedPreferences.edit().remove(key).apply()
        notifyPreferenceChanged(key)
    }
    
    /**
     * SharedPreferences의 모든 값을 지웁니다.
     */
    fun clearAll() {
        sharedPreferences.edit().clear().apply()
        // 모든 콜백 알림
        preferenceChangedCallbacks.keys.forEach { notifyPreferenceChanged(it) }
    }
    
    /**
     * 리소스 정리
     */
    fun cleanup() {
        preferenceChangedCallbacks.clear()
    }
    
    companion object {
        private const val PREFS_NAME = "hotkey_prefs"
        
        // 마지막 동기화 시간 키 접두사
        const val LAST_SYNC_PREFIX = "last_sync_"
    }
} 