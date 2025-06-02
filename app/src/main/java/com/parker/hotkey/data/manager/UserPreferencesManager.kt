package com.parker.hotkey.data.manager

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 사용자 설정 및 정보를 관리하는 매니저 클래스
 */
@Singleton
class UserPreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) : DefaultLifecycleObserver {
    companion object {
        private const val PREF_NAME = "user_preferences"
        private const val KEY_INSTALL_DATE = "install_date"
        private const val KEY_KAKAO_ID = "kakao_id"
        private const val KEY_KAKAO_NICKNAME = "kakao_nickname"
        private const val KEY_KAKAO_PROFILE_URL = "kakao_profile_url"
        private const val KEY_HIDE_HELP_GUIDE = "hide_help_guide"
    }

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
    
    // 약한 참조로 Activity와 Fragment 참조 저장
    private var weakActivity: WeakReference<Activity>? = null
    private var weakFragment: WeakReference<Fragment>? = null
    
    // 약한 참조로 콜백 저장
    private var preferenceChangedCallbacks: MutableMap<String, WeakReference<() -> Unit>> = mutableMapOf()
    
    /**
     * Activity 참조 설정
     * @param activity 설정할 Activity 인스턴스
     */
    fun setActivity(activity: Activity) {
        this.weakActivity = WeakReference(activity)
        Timber.d("Activity 참조가 설정됨")
    }
    
    /**
     * Fragment 참조 설정 및 생명주기 관찰 설정
     * @param fragment 설정할 Fragment 인스턴스
     */
    fun setFragment(fragment: Fragment) {
        this.weakFragment = WeakReference(fragment)
        fragment.lifecycle.addObserver(this)
        Timber.d("Fragment 참조가 설정됨 및 생명주기 관찰 시작")
    }
    
    /**
     * 콜백 등록
     * @param key 설정 키
     * @param callback 설정 변경 시 호출될 콜백
     */
    fun registerPreferenceChangedCallback(key: String, callback: () -> Unit) {
        preferenceChangedCallbacks[key] = WeakReference(callback)
    }
    
    /**
     * 콜백 호출
     * @param key 설정 키
     */
    private fun notifyPreferenceChanged(key: String) {
        preferenceChangedCallbacks[key]?.get()?.invoke()
    }

    /**
     * 앱 설치일을 저장합니다. 이미 저장된 설치일이 있으면 저장하지 않습니다.
     */
    fun saveInstallDateIfNotExists() {
        if (!sharedPreferences.contains(KEY_INSTALL_DATE)) {
            val currentTimeMillis = System.currentTimeMillis()
            sharedPreferences.edit().putLong(KEY_INSTALL_DATE, currentTimeMillis).apply()
            Timber.d("앱 설치일 저장: ${formatDate(currentTimeMillis)}")
            notifyPreferenceChanged(KEY_INSTALL_DATE)
        }
    }

    /**
     * 앱 설치일을 가져옵니다. 설치일이 저장되어 있지 않은 경우 현재 시간을 저장하고 반환합니다.
     * @return 앱 설치일(밀리초)
     */
    fun getInstallDate(): Long {
        if (!sharedPreferences.contains(KEY_INSTALL_DATE)) {
            saveInstallDateIfNotExists()
        }
        return sharedPreferences.getLong(KEY_INSTALL_DATE, System.currentTimeMillis())
    }

    /**
     * 앱 설치일부터 현재까지의 일수를 계산합니다.
     * @return 설치 이후 경과 일수
     */
    fun getDaysSinceInstall(): Int {
        val installDate = getInstallDate()
        val currentTime = System.currentTimeMillis()
        
        val diffMillis = currentTime - installDate
        return TimeUnit.MILLISECONDS.toDays(diffMillis).toInt() + 1 // 당일 포함
    }

    /**
     * 카카오 계정 정보를 저장합니다.
     */
    fun saveKakaoUserInfo(id: String, nickname: String, profileUrl: String?) {
        sharedPreferences.edit()
            .putString(KEY_KAKAO_ID, id)
            .putString(KEY_KAKAO_NICKNAME, nickname)
            .putString(KEY_KAKAO_PROFILE_URL, profileUrl)
            .apply()
        
        // 콜백 알림
        notifyPreferenceChanged(KEY_KAKAO_ID)
        notifyPreferenceChanged(KEY_KAKAO_NICKNAME)
        notifyPreferenceChanged(KEY_KAKAO_PROFILE_URL)
    }

    /**
     * 카카오 계정 정보를 삭제합니다.
     */
    fun clearKakaoUserInfo() {
        sharedPreferences.edit()
            .remove(KEY_KAKAO_ID)
            .remove(KEY_KAKAO_NICKNAME)
            .remove(KEY_KAKAO_PROFILE_URL)
            .apply()
        Timber.d("카카오 사용자 정보가 삭제되었습니다.")
        
        // 콜백 알림
        notifyPreferenceChanged(KEY_KAKAO_ID)
        notifyPreferenceChanged(KEY_KAKAO_NICKNAME)
        notifyPreferenceChanged(KEY_KAKAO_PROFILE_URL)
    }

    /**
     * 카카오 아이디를 가져옵니다.
     */
    fun getKakaoId(): String? {
        return sharedPreferences.getString(KEY_KAKAO_ID, null)
    }

    /**
     * 카카오 닉네임을 가져옵니다.
     */
    fun getKakaoNickname(): String? {
        return sharedPreferences.getString(KEY_KAKAO_NICKNAME, null)
    }

    /**
     * 카카오 프로필 URL을 가져옵니다.
     */
    fun getKakaoProfileUrl(): String? {
        return sharedPreferences.getString(KEY_KAKAO_PROFILE_URL, null)
    }

    /**
     * 도움말 안내 표시 여부를 설정합니다.
     * @param hide true인 경우 도움말 안내를 표시하지 않습니다.
     */
    suspend fun setHideHelpGuide(hide: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_HIDE_HELP_GUIDE, hide).apply()
        Timber.d("도움말 안내 숨김 설정: $hide")
        notifyPreferenceChanged(KEY_HIDE_HELP_GUIDE)
    }

    /**
     * 도움말 안내 표시 여부를 가져옵니다.
     * @return true인 경우 도움말 안내를 표시하지 않습니다.
     */
    suspend fun getHideHelpGuide(): Boolean {
        return sharedPreferences.getBoolean(KEY_HIDE_HELP_GUIDE, false)
    }

    /**
     * 날짜를 포맷팅합니다.
     */
    private fun formatDate(timeMillis: Long): String {
        val date = Date(timeMillis)
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return format.format(date)
    }
    
    /**
     * 리소스 정리
     */
    fun cleanup() {
        Timber.d("UserPreferencesManager 리소스 정리 중")
        weakActivity = null
        weakFragment = null
        preferenceChangedCallbacks.clear()
    }
    
    // DefaultLifecycleObserver 구현
    
    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        Timber.d("소유자가 파괴됨, 참조 정리")
        
        // Fragment가 파괴되었을 때 참조 정리
        val fragment = weakFragment?.get()
        if (fragment != null && owner === fragment) {
            weakFragment = null
            Timber.d("Fragment 참조가 정리됨")
        }
    }
} 