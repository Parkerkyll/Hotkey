package com.parker.hotkey.presentation.notice

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.button.MaterialButton
import com.parker.hotkey.MainActivity
import com.parker.hotkey.R
import com.parker.hotkey.databinding.FragmentNoticeDetailBinding
import com.parker.hotkey.presentation.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class NoticeDetailFragment : Fragment() {
    
    private var _binding: FragmentNoticeDetailBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: NoticeViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val args: NoticeDetailFragmentArgs by navArgs()
    
    // 백 버튼 콜백
    private lateinit var backPressedCallback: OnBackPressedCallback
    
    // MainActivity 참조를 위한 변수
    private var mainActivity: MainActivity? = null
    
    override fun onAttach(context: Context) {
        super.onAttach(context)
        
        // MainActivity 참조 저장
        mainActivity = context as? MainActivity
        
        // 하드웨어 백버튼 처리를 위한 콜백 등록
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Timber.d("NoticeDetailFragment: 백버튼 이벤트 처리")
                // 네비게이션 컨트롤러로 뒤로가기 실행
                findNavController().navigateUp()
            }
        }
        
        // 백버튼 콜백 등록
        requireActivity().onBackPressedDispatcher.addCallback(this, backPressedCallback)
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNoticeDetailBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupObservers()
        setupBackToMapButton()
        loadNoticeDetail()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    override fun onDetach() {
        super.onDetach()
        // 백버튼 콜백 제거
        backPressedCallback.remove()
        // MainActivity 참조 해제
        mainActivity = null
    }
    
    private fun setupToolbar() {
        // 툴바 설정
        binding.toolbar.apply {
            // 백버튼 클릭 리스너 설정
            setNavigationOnClickListener {
                Timber.d("NoticeDetailFragment: 툴바 백버튼 클릭")
                findNavController().navigateUp()
            }
        }
    }
    
    private fun setupBackToMapButton() {
        // CardView 생성
        val cardView = androidx.cardview.widget.CardView(requireContext()).apply {
            id = View.generateViewId()
            cardElevation = resources.getDimension(R.dimen.fab_margin) / 2  // 8dp
            radius = resources.getDimension(R.dimen.fab_margin) * 1.5f      // 24dp
            setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
            
            // ConstraintLayout에 추가하기 위한 레이아웃 파라미터
            layoutParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                marginEnd = resources.getDimensionPixelSize(R.dimen.fab_margin)
                bottomMargin = resources.getDimensionPixelSize(R.dimen.fab_margin)
            }
        }
        
        // MaterialButton 생성
        val backToMapButton = com.google.android.material.button.MaterialButton(requireContext()).apply {
            id = View.generateViewId()
            text = getString(R.string.back_to_map)
            
            // 아이콘 설정
            setIconResource(R.drawable.ic_map)
            iconSize = resources.getDimensionPixelSize(R.dimen.fab_margin) * 3 / 2  // 24dp
            iconTint = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#757575"))
            iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = resources.getDimensionPixelSize(R.dimen.fab_margin) / 2    // 8dp
            
            // 스타일 설정
            cornerRadius = resources.getDimensionPixelSize(R.dimen.fab_margin) * 3 / 2  // 24dp
            setPadding(
                resources.getDimensionPixelSize(R.dimen.fab_margin),  // 16dp
                resources.getDimensionPixelSize(R.dimen.fab_margin) * 3 / 4,  // 12dp
                resources.getDimensionPixelSize(R.dimen.fab_margin),  // 16dp
                resources.getDimensionPixelSize(R.dimen.fab_margin) * 3 / 4   // 12dp
            )
            
            // 색상 설정
            setTextColor(android.graphics.Color.BLACK)
            backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            elevation = 0f
            
            // 클릭 리스너
            setOnClickListener {
                Timber.d("지도로 돌아가기 버튼 클릭")
                navigateToMap()
            }
            
            // 레이아웃 파라미터
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        // CardView에 버튼 추가
        cardView.addView(backToMapButton)
        
        // 루트 레이아웃에 CardView 추가
        binding.root.addView(cardView)
    }
    
    private fun setupObservers() {
        viewModel.selectedNotice.observe(viewLifecycleOwner) { notice ->
            notice?.let {
                binding.tvTitle.text = it.title
                binding.tvContent.text = it.content
                binding.tvDate.text = formatDate(it.createdAt)
            }
        }
        
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        
        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (errorMessage.isNotEmpty()) {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 지도로 돌아가기
     * MainViewModel의 navigateToMap() 메서드 호출하여 로딩화면과 함께 지도로 이동
     */
    private fun navigateToMap() {
        Timber.d("지도로 돌아가기 메서드 호출")
        
        // activityViewModels()로 주입받은 MainViewModel 직접 사용
        mainViewModel.navigateToMap()
    }
    
    private fun loadNoticeDetail() {
        val noticeId = args.noticeId
        viewModel.loadNoticeDetail(noticeId)
    }
    
    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy년 MM월 dd일 HH:mm", Locale.getDefault())
        // 초 단위 타임스탬프를 밀리초로 변환
        return sdf.format(Date(timestamp * 1000))
    }
} 