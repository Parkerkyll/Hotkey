package com.parker.hotkey.presentation.map.controllers

import android.content.Context
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraAnimation
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.NaverMap
import com.parker.hotkey.R
import com.parker.hotkey.domain.constants.MapConstants
import com.parker.hotkey.domain.constants.TimingConstants
import com.parker.hotkey.presentation.base.BaseUIController
import com.parker.hotkey.presentation.map.MapError
import com.parker.hotkey.presentation.map.MapFragment
import com.parker.hotkey.presentation.state.MapState
import com.parker.hotkey.util.dp2px
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import com.parker.hotkey.MainActivity
import java.lang.ref.WeakReference

@Singleton
class MapUIController @Inject constructor(
    @ApplicationContext context: Context
) : BaseUIController(context) {
    private lateinit var progressBar: ProgressBar
    private lateinit var modeText: TextView
    private lateinit var modeSwitch: SwitchMaterial
    private lateinit var modeBar: CardView
    private lateinit var menuIcon: View
    private lateinit var editModeTimer: TextView
    
    // onModeToggle 콜백을 약한 참조로 변경
    private var onModeToggle: ((Boolean) -> Unit)? = null
    
    // 이 컨트롤러가 활성화되어 있는지 여부를 추적
    private var isActive = false
    
    // 프로그래밍적 스위치 변경 중인지 표시하는 플래그
    private var isProgrammaticChange = false
    
    // 스위치 이벤트 처리 관련 변수 추가
    private var lastSwitchEventTime: Long = 0
    private var isProcessingUserEvent: Boolean = false
    private var isUserAction: Boolean = false
    
    /**
     * 모드 토글 콜백 설정
     * @param callback 모드 토글 콜백
     */
    fun setModeToggleCallback(callback: (Boolean) -> Unit) {
        Timber.d("[스위치 관리] 모드 토글 콜백 설정됨")
        onModeToggle = callback
        
        // 콜백 설정 후 리스너 재설정
        if (::modeSwitch.isInitialized) {
            setupSwitchListener()
        }
    }
    
    /**
     * UI 컴포넌트 초기화
     * BaseUIController 구현
     * @param rootView 루트 뷰
     */
    override fun onInitialize(rootView: View) {
        // UI 컴포넌트 바인딩
        progressBar = bindView(R.id.progress_bar, "progressBar")
        modeText = bindView(R.id.mode_text, "modeText")
        modeSwitch = bindView(R.id.mode_switch, "modeSwitch")
        modeBar = bindView(R.id.mode_bar, "modeBar")
        menuIcon = bindView(R.id.menu_icon, "menuIcon")
        editModeTimer = bindView(R.id.edit_mode_timer, "editModeTimer")
        
        // 모드 스위치 리스너 설정 - 단일 진입점으로 만들어 여기서만 관리
        setupSwitchListener()
        
        // 모드 바 클릭 이벤트 가로채기
        modeBar.setOnClickListener {
            // 클릭 이벤트 소비 (지도로 이벤트 전달 방지)
            Timber.d("모드 전환 바 클릭 이벤트 가로채기")
        }
        
        Timber.d("MapUIController 초기화 완료")
    }
    
    /**
     * 스위치 리스너 설정 - 완전히 재작성
     */
    private fun setupSwitchListener() {
        try {
            // 리스너 설정 전에 로그
            Timber.d("[스위치 리스너] 설정 시작: 현재=${if(modeSwitch.isChecked) "쓰기" else "읽기"}모드")
            
            // 기존 리스너 확실히 제거
            modeSwitch.setOnCheckedChangeListener(null)
            
            // 리스너 설정 전 마지막 처리 시간 초기화
            lastSwitchEventTime = 0L
            
            // 새 리스너 설정
            modeSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
                // 현재 시간 가져오기
                val currentTime = System.currentTimeMillis()
                
                // 프로그래밍적 변경 플래그 확인
                if (isProgrammaticChange) {
                    Timber.d("[스위치 리스너] 프로그래밍적 변경 감지 - 콜백 호출 무시")
                    return@setOnCheckedChangeListener
                }
                
                // 디바운싱 - 마지막 이벤트로부터 500ms 이내면 무시
                if (lastSwitchEventTime > 0 && currentTime - lastSwitchEventTime < 500) {
                    Timber.d("[스위치 리스너] 중복 이벤트 감지 (${currentTime - lastSwitchEventTime}ms) - 무시")
                    return@setOnCheckedChangeListener
                }
                
                // 시간 업데이트
                lastSwitchEventTime = currentTime
                
                // 사용자 입력 확인
                if (!buttonView.isPressed && !isUserAction) {
                    Timber.d("[스위치 리스너] 사용자 입력이 아닌 이벤트 감지 - 무시")
                    return@setOnCheckedChangeListener
                }
                
                // 여기까지 오면 실제 사용자 액션으로 간주
                val modeName = if (isChecked) "쓰기" else "읽기"
                Timber.d("[스위치 리스너] 사용자가 ${modeName}모드로 변경 - 콜백 호출")
                
                // 콜백 호출 전 이벤트 처리 중 플래그 설정
                isProcessingUserEvent = true
                
                try {
                    // 콜백 추출 및 실행을 분리하여 안전하게 호출
                    val callback = onModeToggle
                    if (callback != null) {
                        // 콜백 실행
                        callback(isChecked)
                        Timber.d("[스위치 리스너] 모드 전환 콜백 실행 완료")
                    } else {
                        Timber.e("[스위치 리스너] 콜백이 null입니다")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[스위치 리스너] 콜백 실행 중 오류 발생")
                } finally {
                    // 이벤트 처리 완료 플래그 해제
                    isProcessingUserEvent = false
                }
            }
            
            Timber.d("[스위치 리스너] 설정 완료")
        } catch (e: Exception) {
            Timber.e(e, "[스위치 리스너] 설정 중 오류 발생")
        }
    }
    
    /**
     * 레거시 호환성을 위한 초기화 메서드
     * @param view 루트 뷰
     * @param toggleEditModeCallback 편집 모드 토글 콜백
     */
    fun init(view: View, toggleEditModeCallback: (Boolean) -> Unit) {
        setModeToggleCallback(toggleEditModeCallback)
        initialize(view)
    }
    
    /**
     * 레거시 호환성을 위한 UI 컴포넌트 맵 반환 메서드
     * 부모 클래스의 getUIComponents() 메서드를 사용하도록 수정
     */
    fun getMapUIComponents(): Map<String, View> {
        // 초기화 확인은 부모 클래스에서 처리
        val components = super.getUIComponents()
        if (components.isEmpty()) {
            return emptyMap()
        }
        
        // modeText, modeSwitch, modeBar, editModeTimer만 반환
        return mapOf(
            "modeText" to components["modeText"]!!,
            "modeSwitch" to components["modeSwitch"]!!,
            "modeBar" to components["modeBar"]!!,
            "editModeTimer" to components["editModeTimer"]!!
        )
    }
    
    fun setupMap(map: NaverMap) {
        // 지도 기본 설정
        map.apply {
            minZoom = MapConstants.MIN_ZOOM
            maxZoom = MapConstants.MAX_ZOOM
            moveCamera(CameraUpdate.zoomTo(MapConstants.DEFAULT_ZOOM))
            
            uiSettings.apply {
                isZoomControlEnabled = false  // 줌 컨트롤러 비활성화
                isCompassEnabled = true
                isLocationButtonEnabled = true
                isScaleBarEnabled = true
            }
            
            // 현재 위치 버튼과 나침반의 위치 조정
            setContentPadding(0, 80.dp2px(context), 16.dp2px(context), 16.dp2px(context))  // left, top, right, bottom
        }
        
        Timber.d("지도 기본 설정 완료")
    }
    
    fun updateUI(state: MapState.Success) {
        safeUpdateUI {
            // 편집 모드에 따른 UI 업데이트
            updateEditModeUI(state.editMode)
            
            // 로딩 상태 처리
            progressBar.isVisible = false
            
            Timber.d("UI 업데이트 완료: 편집 모드=${state.editMode}")
        }
    }
    
    /**
     * 편집 모드 UI 업데이트 - 완전히 재작성
     */
    fun updateEditModeUI(editMode: Boolean) {
        safeUpdateUI {
            try {
                // 현재 스위치 상태 확인
                val currentChecked = modeSwitch.isChecked
                
                // 변경사항에 대한 로깅을 명확하게 개선
                Timber.d("[모드 UI 업데이트] 요청: ${if(editMode) "쓰기" else "읽기"}모드, 현재: ${if(currentChecked) "쓰기" else "읽기"}모드")
                
                // 현재 모드와 요청 모드가 같으면 UI 업데이트 불필요
                if (currentChecked == editMode) {
                    Timber.d("[모드 UI 업데이트] 스위치 상태가 이미 일치함 - UI 업데이트만 수행")
                    // UI 업데이트만 수행하고 스위치 상태는 변경하지 않음
                    updateModeTextAndColor(editMode)
                    return@safeUpdateUI
                }
                
                // 모드 불일치 감지 - 스위치 상태 변경 필요
                Timber.d("[모드 UI 업데이트] 모드 불일치 감지 - 스위치 ${if(currentChecked) "쓰기→읽기" else "읽기→쓰기"} 변경 시작")
                
                // 프로그래밍적 변경 플래그 설정
                isProgrammaticChange = true
                
                try {
                    // 스위치 상태 변경 전에 모든 텍스트/색상 업데이트 먼저 수행
                    updateModeTextAndColor(editMode)
                    
                    // 리스너 일시 제거
                    modeSwitch.setOnCheckedChangeListener(null)
                    
                    // 상태 변경
                    modeSwitch.isChecked = editMode
                    Timber.d("[모드 UI 업데이트] 스위치 상태 변경 완료: ${if(editMode) "쓰기" else "읽기"}모드")
                    
                    // 리스너 재설정
                    setupSwitchListener()
                } finally {
                    // 플래그 초기화 - finally 블록에서 반드시 실행되도록
                    isProgrammaticChange = false
                }
                
                Timber.d("[모드 UI 업데이트] 완료: ${if(editMode) "쓰기" else "읽기"}모드")
            } catch (e: Exception) {
                // 에러 발생 시 플래그 초기화 (안전을 위해)
                isProgrammaticChange = false
                Timber.e(e, "[모드 UI 업데이트] 오류 발생")
            }
        }
    }
    
    /**
     * 모드 텍스트 및 색상 업데이트 - 스위치 상태와 분리
     */
    private fun updateModeTextAndColor(isEditMode: Boolean) {
        try {
            // 현재 모드 텍스트 업데이트
            modeText.text = context.getString(
                if (isEditMode) R.string.write_mode else R.string.read_mode
            )
            
            // 색상 업데이트
            val backgroundColor = ContextCompat.getColor(context, R.color.mode_bar_background)
            val textColor = if (isEditMode) {
                ContextCompat.getColor(context, R.color.write_mode_text)
            } else {
                ContextCompat.getColor(context, R.color.read_mode_text)
            }
            
            modeBar.apply {
                setCardBackgroundColor(backgroundColor)
                cardElevation = 4f
            }
            modeText.setTextColor(textColor)
            
            // 타이머 표시 여부 업데이트
            editModeTimer.visibility = if (isEditMode) View.VISIBLE else View.GONE
            
            Timber.d("[모드 UI 업데이트] 텍스트/색상 업데이트 완료: ${if(isEditMode) "쓰기" else "읽기"}모드")
        } catch (e: Exception) {
            Timber.e(e, "[모드 UI 업데이트] 텍스트/색상 업데이트 중 오류 발생")
        }
    }
    
    fun showLoading(isLoading: Boolean) {
        safeUpdateUI {
            progressBar.isVisible = isLoading
        }
    }
    
    fun showError(error: MapError) {
        val message = when (error) {
            is MapError.LocationError -> "위치 정보를 가져올 수 없습니다. GPS 신호를 확인해주세요."
            is MapError.NetworkError -> "네트워크 연결을 확인해주세요."
            is MapError.WriteModeLocked -> "쓰기 모드로 전환이 필요합니다."
            is MapError.UnknownError -> error.message
            is MapError.PermissionError -> "필요한 권한이 없습니다. 설정에서 권한을 허용해주세요."
            is MapError.GenericError -> error.message
            is MapError.MarkerLoadingError -> "마커 로딩 중 오류가 발생했습니다: ${error.message}"
        }
        showError(message)
    }
    
    fun showError(message: String) {
        // 코루틴 취소 예외는 에러 메시지로 표시하지 않음
        if (message.contains("StandaloneCoroutine was cancelled") || 
            message.contains("JobCancellationException") ||
            message.contains("SupervisorJobImpl{Cancelling}")) {
            Timber.d("코루틴 취소 감지됨 - 에러 메시지 표시 생략: $message")
            return
        }
        
        safeUpdateUI {
            rootView?.let { view ->
                Snackbar.make(view, message, Snackbar.LENGTH_LONG)
                    .apply { 
                        this.view.translationY = -(150f.dp2px(view.resources))
                    }
                    .show()
                    
                Timber.d("에러 메시지 표시: $message")
            }
        }
    }
    
    fun moveToLocation(map: NaverMap, latitude: Double, longitude: Double, zoom: Double = MapConstants.DEFAULT_ZOOM) {
        try {
            val cameraUpdate = CameraUpdate.scrollAndZoomTo(
                com.naver.maps.geometry.LatLng(latitude, longitude),
                zoom
            ).animate(CameraAnimation.Easing)
            
            map.moveCamera(cameraUpdate)
            Timber.d("지도 위치 이동: lat=$latitude, lng=$longitude, zoom=$zoom")
        } catch (e: Exception) {
            Timber.e(e, "지도 위치 이동 중 오류 발생")
        }
    }
    
    /**
     * 컴포넌트가 시작될 때 호출됩니다.
     * 필요한 리소스를 초기화하고 작업을 재개합니다.
     */
    override fun onStart() {
        super.onStart()
        Timber.d("MapUIController onStart - 활성화됨")
        isActive = true
    }
    
    /**
     * 컴포넌트가 중지될 때 호출됩니다.
     * 필요하지 않은 리소스를 해제하고 작업을 중단합니다.
     */
    override fun onStop() {
        super.onStop()
        Timber.d("MapUIController onStop - 비활성화됨")
        isActive = false
    }
    
    /**
     * 컴포넌트가 파괴될 때 호출됩니다.
     * 모든 리소스를 정리합니다.
     */
    override fun onDestroy() {
        Timber.d("MapUIController onDestroy - 리소스 정리")
        
        // 리스너 정리
        if (::modeSwitch.isInitialized) {
            modeSwitch.setOnCheckedChangeListener(null)
        }
        
        if (::modeBar.isInitialized) {
            modeBar.setOnClickListener(null)
        }
        
        // 콜백 참조 해제
        onModeToggle = null
        
        // 컴포넌트 참조 해제
        clearComponentReferences()
        
        // 부모 클래스의 onDestroy 호출 (cleanup 포함)
        super.onDestroy()
    }
    
    /**
     * UI 업데이트를 안전하게 수행합니다.
     * 컴포넌트가 활성화되어 있을 때만 UI 업데이트를 수행합니다.
     */
    fun safeUpdateUIIfActive(action: () -> Unit) {
        if (isActive && isInitialized()) {
            safeUpdateUI(action)
        } else {
            Timber.d("MapUIController가 비활성화되어 있거나 초기화되지 않아 UI 업데이트를 수행하지 않습니다.")
        }
    }
} 