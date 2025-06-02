package com.parker.hotkey.presentation.base

import android.content.Context
import android.view.View
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import com.parker.hotkey.util.LifecycleAware
import java.lang.ref.WeakReference

/**
 * 모든 UI 컨트롤러의 기본 인터페이스
 * UI 컨트롤러 초기화 및 상태 확인을 위한 공통 메소드를 정의합니다.
 */
interface UIController {
    /**
     * UI 컨트롤러가 초기화되었는지 확인합니다
     * @return 초기화 여부
     */
    fun isInitialized(): Boolean
    
    /**
     * UI 컨트롤러 초기화
     * @param rootView 루트 뷰
     */
    fun initialize(rootView: View)
    
    /**
     * 리소스 정리
     */
    fun cleanup()
}

/**
 * UI 컨트롤러의 기본 구현 클래스
 * 초기화 상태 추적 및 에러 처리 등의 공통 기능을 제공합니다.
 * 
 * @param context 애플리케이션 컨텍스트
 */
abstract class BaseUIController(protected val context: Context) : UIController, LifecycleAware {
    
    // 초기화 상태 추적
    private val initialized = AtomicBoolean(false)
    
    // 루트 뷰 참조 - WeakReference로 변경
    protected var rootViewRef: WeakReference<View>? = null
    protected val rootView: View?
        get() = rootViewRef?.get()
    
    // UI 컴포넌트 맵 - 키-값 형태로 UI 요소 관리
    protected val uiComponents = mutableMapOf<String, View>()
    
    /**
     * UI 컨트롤러 초기화 여부 확인
     * @return 초기화 여부
     */
    override fun isInitialized(): Boolean {
        return initialized.get()
    }
    
    /**
     * UI 컨트롤러 초기화
     * @param rootView 루트 뷰
     */
    override fun initialize(rootView: View) {
        if (initialized.get()) {
            Timber.d("${javaClass.simpleName}가 이미 초기화되어 있습니다.")
            return
        }
        
        try {
            this.rootViewRef = WeakReference(rootView)
            
            // 자식 클래스별 초기화 수행
            onInitialize(rootView)
            
            // 초기화 완료 표시
            initialized.set(true)
            Timber.d("${javaClass.simpleName} 초기화 완료")
        } catch (e: Exception) {
            Timber.e(e, "${javaClass.simpleName} 초기화 실패")
            throw e
        }
    }
    
    /**
     * 하위 클래스에서 구현할 초기화 메서드
     * @param rootView 루트 뷰
     */
    protected abstract fun onInitialize(rootView: View)
    
    /**
     * UI 컴포넌트 바인딩 헬퍼 메서드
     * @param id 뷰 ID
     * @param key 컴포넌트 맵에 저장할 키
     * @throws IllegalStateException 뷰를 찾을 수 없는 경우
     */
    protected fun <T : View> bindView(id: Int, key: String): T {
        val view = rootView?.findViewById<T>(id)
            ?: throw IllegalStateException("ID $id 에 해당하는 뷰를 찾을 수 없습니다")
        
        uiComponents[key] = view
        return view
    }
    
    /**
     * UI 컴포넌트 맵 반환
     * @return UI 컴포넌트 맵의 불변 복사본
     */
    fun getUIComponents(): Map<String, View> {
        // 초기화 상태 확인
        if (!isInitialized()) {
            Timber.e("${javaClass.simpleName}가 초기화되지 않았습니다. UI 컴포넌트를 가져올 수 없습니다.")
            return emptyMap()
        }
        
        return uiComponents.toMap() // 불변 복사본 반환
    }
    
    /**
     * 리소스 정리 메서드
     */
    override fun cleanup() {
        uiComponents.clear()
        rootViewRef?.clear() // WeakReference 해제
        rootViewRef = null
        initialized.set(false)
        
        // 자식 클래스별 정리 작업 수행
        onCleanup()
        
        Timber.d("${javaClass.simpleName} 정리 완료")
    }
    
    /**
     * 하위 클래스에서 구현할 정리 메서드
     */
    protected open fun onCleanup() {
        // 기본 구현은 비어있음 - 필요한 경우 하위 클래스에서 오버라이드
    }
    
    /**
     * UI 컴포넌트 참조를 명시적으로 해제하는 메서드
     * 메모리 누수 방지를 위해 사용
     */
    protected fun clearComponentReferences() {
        try {
            uiComponents.clear()
            rootViewRef?.clear() // WeakReference 해제
            rootViewRef = null
            Timber.d("${javaClass.simpleName} 컴포넌트 참조 해제 완료")
        } catch (e: Exception) {
            Timber.e(e, "컴포넌트 참조 해제 중 오류 발생")
        }
    }
    
    /**
     * UI 상태 변경을 안전하게 처리하는 헬퍼 메서드
     * @param action UI 상태 변경을 수행할 람다
     */
    protected fun safeUpdateUI(action: () -> Unit) {
        try {
            if (isInitialized()) {
                action()
            } else {
                Timber.e("${javaClass.simpleName}가 초기화되지 않았습니다. UI 업데이트를 수행할 수 없습니다.")
            }
        } catch (e: Exception) {
            Timber.e(e, "${javaClass.simpleName} UI 업데이트 중 오류 발생")
        }
    }
    
    /**
     * 컴포넌트 시작 시 호출됩니다.
     * 자식 클래스에서 필요한 리소스 초기화나 작업 재개를 구현할 수 있습니다.
     */
    override fun onStart() {
        Timber.d("${javaClass.simpleName} onStart 호출됨")
    }
    
    /**
     * 컴포넌트 중지 시 호출됩니다.
     * 자식 클래스에서 리소스 해제나 작업 중단을 구현할 수 있습니다.
     */
    override fun onStop() {
        Timber.d("${javaClass.simpleName} onStop 호출됨")
    }
    
    /**
     * 컴포넌트 파괴 시 호출됩니다.
     * 자식 클래스에서 모든 리소스 정리를 구현해야 합니다.
     * 기본적으로 cleanup() 메서드를 호출합니다.
     */
    override fun onDestroy() {
        Timber.d("${javaClass.simpleName} onDestroy 호출됨")
        cleanup()
    }
} 