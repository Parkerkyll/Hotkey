package com.parker.hotkey.presentation.notice

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.parker.hotkey.MainActivity
import com.parker.hotkey.R
import com.parker.hotkey.data.manager.LoadingManager
import com.parker.hotkey.databinding.FragmentNoticeListBinding
import com.parker.hotkey.presentation.main.MainViewModel
import com.parker.hotkey.utils.event.NoticeUpdateEvent
import dagger.hilt.android.AndroidEntryPoint
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class NoticeListFragment : Fragment() {
    
    private var _binding: FragmentNoticeListBinding? = null
    private val binding get() = _binding!!
    
    @Inject
    lateinit var loadingManager: LoadingManager
    
    private val viewModel: NoticeViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    
    private lateinit var noticeAdapter: NoticeAdapter
    private var mainActivity: MainActivity? = null
    private lateinit var backPressedCallback: OnBackPressedCallback
    
    override fun onAttach(context: Context) {
        super.onAttach(context)
        
        // MainActivity 참조 저장
        mainActivity = context as? MainActivity
        
        // 하드웨어 백버튼 처리를 위한 콜백 등록
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Timber.d("NoticeListFragment: 백버튼 이벤트 처리")
                // 드로어 메뉴 열기
                mainActivity?.openDrawer()
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
        _binding = FragmentNoticeListBinding.inflate(inflater, container, false)
        
        setupToolbar()
        setupRecyclerView()
        setupObservers()
        setupSwipeRefresh()
        setupBackToMapButton()
        
        viewModel.loadNotices()
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerView()
        setupListeners()
        viewModel.loadNotices()
    }
    
    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }
    
    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // RecyclerView 어댑터 참조 해제
        if (_binding != null) {
            binding.rvNotices.adapter = null
        }
        _binding = null
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
    
    private fun setupToolbar() {
        binding.toolbar.apply {
            // 백버튼 클릭 리스너 설정
            setNavigationOnClickListener {
                Timber.d("NoticeListFragment: 툴바 백버튼 클릭")
                // 드로어 메뉴 열기
                mainActivity?.openDrawer()
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
    
    private fun setupRecyclerView() {
        noticeAdapter = NoticeAdapter { notice ->
            // 공지사항 클릭 시 상세 화면으로 이동
            val action = NoticeListFragmentDirections.actionNoticeListToNoticeDetail(notice.id)
            findNavController().navigate(action)
        }
        
        binding.rvNotices.apply {
            adapter = noticeAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
        }
    }
    
    private fun setupObservers() {
        viewModel.noticeList.observe(viewLifecycleOwner) { notices ->
            noticeAdapter.submitList(notices)
            
            // 빈 목록 처리
            binding.tvEmpty.visibility = if (notices.isNullOrEmpty()) View.VISIBLE else View.GONE
        }
        
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.swipeRefresh.isRefreshing = false
        }
        
        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (errorMessage.isNotEmpty()) {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadNotices()
        }
    }
    
    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadNotices()
        }
    }
    
    /**
     * 지도로 돌아가기
     * MainViewModel의 navigateToMap() 메서드 호출하여 로딩화면과 함께 지도로 이동
     */
    private fun navigateToMap() {
        Timber.d("지도로 돌아가기 메서드 호출")
        
        // activityViewModels로 주입받은 MainViewModel 사용
        mainViewModel.navigateToMap()
    }
    
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onNoticeUpdateEvent(@Suppress("UNUSED_PARAMETER") event: NoticeUpdateEvent) {
        // 공지 업데이트 이벤트 수신 시 공지 목록 새로고침
        viewModel.loadNotices()
    }
} 