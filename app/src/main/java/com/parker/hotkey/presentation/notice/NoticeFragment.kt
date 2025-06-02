package com.parker.hotkey.presentation.notice

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.parker.hotkey.R
import com.parker.hotkey.presentation.main.MainViewModel
import timber.log.Timber

class NoticeFragment : Fragment() {
    
    private var callback: OnBackPressedCallback? = null
    private lateinit var btnBackToMap: Button
    private lateinit var btnBack: ImageButton
    
    // MainViewModel 참조
    private val mainViewModel: MainViewModel by activityViewModels()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_notice, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // View 초기화
        initViews(view)
        
        // 이벤트 리스너 설정
        setupEventListeners()
        
        // 백버튼 콜백 설정
        setupBackPressedCallback()
    }
    
    private fun initViews(view: View) {
        btnBackToMap = view.findViewById(R.id.btn_back_to_map)
        btnBack = view.findViewById(R.id.btn_back)
    }
    
    private fun setupEventListeners() {
        // 지도로 돌아가기 버튼 클릭 이벤트
        btnBackToMap.setOnClickListener {
            Timber.d("공지사항 화면에서 지도로 돌아가기 버튼 클릭")
            // MainViewModel을 통해 지도로 이동 (로딩 화면 포함)
            mainViewModel.navigateToMap()
        }
        
        // 툴바의 백버튼 클릭 이벤트
        btnBack.setOnClickListener {
            // DrawerLayout을 열어 네비게이션 메뉴 표시
            val activity = requireActivity()
            val drawerLayout = activity.findViewById<DrawerLayout>(R.id.drawer_layout)
            drawerLayout.open()
        }
    }
    
    private fun setupBackPressedCallback() {
        callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // DrawerLayout을 열어 네비게이션 메뉴 표시
                val activity = requireActivity()
                val drawerLayout = activity.findViewById<DrawerLayout>(R.id.drawer_layout)
                drawerLayout.open()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback!!)
    }
    
    override fun onDestroyView() {
        // View 참조 해제
        btnBackToMap.setOnClickListener(null)
        btnBack.setOnClickListener(null)
        
        // 백버튼 콜백 제거
        callback?.remove()
        callback = null
        
        // 추가: 모든 뷰 리스너 정리
        view?.let { com.parker.hotkey.util.MemoryLeakHelper.clearViewListeners(it) }
        
        super.onDestroyView()
    }
} 