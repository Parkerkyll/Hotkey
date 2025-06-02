package com.parker.hotkey.presentation.help

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import by.kirich1409.viewbindingdelegate.viewBinding
import com.parker.hotkey.MainActivity
import com.parker.hotkey.R
import com.parker.hotkey.data.manager.LoadingManager
import com.parker.hotkey.databinding.FragmentHelpBinding
import com.parker.hotkey.presentation.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.ref.WeakReference
import javax.inject.Inject

/**
 * 사용법 화면 Fragment
 */
@AndroidEntryPoint
class HelpFragment : Fragment(R.layout.fragment_help) {
    
    // ViewBinding 프로퍼티 델리게이트 사용
    private val binding by viewBinding(FragmentHelpBinding::bind)
    
    // ViewModel 주입 - viewModels() 대리자 사용 (lifecycle-aware)
    private val viewModel: HelpViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    
    // 로딩 매니저 주입
    @Inject
    lateinit var loadingManager: LoadingManager
    
    // 하드웨어 백버튼 콜백
    private lateinit var backPressedCallback: OnBackPressedCallback
    
    // MainActivity 참조를 위한 변수
    private var mainActivity: MainActivity? = null
    
    // 이벤트 구독 취소를 위한 코루틴 저장
    private val fragmentJobs = mutableListOf<kotlinx.coroutines.Job>()
    
