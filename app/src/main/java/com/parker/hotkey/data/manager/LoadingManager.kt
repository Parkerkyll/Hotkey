package com.parker.hotkey.data.manager

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import com.parker.hotkey.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 앱 전체에서 사용할 로딩 화면을 관리하는 싱글톤 클래스
 */
@Singleton
class LoadingManager @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    private var loadingView: FrameLayout? = null
    private var messageTextView: TextView? = null
    private var motorcycleImageView: ImageView? = null
    private var progressBarLoading: ProgressBar? = null
    private var motorcycleAnimation: Animation? = null
    private var minimumLoadingJob: Job? = null
    
    // 최소 로딩 시간 (밀리초) - 너무 빠른 로딩 화면 전환으로 인한 깜빡임 방지
    private val minimumLoadingTimeMs = 2200L
    
    // 로딩 시작 시간
    private var loadingStartTimeMs = 0L

    // Activity context 참조 저장
    private var activityContext: Activity? = null
    
    /**
     * Activity context 설정
     * 로딩 화면 표시에 필요함
     */
    fun setActivityContext(activity: Activity) {
        this.activityContext = activity
        // 로딩 뷰 초기화
        initLoadingView()
        Timber.d("ActivityContext 설정됨: ${activity.javaClass.simpleName}")
    }
    
    /**
     * 로딩 뷰 초기화
     */
    private fun initLoadingView() {
        activityContext?.let { activity ->
            try {
                // 로딩 뷰 직접 찾기
                loadingView = activity.findViewById(R.id.fullscreenLoadingLayout)
                Timber.d("로딩 뷰 찾기 시도: ${loadingView != null}")
                
                // 로딩 뷰가 null인 경우 로그 출력
                if (loadingView == null) {
                    Timber.e("로딩 뷰를 찾을 수 없습니다: fullscreenLoadingLayout")
                    
                    // 문제 해결을 위한 디버깅 정보 출력
                    val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
                    dumpViewHierarchy(rootView, "")
                    
                    return
                }
                
                // 로딩 뷰가 제대로 설정되었는지 확인
                val parentView = loadingView?.parent as? ViewGroup
                if (parentView != null) {
                    Timber.d("로딩 뷰 부모: ${parentView.javaClass.simpleName}")
                } else {
                    Timber.e("로딩 뷰의 부모를 찾을 수 없습니다")
                }
                
                // 메시지 뷰 초기화
                messageTextView = loadingView?.findViewById(R.id.tvLoadingMessage)
                
                // 오토바이 이미지뷰 초기화
                motorcycleImageView = loadingView?.findViewById(R.id.ivMotorcycle)
                
                // 프로그레스바 초기화
                progressBarLoading = loadingView?.findViewById(R.id.progressBarLoading)
                
                // 애니메이션 로드
                motorcycleAnimation = AnimationUtils.loadAnimation(activity, R.anim.motorcycle_move)
                
                Timber.d("로딩 뷰 초기화 완료: ${loadingView?.id}, 가시성: ${loadingView?.visibility}")
            } catch (e: Exception) {
                Timber.e(e, "로딩 뷰 초기화 중 오류 발생")
            }
        }
    }
    
    // 디버깅용: 뷰 계층 구조 출력
    private fun dumpViewHierarchy(view: View?, prefix: String) {
        if (view == null) return
        
        Timber.d("$prefix${view.javaClass.simpleName}(${view.id})")
        
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                dumpViewHierarchy(view.getChildAt(i), "$prefix  ")
            }
        }
    }
    
    /**
     * 로딩 화면을 표시합니다.
     * 
     * @param message 로딩 화면에 표시할 메시지
     */
    fun showLoading(message: String) {
        Timber.d("로딩 화면 표시 요청: $message")
        
        if (activityContext == null || activityContext?.isFinishing == true) {
            Timber.e("유효한 Activity context가 없어 로딩 화면을 표시할 수 없습니다.")
            return
        }
        
        if (loadingView == null) {
            Timber.d("로딩 뷰가 null이므로 초기화 시도")
            initLoadingView()
            
            if (loadingView == null) {
                Timber.e("로딩 뷰 초기화 실패")
                return
            }
        }
        
        activityContext?.runOnUiThread {
            try {
                Timber.d("로딩 화면 표시 시작: 메시지=$message")
                
                // 메시지 설정
                messageTextView?.text = message
                
                // 프로그레스바 초기화
                progressBarLoading?.progress = 0
                
                // 오토바이 애니메이션 시작
                motorcycleImageView?.let { imageView ->
                    motorcycleAnimation?.let { animation ->
                        imageView.startAnimation(animation)
                    }
                }
                
                // 확실하게 로딩 화면이 보이도록 최상위로 설정
                loadingView?.bringToFront()
                
                // 확실하게 보이도록 설정
                loadingView?.visibility = View.VISIBLE
                
                // 부모 뷰 강제 레이아웃 갱신
                (loadingView?.parent as? ViewGroup)?.invalidate()
                
                loadingStartTimeMs = System.currentTimeMillis()
                Timber.d("로딩 화면 표시됨: 가시성=${loadingView?.visibility}")
            } catch (e: Exception) {
                Timber.e(e, "로딩 화면 표시 중 오류 발생")
            }
        }
    }
    
    /**
     * 로딩 진행률을 업데이트합니다.
     * 
     * @param progress 0-100 사이의 진행률 값
     */
    fun updateProgress(progress: Int) {
        if (activityContext == null || activityContext?.isFinishing == true) {
            Timber.e("유효한 Activity context가 없어 로딩 진행률을 업데이트할 수 없습니다.")
            return
        }
        
        activityContext?.runOnUiThread {
            try {
                val clampedProgress = progress.coerceIn(0, 100)
                progressBarLoading?.progress = clampedProgress
                Timber.d("로딩 진행률 업데이트: $clampedProgress%")
            } catch (e: Exception) {
                Timber.e(e, "로딩 진행률 업데이트 중 오류 발생")
            }
        }
    }
    
    /**
     * 로딩 화면을 숨깁니다.
     * 최소 로딩 시간을 보장하기 위해 필요한 경우 지연 후 숨깁니다.
     * 
     * @param lifecycleScope 코루틴 실행을 위한 LifecycleCoroutineScope
     */
    fun hideLoading(lifecycleScope: LifecycleCoroutineScope? = null) {
        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - loadingStartTimeMs
        
        Timber.d("로딩 화면 숨김 요청: 경과 시간=$elapsedTime ms, 최소 시간=$minimumLoadingTimeMs ms")
        
        // 최소 로딩 시간 보장
        if (elapsedTime < minimumLoadingTimeMs && lifecycleScope != null) {
            // 기존 작업 취소
            minimumLoadingJob?.cancel()
            
            minimumLoadingJob = lifecycleScope.launch {
                try {
                    val remainingTime = minimumLoadingTimeMs - elapsedTime
                    Timber.d("최소 로딩 시간 보장을 위해 ${remainingTime}ms 대기")
                    
                    // 남은 시간 동안 프로그레스바 100%로 자연스럽게 채우기
                    val currentProgress = progressBarLoading?.progress ?: 0
                    val remainingProgress = 100 - currentProgress
                    
                    if (remainingProgress > 0) {
                        val stepCount = 10 // 10단계로 나눠서 진행
                        val stepTime = remainingTime / stepCount
                        val progressStep = remainingProgress / stepCount
                        
                        for (i in 1..stepCount) {
                            delay(stepTime)
                            val newProgress = currentProgress + (progressStep * i)
                            updateProgress(newProgress.coerceAtMost(100))
                        }
                    }
                    
                    hideLoadingView()
                } catch (e: CancellationException) {
                    Timber.d("로딩 화면 숨기기 작업이 취소됨")
                } catch (e: Exception) {
                    Timber.e(e, "로딩 화면 숨기기 중 오류 발생")
                    hideLoadingView() // 오류 발생 시에도 로딩 화면은 숨기도록 처리
                }
            }
        } else {
            // 최소 시간이 이미 경과했거나 lifecycleScope가 없는 경우 바로 숨기기
            hideLoadingView()
        }
    }
    
    /**
     * 로딩 화면을 안전하게 숨깁니다.
     */
    private fun hideLoadingView() {
        activityContext?.runOnUiThread {
            try {
                Timber.d("로딩 화면 숨김 처리 시작: 현재 가시성=${loadingView?.visibility}")
                
                // 애니메이션 중지
                motorcycleImageView?.clearAnimation()
                
                loadingView?.visibility = View.GONE
                Timber.d("로딩 화면 숨김 처리 완료: 변경 후 가시성=${loadingView?.visibility}")
            } catch (e: Exception) {
                Timber.e(e, "로딩 화면 숨기기 중 오류 발생")
            }
        }
    }
    
    /**
     * 리소스 정리
     * 액티비티 종료 시 호출 필요
     */
    fun cleanup() {
        try {
            hideLoadingView()
            loadingView = null
            messageTextView = null
            motorcycleImageView = null
            progressBarLoading = null
            motorcycleAnimation = null
            minimumLoadingJob?.cancel()
            minimumLoadingJob = null
            activityContext = null
            Timber.d("LoadingManager 리소스 정리 완료")
        } catch (e: Exception) {
            Timber.e(e, "LoadingManager 리소스 정리 중 오류 발생")
        }
    }
} 