package com.parker.hotkey.presentation.bug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.parker.hotkey.MainActivity
import com.parker.hotkey.R
import com.parker.hotkey.data.manager.LoadingManager
import com.parker.hotkey.presentation.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class ReportBugFragment : Fragment() {

    private var _toolbar: Toolbar? = null
    private var _backButton: ImageButton? = null
    private var _linkTextView: TextView? = null
    private var _codeTextView: TextView? = null
    private var _copyButton: Button? = null
    private var _openKakaoButton: Button? = null
    private var _backToMapButton: MaterialButton? = null
    
    // 프로퍼티를 통한 null 안전 접근
    private val toolbar get() = _toolbar
    private val backButton get() = _backButton
    private val linkTextView get() = _linkTextView
    private val codeTextView get() = _codeTextView
    private val copyButton get() = _copyButton
    private val openKakaoButton get() = _openKakaoButton
    private val backToMapButton get() = _backToMapButton
    
    @Inject
    lateinit var loadingManager: LoadingManager
    
    // ViewModel을 액티비티에서 가져오는 대신 activityViewModels() 사용
    private val mainViewModel: MainViewModel by activityViewModels()
    
    private val kakaoLink = "https://open.kakao.com/o/gdNwJTsh"
    private val inviteCode = "Hotkey01"
    
    // 백 버튼 콜백
    private var backPressedCallback: OnBackPressedCallback? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_report_bug, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 백 버튼 처리
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                openNavigationDrawer()
            }
        }.also {
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, it)
        }

        // 뷰 바인딩
        _toolbar = view.findViewById(R.id.toolbar)
        _backButton = view.findViewById(R.id.btn_back)
        _linkTextView = view.findViewById(R.id.tv_link)
        _codeTextView = view.findViewById(R.id.tv_code)
        _copyButton = view.findViewById(R.id.btn_copy)
        _openKakaoButton = view.findViewById(R.id.btn_open_kakao)
        _backToMapButton = view.findViewById(R.id.btn_back_to_map)

        // 백버튼 클릭 이벤트 처리
        backButton?.setOnClickListener {
            openNavigationDrawer()
        }

        // 링크 클릭 이벤트 처리
        linkTextView?.setOnClickListener {
            openKakaoLink()
        }

        // 복사 버튼 클릭 이벤트 처리
        copyButton?.setOnClickListener {
            copyInviteCode()
        }

        // 카카오톡으로 참여하기 버튼 클릭 이벤트 처리
        openKakaoButton?.setOnClickListener {
            openKakaoLink()
        }
        
        // 지도로 돌아가기 버튼 클릭 이벤트 처리
        backToMapButton?.setOnClickListener {
            navigateToMap()
        }
    }
    
    override fun onDestroyView() {
        // 뷰 참조 제거
        _toolbar = null
        _backButton = null
        _linkTextView = null
        _codeTextView = null
        _copyButton = null
        _openKakaoButton = null
        _backToMapButton = null
        
        // 백 버튼 콜백 제거
        backPressedCallback?.remove()
        backPressedCallback = null
        
        super.onDestroyView()
    }
    
    /**
     * 네비게이션 드로어를 엽니다.
     */
    private fun openNavigationDrawer() {
        (requireActivity() as? MainActivity)?.openDrawer()
    }
    
    /**
     * 네비게이션 컨트롤러로 위로 이동
     */
    private fun navigateUp() {
        findNavController().navigateUp()
    }
    
    /**
     * 지도로 이동 (로딩 화면 표시)
     */
    private fun navigateToMap() {
        // 로딩 화면 표시
        loadingManager.showLoading("지도 준비중")
        
        // MainViewModel의 지도로 이동 메서드 호출
        mainViewModel.navigateToMap()
        
        // 다른 메뉴들과의 일관성을 위해 약간의 지연 후 로딩 화면 숨김
        // 실제 메인 액티비티에서 추가 로직 처리
        viewLifecycleOwner.lifecycleScope.launch {
            delay(500) // 다른 화면과 일관성을 위한 지연
        }
    }

    /**
     * 카카오톡 오픈채팅방 링크를 열기
     */
    private fun openKakaoLink() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(kakaoLink))
            startActivity(intent)
            Timber.d("카카오톡 링크 열기 시도: $kakaoLink")
        } catch (e: Exception) {
            Timber.e(e, "카카오톡 링크를 여는 중 오류 발생")
            Toast.makeText(requireContext(), "링크를 열 수 없습니다", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 초대 코드를 클립보드에 복사
     */
    private fun copyInviteCode() {
        try {
            // 클립보드 매니저 가져오기
            val clipboardManager = requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            
            // 클립 데이터 생성
            val clipData = ClipData.newPlainText("invite_code", inviteCode)
            
            // 클립보드에 데이터 저장
            clipboardManager.setPrimaryClip(clipData)
            
            Toast.makeText(requireContext(), "초대 코드가 복사되었습니다", Toast.LENGTH_SHORT).show()
            Timber.d("초대 코드 복사됨: $inviteCode")
        } catch (e: Exception) {
            Timber.e(e, "초대 코드 복사 중 오류 발생")
            Toast.makeText(requireContext(), "초대 코드 복사에 실패했습니다", Toast.LENGTH_SHORT).show()
        }
    }
} 