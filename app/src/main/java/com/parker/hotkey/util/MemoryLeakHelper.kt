package com.parker.hotkey.util

import android.app.Activity
import android.app.Dialog
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import timber.log.Timber
import java.lang.reflect.Field

/**
 * 메모리 누수를 방지하기 위한 유틸리티 클래스
 * - CoordinatorLayout의 OnPreDrawListener 메모리 누수 방지
 * - ThreadObject 메모리 누수 방지
 * - FragmentManager 관련 메모리 누수 방지
 * - Activity 및 View 관련 메모리 누수 방지
 */
object MemoryLeakHelper {

    /**
     * CoordinatorLayout의 OnPreDrawListener 참조로 인한 메모리 누수를 방지합니다.
     * 이 문제는 CoordinatorLayout 내부에서 ViewTreeObserver의 OnPreDrawListener를 등록하지만
     * 제대로 제거하지 않아 발생하는 문제입니다.
     *
     * @param coordinatorLayout 메모리 누수를 방지할 CoordinatorLayout
     * @param lifecycleOwner LifecycleOwner(일반적으로 Fragment)
     */
    fun preventCoordinatorLayoutLeak(coordinatorLayout: CoordinatorLayout, lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                try {
                    // 리플렉션을 사용하여 mOnPreDrawListener 제거
                    val fieldName = "mOnPreDrawListener"
                    val field = CoordinatorLayout::class.java.getDeclaredField(fieldName)
                    field.isAccessible = true
                    
                    val listener = field.get(coordinatorLayout) as? ViewTreeObserver.OnPreDrawListener
                    listener?.let {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            // Android 10 이상
                            coordinatorLayout.viewTreeObserver.removeOnPreDrawListener(it)
                        } else {
                            // Android 9 이하
                            try {
                                coordinatorLayout.viewTreeObserver.takeIf { !it.isAlive }?.let { observer -> 
                                    observer.removeOnPreDrawListener(it)
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Coordinator PreDrawListener 제거 실패")
                            }
                        }
                    }
                    
                    // null로 설정하여 참조 해제
                    field.set(coordinatorLayout, null)
                    
                    Timber.d("CoordinatorLayout mOnPreDrawListener 참조 해제 성공")
                } catch (e: Exception) {
                    Timber.e(e, "CoordinatorLayout 메모리 누수 방지 실패")
                }
                
                // 옵저버 제거
                lifecycleOwner.lifecycle.removeObserver(this)
            }
        })
    }
    
    /**
     * 핸들러 객체의 메모리 누수를 방지합니다.
     * 리플렉션을 사용하여 핸들러 참조를 제거합니다.
     *
     * @param threadName 제거할 쓰레드 이름
     */
    fun cleanupHandlerThreads(threadName: String) {
        try {
            // ThreadGroup에서 활성 쓰레드 가져오기
            val rootGroup = Thread.currentThread().threadGroup
            var threads = arrayOfNulls<Thread>(rootGroup?.activeCount() ?: 0)
            rootGroup?.enumerate(threads)
            
            // 지정된 이름의 쓰레드 찾기
            threads.filterNotNull()
                .filter { it.name.contains(threadName, ignoreCase = true) }
                .forEach { thread ->
                    try {
                        // mHandler 필드 찾기
                        val handleField = thread.javaClass.getDeclaredField("mHandler")
                        handleField.isAccessible = true
                        handleField.set(thread, null)
                        Timber.d("Thread '$threadName' 핸들러 참조 해제 성공")
                    } catch (e: Exception) {
                        Timber.e(e, "Thread 핸들러 참조 해제 실패: ${thread.name}")
                    }
                }
        } catch (e: Exception) {
            Timber.e(e, "HandlerThread 정리 중 오류 발생")
        }
    }
    
    /**
     * 뷰 객체의 리스너를 제거합니다.
     *
     * @param view 리스너를 제거할 뷰
     */
    fun clearViewListeners(view: View?) {
        if (view == null) return
        
        try {
            // 텍스트뷰 정적 참조 제거 (어디서든 호출 가능하도록)
            clearAllStaticTextViewReferences()
            
            // 클릭 리스너 제거
            view.setOnClickListener(null)
            
            // 터치 리스너 제거
            view.setOnTouchListener(null)
            
            // 롱클릭 리스너 제거
            view.setOnLongClickListener(null)
            
            // ViewTreeObserver 리스너 제거
            val observer = view.viewTreeObserver
            if (observer.isAlive) {
                observer.removeOnPreDrawListener(null)
                observer.removeOnGlobalLayoutListener(null)
                observer.removeOnScrollChangedListener(null)
            }
            
            // 포커스 제거
            view.clearFocus()
            
            // 애니메이션 제거
            view.clearAnimation()
            
            // 하위 뷰도 처리
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    clearViewListeners(view.getChildAt(i))
                }
                
                // 중요: ViewGroup의 mFocused 필드 정리 (메모리 누수의 주요 원인)
                try {
                    val viewGroupClass = ViewGroup::class.java
                    val mFocusedField = viewGroupClass.getDeclaredField("mFocused")
                    mFocusedField.isAccessible = true
                    mFocusedField.set(view, null)
                } catch (e: Exception) {
                    Timber.e(e, "ViewGroup.mFocused 필드 정리 중 오류 발생")
                }
            }
            
            Timber.v("뷰 리스너 정리 완료: ${view.javaClass.simpleName}")
        } catch (e: Exception) {
            Timber.e(e, "뷰 리스너 정리 중 오류 발생")
        }
    }
    
    /**
     * TextView 관련 모든 정적 필드를 제거하여 메모리 누수를 방지합니다.
     * 특히 mLastHoveredView는 메모리 누수의 주요 원인입니다.
     */
    fun clearAllStaticTextViewReferences() {
        try {
            val textViewClass = android.widget.TextView::class.java
            
            // 메모리 누수의 주요 원인이 되는 호버 관련 필드를 직접 처리
            val hoveredFields = listOf("mLastHoveredView", "sLastHoveredView")
            
            // 호버 필드 우선 처리
            hoveredFields.forEach { fieldName ->
                try {
                    val field = textViewClass.getDeclaredField(fieldName)
                    field.isAccessible = true
                    val oldValue = field.get(null)
                    field.set(null, null)
                    val success = field.get(null) == null
                    Timber.d("TextView $fieldName 정리 ${if(success) "성공" else "실패"} (이전 값: $oldValue)")
                } catch (e: Exception) {
                    Timber.e(e, "TextView $fieldName 정리 중 오류 발생")
                }
            }
            
            // 알려진 문제가 있는 정적 필드 목록
            val problematicFields = listOf(
                "mLastHoveredView",
                "sLastHoveredView",
                "sPrecomputedText",
                "sTemp",
                "EMPTY_DRAWABLE_STATE",
                "CHANGE_WATCHER_ARRAY",
                "SELECTED_STATE_SET"
            )
            
            // 모든 정적 필드를 찾아서 처리 (이미 처리한 호버 필드도 포함)
            textViewClass.declaredFields
                .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) }
                .forEach { field ->
                    field.isAccessible = true
                    try {
                        // 특히 View 타입이거나 알려진 문제 필드인 경우 null로 설정
                        val value = field.get(null)
                        if (value is View || problematicFields.contains(field.name)) {
                            field.set(null, null)
                            val success = field.get(null) == null
                            if (success) {
                                Timber.d("TextView 정적 필드 제거 완료: ${field.name}")
                            } else {
                                Timber.w("TextView 정적 필드 제거 실패 (null로 설정했으나 여전히 값이 있음): ${field.name}")
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "TextView 정적 필드 제거 중 오류 발생: ${field.name}")
                    }
                }
            
            // 강제 GC 요청 없이 자동 정리에 맡김
            Timber.d("모든 TextView 정적 필드 제거 완료")
        } catch (e: Exception) {
            Timber.e(e, "TextView 정적 필드 제거 중 오류 발생")
        }
    }
    
    /**
     * Dagger Hilt 컴포넌트의 메모리 누수를 정리합니다.
     * 특히 DaggerHotKeyApplication_HiltComponents 관련 누수를 처리합니다.
     */
    fun cleanupDaggerHiltComponents() {
        try {
            // 싱글톤 컴포넌트 찾기
            val classLoader = Thread.currentThread().contextClassLoader
            val singletonComponentClass = classLoader?.loadClass("com.parker.hotkey.DaggerHotKeyApplication_HiltComponents\$SingletonC")
            
            // 싱글톤 인스턴스 찾기
            val instanceField = singletonComponentClass?.getDeclaredField("INSTANCE")
            instanceField?.isAccessible = true
            val singletonInstance = instanceField?.get(null)
            
            if (singletonInstance != null) {
                // 싱글톤 컴포넌트의 바인딩 맵 찾기
                val mapFields = singletonComponentClass.declaredFields
                    .filter { it.type.name.contains("Map") }
                    .filter { !it.name.contains("INSTANCE") }
                
                // 각 맵 필드를 빈 맵으로 대체하거나 null로 설정
                mapFields.forEach { field ->
                    field.isAccessible = true
                    try {
                        val map = field.get(singletonInstance)
                        if (map != null && map is MutableMap<*, *>) {
                            map.clear()
                        } else {
                            field.set(singletonInstance, null)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Dagger 컴포넌트 필드 정리 중 오류 발생: ${field.name}")
                    }
                }
                
                // 싱글톤 컴포넌트의 모든 프로바이더 필드 정리
                val providerFields = singletonComponentClass.declaredFields
                    .filter { it.type.name.contains("Provider") || it.name.contains("provider") }
                
                providerFields.forEach { field ->
                    field.isAccessible = true
                    try {
                        field.set(singletonInstance, null)
                    } catch (e: Exception) {
                        Timber.e(e, "Dagger 프로바이더 필드 정리 중 오류 발생: ${field.name}")
                    }
                }
                
                Timber.d("Dagger Hilt 컴포넌트 정리 완료")
            }
        } catch (e: Exception) {
            Timber.e(e, "Dagger Hilt 컴포넌트 정리 중 오류 발생")
        } finally {
            // 메모리 정리 명시적 제안
            // System.gc() // Hotkey App Optimization: Removed explicit GC call
        }
    }
    
    /**
     * Fragment 관련 메모리 누수를 방지합니다.
     * FragmentManager 내부에 저장된 BackStackRecords, mFragmentStore 등의 참조를 정리합니다.
     *
     * @param fragmentManager 정리할 FragmentManager
     */
    fun cleanupFragmentManager(fragmentManager: FragmentManager?) {
        if (fragmentManager == null) return
        
        try {
            // FragmentManager의 private 필드들 접근
            val fields = FragmentManager::class.java.declaredFields
            
            // BackStack 관련 필드 찾기 및 정리
            fields.filter { it.name.contains("BackStack", ignoreCase = true) }
                .forEach { field ->
                    field.isAccessible = true
                    val value = field.get(fragmentManager)
                    if (value is MutableCollection<*>) {
                        value.clear()
                        Timber.d("FragmentManager ${field.name} 정리 완료")
                    }
                }
            
            // FragmentStore 관련 필드 찾기 및 정리
            fields.filter { it.name.contains("FragmentStore", ignoreCase = true) }
                .forEach { field ->
                    field.isAccessible = true
                    try {
                        val fragmentStore = field.get(fragmentManager)
                        fragmentStore?.let { store ->
                            val storeFields = store.javaClass.declaredFields
                            storeFields.forEach { storeField ->
                                storeField.isAccessible = true
                                val storeValue = storeField.get(store)
                                if (storeValue is MutableCollection<*>) {
                                    storeValue.clear()
                                } else if (storeValue is MutableMap<*, *>) {
                                    storeValue.clear()
                                }
                            }
                        }
                        Timber.d("FragmentManager ${field.name} 정리 완료")
                    } catch (e: Exception) {
                        Timber.e(e, "FragmentStore 정리 중 오류 발생: ${field.name}")
                    }
                }
            
            Timber.d("FragmentManager 정리 완료")
        } catch (e: Exception) {
            Timber.e(e, "FragmentManager 정리 중 오류 발생")
        }
    }
    
    /**
     * Activity 내의 View 및 리스너 참조로 인한 메모리 누수를 방지합니다.
     * Window.Callback, View.AttachInfo 등의 참조를 정리합니다.
     *
     * @param activity 정리할 Activity
     */
    fun cleanupActivityReferences(activity: Activity?) {
        if (activity == null) return
        
        try {
            // 활동의 모든 View에 대해 리스너 정리
            val decorView = activity.window?.decorView
            if (decorView != null) {
                clearViewListeners(decorView)
                
                // API 34 이상에서는 mAttachInfo 필드 접근 대신 대체 방법 사용
                if (Build.VERSION.SDK_INT < 34) {
                    try {
                        // decorView의 AttachInfo 정리 (API 33 이하만 사용)
                        val attachInfoField = View::class.java.getDeclaredField("mAttachInfo")
                        attachInfoField.isAccessible = true
                        attachInfoField.set(decorView, null)
                        Timber.d("View AttachInfo 참조 정리 완료 (리플렉션)")
                    } catch (e: Exception) {
                        Timber.w("AttachInfo 정리 중 오류 발생", e)
                    }
                } else {
                    // API 34 이상에서 대체 방법 사용
                    cleanupAttachInfoAlternative(decorView)
                }
                
                // Window.Callback 정리
                activity.window?.callback = null
                
                Timber.d("Activity Window/View 참조 정리 완료")
            }
            
            // FragmentActivity인 경우 FragmentManager 정리
            if (activity is FragmentActivity) {
                cleanupFragmentManager(activity.supportFragmentManager)
            }
            
            Timber.d("Activity 참조 정리 완료")
        } catch (e: Exception) {
            Timber.e(e, "Activity 참조 정리 중 오류 발생")
        }
    }
    
    /**
     * API 34 이상에서 AttachInfo를 정리하기 위한 대체 메서드
     * 공식 API를 사용하여 유사한 효과를 얻습니다.
     * 
     * @param view 정리할 뷰
     */
    private fun cleanupAttachInfoAlternative(view: View) {
        try {
            // View의 리스너 제거
            view.cancelPendingInputEvents()
            view.clearFocus()
            
            // 가능한 경우 뷰 계층 전체에 대해 정리 수행
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    view.getChildAt(i)?.let { child ->
                        cleanupAttachInfoAlternative(child)
                    }
                }
            }
            
            // 뷰 애니메이션 정리
            view.clearAnimation()
            
            // API 24 이상에서 리스너 정리
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {}
                    override fun onViewDetachedFromWindow(v: View) {
                        v.removeOnAttachStateChangeListener(this)
                    }
                })
            }
            
            Timber.d("View AttachInfo 참조 정리 완료 (대체 방법)")
        } catch (e: Exception) {
            Timber.e(e, "AttachInfo 대체 정리 중 오류 발생")
        }
    }
    
    /**
     * OkHttp 관련 메모리 누수를 방지합니다.
     * OkHttp의 ConnectionPool, Dispatcher 등의 참조를 정리합니다.
     */
    fun cleanupOkHttpReferences() {
        try {
            // OkHttp 클래스 로드
            val classLoader = Thread.currentThread().contextClassLoader
            val okHttpClientClass = classLoader?.loadClass("okhttp3.OkHttpClient")
            
            // OkHttpClient의 static 필드 찾기
            okHttpClientClass?.declaredFields
                ?.filter { java.lang.reflect.Modifier.isStatic(it.modifiers) }
                ?.forEach { field ->
                    field.isAccessible = true
                    try {
                        // Dispatcher, ConnectionPool 등의 정적 참조 정리
                        val value = field.get(null)
                        // Dispatcher 정리
                        if (field.name.contains("Dispatcher", ignoreCase = true)) {
                            val dispatcherClass = classLoader.loadClass("okhttp3.Dispatcher")
                            val cancelField = dispatcherClass.getDeclaredMethod("cancelAll")
                            cancelField.invoke(value)
                        }
                        // ConnectionPool 정리
                        else if (field.name.contains("ConnectionPool", ignoreCase = true)) {
                            val poolClass = classLoader.loadClass("okhttp3.ConnectionPool")
                            val evictMethod = poolClass.getDeclaredMethod("evictAll")
                            evictMethod.invoke(value)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "OkHttp 참조 정리 중 오류 발생: ${field.name}")
                    }
                }
            
            Timber.d("OkHttp 참조 정리 완료")
        } catch (e: Exception) {
            Timber.e(e, "OkHttp 참조 정리 중 오류 발생")
        }
    }
    
    /**
     * Dialog와 관련된 ViewRootImpl 참조를 정리합니다.
     * 메인 스레드 메시지 큐에 남아있는 메시지로 인한 메모리 누수를 방지합니다.
     *
     * @param dialog 정리할 Dialog
     */
    fun clearDialogReferences(dialog: Dialog?) {
        if (dialog == null) return
        
        try {
            // 1. 애니메이션 중지
            dialog.window?.decorView?.clearAnimation()
            
            // 2. Window 콜백 제거
            dialog.window?.callback = null
            
            // 3. TextView 정적 필드 정리 (메모리 누수의 주요 원인)
            clearAllStaticTextViewReferences()
            
            // 4. 다이얼로그의 모든 View에서 리스너 제거
            dialog.window?.decorView?.let { clearViewListeners(it) }
            
            // 5. 메인 핸들러의 메시지 큐에서 관련 메시지 제거
            val mainHandler = Handler(Looper.getMainLooper())
            mainHandler.removeCallbacksAndMessages(null)
            
            // 6. MessageQueue 정리 시도
            try {
                val queueField = mainHandler.javaClass.getDeclaredField("mQueue")
                queueField.isAccessible = true
                val queue = queueField.get(mainHandler)
                
                if (queue != null) {
                    // MessageQueue에서 현재 메시지 가져오기
                    val messageQueueClass = queue.javaClass
                    val messagesField = messageQueueClass.getDeclaredField("mMessages")
                    messagesField.isAccessible = true
                    
                    // 모든 메시지 제거 (특정 대화상자와 관련된 메시지만 제거하기 어려움)
                    // 대신 모든 메시지를 제거하고 필수 메시지는 곧 다시 예약됨
                    messagesField.set(queue, null)
                    
                    Timber.d("MessageQueue 정리 완료")
                }
            } catch (e: Exception) {
                Timber.e(e, "메시지 큐 정리 중 오류 발생")
            }
            
            // 7. ViewRootImpl 참조 제거
            dialog.window?.decorView?.let { decorView ->
                try {
                    // DecorView의 mKeyedTags 제거
                    val viewClass = View::class.java
                    val mKeyedTagsField = viewClass.getDeclaredField("mKeyedTags")
                    mKeyedTagsField.isAccessible = true
                    mKeyedTagsField.set(decorView, null)
                    
                    // OnClickListener 제거 (중요!)
                    clearViewOnClickListenersRecursively(decorView)
                    
                    // AttachInfo 필드 접근
                    val attachInfoField = viewClass.getDeclaredField("mAttachInfo")
                    attachInfoField.isAccessible = true
                    val attachInfo = attachInfoField.get(decorView)
                    
                    if (attachInfo != null) {
                        // ViewRootImpl 참조 제거
                        val attachInfoClass = attachInfo.javaClass
                        val viewRootImplField = attachInfoClass.getDeclaredField("mViewRootImpl")
                        viewRootImplField.isAccessible = true
                        viewRootImplField.set(attachInfo, null)
                        
                        // 다른 참조도 제거
                        attachInfoClass.declaredFields.forEach { field ->
                            field.isAccessible = true
                            val value = field.get(attachInfo)
                            if (value is View || value is Handler || field.name == "mViewRootImpl") {
                                field.set(attachInfo, null)
                            }
                        }
                    }
                    
                    // 모든 하위 뷰의 포커스 제거
                    clearFocusRecursively(decorView)
                    
                    Timber.d("Dialog ViewRootImpl 참조 제거 완료")
                } catch (e: Exception) {
                    Timber.e(e, "ViewRootImpl 참조 제거 중 오류 발생")
                }
            }
            
            Timber.d("Dialog 참조 정리 완료")
        } catch (e: Exception) {
            Timber.e(e, "Dialog 참조 정리 중 오류 발생")
        }
    }
    
    /**
     * View와 모든 하위 View의 포커스를 재귀적으로 제거합니다.
     */
    private fun clearFocusRecursively(view: View) {
        view.clearFocus()
        
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                clearFocusRecursively(child)
            }
        }
    }
    
    /**
     * View와 모든 하위 View의 클릭 리스너를 재귀적으로 제거합니다.
     */
    private fun clearViewOnClickListenersRecursively(view: View) {
        // 클릭 리스너 제거
        view.setOnClickListener(null)
        
        // 기타 리스너 제거
        view.setOnLongClickListener(null)
        view.setOnTouchListener(null)
        
        // 포커스 제거
        view.clearFocus()
        view.isFocusable = false
        
        // 하위 뷰에 대해 재귀적으로 처리
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                clearViewOnClickListenersRecursively(child)
            }
        }
    }
} 