    override fun onAttach(context: android.content.Context) {
        super.onAttach(context)
        
        // MainActivity 참조 저장
        mainActivity = context as? MainActivity
        
        // 하드웨어 백버튼 처리를 위한 콜백 등록
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Timber.d("HelpFragment: 백버튼 이벤트 처리")
                
                // 로딩 화면 표시
                loadingManager.showLoading("지도 준비중...")
                
                // activityViewModels로 주입받은 MainViewModel 사용
                mainViewModel.navigateToMap()
            }
        }
        
        // 백버튼 콜백 등록
        requireActivity().onBackPressedDispatcher.addCallback(this, backPressedCallback)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 이벤트 리스너 설정
        setupEventListeners()
        
        // 이미지 클릭 리스너 설정
        setupImageClickListeners()
        
        // 기본 이미지 로드
        loadDefaultImages()
        
        // ViewModel 데이터 구독
        observeViewModel()
    }
    
    private fun setupEventListeners() {
        // 모드 전환 카드 클릭 이벤트
        binding.cardHelpMode.setOnClickListener {
            Timber.d("모드 전환 카드 클릭됨")
            viewModel.onHelpItemClicked(0)
        }
        
        // 모드 변경 방법 카드 클릭 이벤트
        binding.cardHelpModeMethod.setOnClickListener {
            Timber.d("모드 변경 방법 카드 클릭됨")
            viewModel.onHelpItemClicked(1)
        }
        
        binding.cardHelpMarker.setOnClickListener {
            Timber.d("마커 만들기 카드 클릭됨")
            viewModel.onHelpItemClicked(2)
        }
        
        binding.cardHelpMemo.setOnClickListener {
            Timber.d("메모 추가 카드 클릭됨")
            viewModel.onHelpItemClicked(3)
        }
        
        // 지도로 돌아가기 버튼 클릭 이벤트
        binding.btnBackToMap.setOnClickListener {
            Timber.d("도움말 화면에서 지도로 돌아가기 버튼 클릭")
            
            // 로딩 화면 표시
            loadingManager.showLoading("지도 준비중...")
            
            // activityViewModels로 주입받은 MainViewModel 사용
            mainViewModel.navigateToMap()
        }
    }
    
    private fun setupImageClickListeners() {
        // 각 이미지 클릭 시 확대 다이얼로그 표시
        binding.ivHelpMode.setOnClickListener {
            showZoomedImage(R.drawable.help_mode_basic)
        }
        
        binding.ivHelpModeMethod.setOnClickListener {
            showZoomedImage(R.drawable.help_mode_change)
        }
        
        binding.ivHelpMarker.setOnClickListener {
            showZoomedImage(R.drawable.help_mode_maker)
        }
        
        binding.ivHelpMemo.setOnClickListener {
            showZoomedImage(R.drawable.help_mode_makerandmeno)
        }
    }
    
    private fun showZoomedImage(imageResId: Int) {
        try {
            val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.parseColor("#CC000000")))
            dialog.setContentView(R.layout.dialog_image_zoom)
            
            // PhotoView 참조 가져오기
            val photoView = dialog.findViewById<com.github.chrisbanes.photoview.PhotoView>(R.id.zoomed_image)
            photoView.setImageResource(imageResId)
            
            // 이미지 클릭 시 다이얼로그 닫기
            photoView.setOnClickListener {
                // 메모리 누수 방지를 위해 이미지 참조 해제
                photoView.setImageDrawable(null)
                dialog.dismiss()
            }
            
            // 다이얼로그가 dismiss될 때 이미지 참조 해제를 위한 리스너 추가
            dialog.setOnDismissListener {
                photoView.setImageDrawable(null)
                Timber.d("이미지 확대 다이얼로그 닫힘, 이미지 참조 해제됨")
            }
            
            dialog.show()
            Timber.d("이미지 확대 다이얼로그 표시 성공")
        } catch (e: Exception) {
            Timber.e(e, "이미지 확대 다이얼로그 표시 실패")
        }
    }
    
    private fun loadDefaultImages() {
        try {
            // 기본 이미지 설정
            binding.ivHelpMode.setImageResource(R.drawable.help_mode_basic)
            binding.ivHelpModeMethod.setImageResource(R.drawable.help_mode_change)
            binding.ivHelpMarker.setImageResource(R.drawable.help_mode_maker)
            binding.ivHelpMemo.setImageResource(R.drawable.help_mode_makerandmeno)
            
            Timber.d("도움말 기본 이미지 로딩 성공")
        } catch (e: Exception) {
            Timber.e(e, "도움말 기본 이미지 로딩 실패")
            
            // 오류 시 기본 아이콘으로 대체
            binding.ivHelpMode.setImageResource(R.drawable.ic_help)
            binding.ivHelpModeMethod.setImageResource(R.drawable.ic_help)
            binding.ivHelpMarker.setImageResource(R.drawable.ic_help)
            binding.ivHelpMemo.setImageResource(R.drawable.ic_help)
        }
    }
    
    /**
     * ViewModel의 데이터를 관찰합니다.
     */
    private fun observeViewModel() {
        val job1 = viewLifecycleOwner.lifecycleScope.launch {
            // 상태 관찰
            viewModel.state.collectLatest { state ->
                // 로딩 상태 처리
                Timber.d("상태 변경: 로딩=${state.isLoading}, 아이템 수=${state.helpItems.size}")
                
                // 에러 메시지 처리
                state.error?.let { errorMessage ->
                    Timber.e("에러 발생: $errorMessage")
                    // 필요시 에러 표시 로직 추가
                }
                
                // 아이템 업데이트
                if (state.helpItems.isNotEmpty()) {
                    updateHelpItems(state.helpItems)
                }
            }
        }
        fragmentJobs.add(job1)
        
        val job2 = viewLifecycleOwner.lifecycleScope.launch {
            // 이벤트 관찰
            viewModel.events.collectLatest { event ->
                when (event) {
                    is HelpEvent.ShowToast -> {
                        Timber.d("토스트 표시 이벤트: ${event.message}")
                        view?.let { v ->
                            com.google.android.material.snackbar.Snackbar.make(
                                v,
                                event.message,
                                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                            ).show()
                        }
                    }
                    is HelpEvent.NavigateToDetail -> {
                        Timber.d("상세 화면 이동 이벤트: itemId=${event.itemId}")
                        // 여기서 상세 화면으로 이동 또는 다이얼로그를 표시할 수 있음
                        when (event.itemId) {
                            0 -> showZoomedImage(R.drawable.help_mode_basic)
                            1 -> showZoomedImage(R.drawable.help_mode_change)
                            2 -> showZoomedImage(R.drawable.help_mode_maker)
                            3 -> showZoomedImage(R.drawable.help_mode_makerandmeno)
                        }
                    }
                }
            }
        }
        fragmentJobs.add(job2)
    }
    
    /**
     * 도움말 아이템 UI 업데이트
     */
    private fun updateHelpItems(items: List<HelpItem>) {
        Timber.d("도움말 아이템 업데이트: ${items.size}개")
        // 현재는 UI 자동 업데이트 필요 없음
    }
    
    override fun onDetach() {
        super.onDetach()
        // 백버튼 콜백 제거
        if (::backPressedCallback.isInitialized) {
            backPressedCallback.remove()
        }
        // MainActivity 참조 해제
        mainActivity = null
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        
        // 프래그먼트 코루틴 작업 취소
        fragmentJobs.forEach { it.cancel() }
        fragmentJobs.clear()
        
        // 뷰바인딩 참조 제거는 viewBinding 델리게이트가 자동으로 처리함
    }
    
    companion object {
        private const val TAG = "HelpFragment"
    }
} 