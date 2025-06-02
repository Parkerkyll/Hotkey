package com.parker.hotkey.presentation.profile

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.parker.hotkey.R
import com.parker.hotkey.data.manager.UserPreferencesManager
import com.parker.hotkey.databinding.FragmentProfileBinding
import com.parker.hotkey.domain.repository.AuthRepository
import com.parker.hotkey.presentation.login.LoginTestActivity
import com.parker.hotkey.presentation.main.MainViewModel
import com.parker.hotkey.util.Result
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import timber.log.Timber
import java.lang.ref.WeakReference
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileFragment : Fragment() {
    
    @Inject
    lateinit var userPreferencesManager: UserPreferencesManager
    
    @Inject
    lateinit var authRepository: AuthRepository
    
    // MainViewModel을 activityViewModels()로 주입
    private val mainViewModel: MainViewModel by activityViewModels()
    
    // ViewBinding 사용
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    
    // 약한 참조로 Activity 관련 객체 저장
    private var _drawerLayoutRef: WeakReference<DrawerLayout>? = null
    
    private var _callback: OnBackPressedCallback? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // DrawerLayout 약한 참조로 저장
        activity?.findViewById<DrawerLayout>(R.id.drawer_layout)?.let {
            _drawerLayoutRef = WeakReference(it)
        }
        
        // 데이터 설정
        setupUserData()
        
        // 이벤트 리스너 설정
        setupEventListeners()
        
        // 백 버튼 콜백 설정
        setupBackPressedCallback()
    }
    
    private fun setupUserData() {
        // null 체크 추가
        if (!isAdded || _binding == null) {
            Timber.e("setupUserData: binding이 null이거나 프래그먼트가 detach됨")
            return
        }
        
        // 카카오 정보 설정
        val kakaoId = userPreferencesManager.getKakaoId() ?: "로그인 정보 없음"
        val nickname = userPreferencesManager.getKakaoNickname() ?: "사용자"
        binding.tvKakaoId.text = kakaoId
        binding.tvNickname.text = nickname
        
        // 프로필 이미지 로드
        val profileUrl = userPreferencesManager.getKakaoProfileUrl()
        Timber.d("프로필 URL: $profileUrl")
        
        if (!profileUrl.isNullOrEmpty() && isAdded) {
            Timber.d("프로필 이미지 로드 시작")
            
            // 기존 이미지 소스 제거
            binding.ivProfile.setImageDrawable(null)
            
            // isAdded 체크와 getContext() null 체크 추가
            context?.let { safeContext ->
                try {
                    Glide.with(safeContext)
                        .load(profileUrl)
                        .circleCrop()
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .error(R.drawable.ic_launcher_foreground)
                        .skipMemoryCache(false)  // 메모리 캐시 사용
                        .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                            override fun onLoadFailed(
                                e: com.bumptech.glide.load.engine.GlideException?,
                                model: Any?,
                                target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                                isFirstResource: Boolean
                            ): Boolean {
                                Timber.e(e, "프로필 이미지 로드 실패")
                                return false
                            }

                            override fun onResourceReady(
                                resource: android.graphics.drawable.Drawable,
                                model: Any,
                                target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                                dataSource: com.bumptech.glide.load.DataSource,
                                isFirstResource: Boolean
                            ): Boolean {
                                if (_binding != null) {
                                    Timber.d("프로필 이미지 로드 성공, 이미지 크기: ${resource.intrinsicWidth}x${resource.intrinsicHeight}")
                                    // 이미지뷰 상태 확인
                                    Timber.d("이미지뷰 상태 - 가시성: ${binding.ivProfile.visibility}, 크기: ${binding.ivProfile.width}x${binding.ivProfile.height}")
                                }
                                return false
                            }
                        })
                        .into(binding.ivProfile)
                } catch (e: Exception) {
                    Timber.e(e, "Glide 이미지 로딩 중 오류 발생")
                }
            }
        } else {
            Timber.d("프로필 URL이 비어있거나 이미지뷰가 null입니다")
            binding.ivProfile.setImageResource(R.drawable.ic_launcher_foreground)
        }
        
        // 설치일 이후 경과 일수 계산
        val daysSinceInstall = userPreferencesManager.getDaysSinceInstall()
        binding.tvTogetherDays.text = "${daysSinceInstall}일"
    }
    
    private fun setupEventListeners() {
        // null 체크 추가
        if (!isAdded || _binding == null) {
            Timber.e("setupEventListeners: binding이 null이거나 프래그먼트가 detach됨")
            return
        }
        
        // 지도로 돌아가기 버튼 클릭 이벤트
        binding.btnBackToMap.setOnClickListener {
            Timber.d("프로필 화면에서 지도로 돌아가기 버튼 클릭")
            // activityViewModels()로 주입받은 MainViewModel 직접 사용
            mainViewModel.navigateToMap()
        }
        
        // 툴바의 백버튼 클릭 이벤트
        binding.btnBack.setOnClickListener {
            // DrawerLayout 약한 참조 사용
            _drawerLayoutRef?.get()?.open()
        }
        
        // 회원 탈퇴 버튼 클릭 이벤트
        binding.btnWithdraw.setOnClickListener {
            showWithdrawConfirmationDialog()
        }
    }
    
    private fun showWithdrawConfirmationDialog() {
        context?.let { ctx ->
            AlertDialog.Builder(ctx)
                .setTitle("회원 탈퇴")
                .setMessage("정말로 탈퇴하시겠습니까? 모든 데이터가 삭제되며 이 작업은 되돌릴 수 없습니다.")
                .setPositiveButton("탈퇴") { _, _ ->
                    processWithdrawal()
                }
                .setNegativeButton("취소", null)
                .setCancelable(true)
                .show()
        }
    }
    
    private fun processWithdrawal() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 로딩 표시 (필요하면 구현)
                
                when (val result = authRepository.withdraw()) {
                    is Result.Success -> {
                        // 회원 탈퇴 성공
                        Timber.d("회원 탈퇴 성공")
                        Toast.makeText(context, "회원 탈퇴가 완료되었습니다.", Toast.LENGTH_SHORT).show()
                        
                        // 로그인 화면으로 이동
                        navigateToLoginScreen()
                    }
                    is Result.Failure -> {
                        // 회원 탈퇴 실패
                        Timber.e(result.exception, "회원 탈퇴 실패")
                        Toast.makeText(context, "회원 탈퇴에 실패했습니다: ${result.exception.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "회원 탈퇴 과정에서 오류 발생")
                Toast.makeText(context, "오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                // 로딩 표시 제거 (필요하면 구현)
            }
        }
    }
    
    private fun navigateToLoginScreen() {
        activity?.let { act ->
            // 로그인 화면으로 이동하고 백스택 비우기
            val intent = Intent(act, LoginTestActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            act.startActivity(intent)
            act.finish()
        }
    }
    
    private fun setupBackPressedCallback() {
        _callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Timber.d("ProfileFragment: 백버튼 이벤트 처리")
                
                // 드로어 메뉴 열기
                _drawerLayoutRef?.get()?.open() ?: run {
                    // DrawerLayout 참조가 없는 경우 MainActivity의 openDrawer 메서드 호출
                    (requireActivity() as? com.parker.hotkey.MainActivity)?.openDrawer()
                }
            }
        }
        
        // 백버튼 콜백 등록
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, _callback!!)
    }
    
    override fun onDestroyView() {
        try {
            Timber.d("ProfileFragment onDestroyView 시작")
            
            // 뷰 컨텍스트로 Glide 정리
            if (isAdded && context != null && _binding != null) {
                try {
                    Glide.with(requireContext()).clear(binding.ivProfile)
                } catch (e: Exception) {
                    Timber.e(e, "Glide 리소스 해제 중 오류")
                }
            }
            
            // 리스너 해제
            if (_binding != null) {
                binding.btnBackToMap.setOnClickListener(null)
                binding.btnBack.setOnClickListener(null)
                binding.btnWithdraw.setOnClickListener(null)
            }
            
            // 백 버튼 콜백 제거
            _callback?.remove()
            _callback = null
            
            // 약한 참조 해제
            _drawerLayoutRef?.clear()
            _drawerLayoutRef = null
            
            // ViewBinding 해제
            _binding = null
            
            Timber.d("ProfileFragment onDestroyView 완료")
            
            super.onDestroyView()
        } catch (e: Exception) {
            Timber.e(e, "onDestroyView 중 오류 발생")
            super.onDestroyView()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            // 추가 리소스 정리
            Runtime.getRuntime().gc()
            Timber.d("ProfileFragment onDestroy 완료")
        } catch (e: Exception) {
            Timber.e(e, "onDestroy 중 오류 발생")
        }
    }
    
    override fun onDetach() {
        super.onDetach()
        try {
            // 액티비티 참조 정리
            if (this::userPreferencesManager.isInitialized) {
                // 필요한 경우 추가 정리
            }
            
            Timber.d("ProfileFragment onDetach 완료")
        } catch (e: Exception) {
            Timber.e(e, "onDetach 중 오류 발생")
        }
    }
} 