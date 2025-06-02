package com.parker.hotkey.presentation.memo

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.parker.hotkey.R
import com.parker.hotkey.domain.model.Memo
import com.parker.hotkey.presentation.state.MemoUiState
import com.parker.hotkey.domain.constants.MapConstants.DEFAULT_ZOOM
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.parker.hotkey.domain.constants.UIConstants
import com.parker.hotkey.presentation.map.MapFragment
import com.parker.hotkey.presentation.map.MapViewModel
import com.parker.hotkey.databinding.DialogMemoListBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import com.parker.hotkey.domain.manager.MemoManager
import com.parker.hotkey.domain.repository.AuthRepository
import javax.inject.Inject
import android.content.DialogInterface
import com.parker.hotkey.MainActivity
import com.parker.hotkey.domain.constants.MemoConstants
import android.text.TextWatcher
import java.lang.ref.WeakReference
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.widget.TextView
import com.parker.hotkey.domain.manager.EditModeEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MemoListDialog : DialogFragment() {
    private var _binding: DialogMemoListBinding? = null
    private val binding get() = _binding!!
    private var _adapter: MemoAdapter? = null
    private val adapter get() = _adapter!!
    
    @Inject
    lateinit var memoManager: MemoManager
    
    @Inject
    lateinit var authRepository: AuthRepository
    
    // ViewModel을 WeakReference로 관리
    private var viewModelRef: WeakReference<MemoViewModel>? = null
    private val viewModel: MemoViewModel?
        get() = viewModelRef?.get()
    
    // 다이얼로그 해제 콜백
    var onDismissListener: (() -> Unit)? = null
    
    private var recyclerView: RecyclerView? = null
    private var memoInput: TextInputEditText? = null
    private var deleteMarkerButton: Button? = null
    private var cancelButton: Button? = null
    private var saveButton: MaterialButton? = null
    private var progressBar: ProgressBar? = null
    private var markerId: String? = null
    private var userId: String = ""
    private lateinit var rootView: View
    private var currentSnackbar: Snackbar? = null
    private var coroutineJob: Job? = null
    
    // 임시 마커 여부
    private var isTemporaryMarker: Boolean = false
    
    // 저장 버튼 클릭 여부 (임시 마커 저장 여부 결정)
    private var saveButtonClicked: Boolean = false
    
    // MapViewModel 참조를 WeakReference로 관리
    private var mapViewModelRef: WeakReference<MapViewModel>? = null
    
    // TextWatcher 참조 저장
    private var textWatcher: TextWatcher? = null

    // Handler 객체 생성 - 메인 루퍼 명시적으로 지정
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        markerId = arguments?.getString("markerId")
        isTemporaryMarker = arguments?.getBoolean("isTemporary", false) ?: false
        
        // MapViewModel 가져오기 및 WeakReference로 저장
        val mapFragment = parentFragment as? MapFragment
        mapFragment?.viewModel?.let {
            mapViewModelRef = WeakReference(it)
        }
        
        Timber.d("MemoListDialog 생성: markerId=$markerId, isTemporary=$isTemporaryMarker")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogMemoListBinding.inflate(layoutInflater)
        rootView = binding.root
        
        // ViewModel 초기화 및 WeakReference로 저장
        val vm = viewModels<MemoViewModel>().value
        viewModelRef = WeakReference(vm)
        
        // View 초기화
        recyclerView = binding.memoList
        memoInput = binding.memoInput
        deleteMarkerButton = binding.deleteMarkerButton
        cancelButton = binding.cancelButton
        saveButton = binding.saveButton
        progressBar = binding.progressBar

        // userId 초기화
        coroutineJob = lifecycleScope.launch {
            try {
                userId = authRepository.getUserId()
                Timber.d("userId 초기화 완료: $userId")
            } catch (e: Exception) {
                Timber.e(e, "userId 초기화 실패")
                userId = "" // 기본값 설정
                showError("사용자 정보를 가져오는데 실패했습니다.")
            }
        }

        // 입력 필드 초기화
        memoInput?.setText("")

        // 어댑터 초기화 및 RecyclerView 설정
        _adapter = MemoAdapter(
            onDeleteClick = { memo ->
                memoManager.validateEditMode(rootView) {
                    showDeleteMemoDialog(memo)
                }
            }
        )
        recyclerView?.layoutManager = LinearLayoutManager(context)
        recyclerView?.adapter = _adapter

        setupButtons(rootView)
        setupMemoInput()
        observeMemoState()
        observeEditMode()
        observeEditModeEvents()

        // 중요: 임시 마커의 경우 이전 메모 데이터가 표시되지 않도록 초기화
        if (isTemporaryMarker) {
            Timber.d("임시 마커 메모장 - 이전 메모 데이터 정리: markerId=$markerId")
            // 어댑터 초기화
            _adapter?.submitList(emptyList())
            // 메모 매니저 상태 정리
            lifecycleScope.launch {
                memoManager.clearMemos()
                delay(50) // 상태 정리 완료 대기
                
                // 메모 데이터 로드 (새로운 임시 마커는 빈 상태)
                markerId?.let { id ->
                    viewModel?.loadMemos(id)
                }
            }
        } else {
            // 일반 마커인 경우 기존 로직
            markerId?.let { id ->
                viewModel?.loadMemos(id)
            }
        }

        // 임시 마커 UI 업데이트
        updateUIForTemporaryMarker()

        // 다이얼로그 생성
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(rootView)
            .create()

        // 다이얼로그 설정
        dialog.apply {
            // 다이얼로그가 표시될 때 크기 조정
            setOnShowListener {
                val width = resources.displayMetrics.widthPixels
                val height = resources.displayMetrics.heightPixels
                window?.setLayout(
                    (width * UIConstants.DIALOG_WIDTH_RATIO).toInt(),
                    (height * UIConstants.DIALOG_HEIGHT_RATIO).toInt()
                )
                
                // 다이얼로그가 표시될 때 쓰기모드 상태 로깅
                Timber.d("다이얼로그 표시됨 - 쓰기모드 상태: ${isEditModeEnabled()}")
            }
            
            // 백키 처리 커스텀 설정 (기본 동작: 다이얼로그 닫기) - 쓰기모드 상태 유지
            setCancelable(true)
            
            // 다이얼로그 외부 클릭 시 동작 설정 - 쓰기모드 상태를 변경하지 않고 유지
            window?.decorView?.setOnTouchListener { _, _ ->
                // 터치 이벤트를 소비하지 않고 기본 동작 유지
                false
            }
        }
        
        return dialog
    }

    private fun setupButtons(view: View) {
        deleteMarkerButton?.setOnClickListener {
            memoManager.validateEditMode(view) {
                showDeleteMarkerDialog()
            }
        }
        
        cancelButton?.setOnClickListener {
            // 지도로 돌아갈 때 기본 줌 레벨로 복귀
            (parentFragment as? MapFragment)?.setMapZoomLevel(DEFAULT_ZOOM)
            
            // 쓰기 모드인 경우 타이머를 종료하지 않고 유지
            // EditModeManager가 타이머를 관리하도록 하고, 여기서는 추가 작업을 하지 않음
            Timber.d("메모장 닫기 - 쓰기모드 상태: ${isEditModeEnabled()}")
            
            dismiss()
        }
        
        saveButton?.setOnClickListener {
            val content = memoInput?.text?.toString()?.trim() ?: ""
            if (content.isNotEmpty()) {
                memoManager.validateEditMode(rootView) {
                    markerId?.let { id ->
                        viewModel?.createMemo(userId, id, content)
                        memoInput?.text?.clear()
                        
                        // 메모 저장 후 타이머 재시작 (사용자 활동으로 간주)
                        memoManager.restartEditModeTimer()
                        Timber.d("메모 저장 - 쓰기 모드 타이머 재시작")
                    }
                }
            }
        }
    }
    
    private fun setupMemoInput() {
        // 입력 필드는 항상 활성화 상태로 유지
        memoInput?.isEnabled = true
        
        // 클릭 리스너 설정
        memoInput?.setOnClickListener {
            if (!isEditModeEnabled()) {
                showWriteModeSnackbar()
            } else {
                // 쓰기 모드에서 입력 필드 클릭 시 타이머 재시작
                memoManager.restartEditModeTimer()
                Timber.d("메모 입력 필드 클릭 - 쓰기 모드 타이머 재시작")
            }
        }
        
        // 텍스트 입력 시 타이머 재시작하는 리스너 생성 및 저장
        textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isEditModeEnabled()) {
                    memoManager.restartEditModeTimer()
                    Timber.d("메모 텍스트 입력 - 쓰기 모드 타이머 재시작")
                }
            }
            
            override fun afterTextChanged(s: android.text.Editable?) {}
        }
        
        // 생성한 TextWatcher 추가
        memoInput?.addTextChangedListener(textWatcher)
    }

    private fun observeMemoState() {
        lifecycleScope.launch {
            viewModel?.memoState?.collect { state ->
                when (state) {
                    is MemoUiState.Initial -> {
                        progressBar?.visibility = View.GONE
                    }
                    is MemoUiState.Loading -> {
                        progressBar?.visibility = View.VISIBLE
                    }
                    is MemoUiState.Success -> {
                        progressBar?.visibility = View.GONE
                        
                        // 메모 목록 최대 갯수 제한
                        val limitedMemos = if (state.memos.size > MemoConstants.MAX_VISIBLE_MEMO_COUNT) {
                            Timber.d("메모 목록이 최대 표시 갯수(${MemoConstants.MAX_VISIBLE_MEMO_COUNT})를 초과하여 제한됩니다. 총 메모 수: ${state.memos.size}")
                            state.memos.take(MemoConstants.MAX_VISIBLE_MEMO_COUNT)
                        } else {
                            state.memos
                        }
                        
                        adapter.submitList(limitedMemos)
                        
                        // 메모 갯수가 최대치에 도달한 경우 안내 메시지 표시
                        if (state.memos.size >= MemoConstants.MAX_MEMO_COUNT) {
                            showLimitWarning()
                        }
                    }
                    is MemoUiState.Error -> {
                        progressBar?.visibility = View.GONE
                        showError(state.message)
                    }
                }
            }
        }
    }

    private fun showDeleteMemoDialog(memo: Memo) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("메모 삭제")
            .setMessage("이 메모를 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                memoManager.validateEditMode(rootView) {
                    viewModel?.deleteMemo(memo)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showDeleteMarkerDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("마커 삭제")
            .setMessage("이 마커를 삭제하시겠습니까?\n모든 메모도 함께 삭제됩니다.")
            .setPositiveButton("삭제") { _, _ ->
                memoManager.validateEditMode(rootView) {
                    markerId?.let { id ->
                        memoManager.deleteMarker(id)
                        dismiss()
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showError(message: String) {
        view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_SHORT)
                .setAnchorView(it.findViewById(R.id.input_layout))
                .show()
        }
    }

    private fun showWriteModeMessage() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.write_mode))
            .setMessage(getString(R.string.write_mode_message))
            .setPositiveButton(getString(R.string.to_write_mode)) { _, _ ->
                memoManager.setEditMode(true)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun isEditModeEnabled(): Boolean {
        return memoManager.getCurrentMode()
    }

    private fun observeEditMode() {
        lifecycleScope.launch {
            memoManager.editModeState.collect { editMode ->
                updateUIForEditMode(editMode)
            }
        }
    }

    private fun updateUIForEditMode(editMode: Boolean) {
        // 입력 필드는 항상 활성화 상태로 유지하고, 배경만 변경
        memoInput?.setBackgroundResource(
            if (editMode) R.drawable.edit_text_enabled_background
            else R.drawable.edit_text_disabled_background
        )
        
        // 입력 가능 상태 설정
        memoInput?.isFocusable = editMode
        memoInput?.isFocusableInTouchMode = editMode
        
        if (!editMode) {
            memoInput?.clearFocus()
        }
        
        // 저장 버튼 활성화/비활성화
        saveButton?.isEnabled = editMode
        
        // 마커 삭제 버튼 표시/숨김
        deleteMarkerButton?.visibility = if (editMode) View.VISIBLE else View.GONE
        
        // 메모 삭제 아이콘 표시/숨김
        adapter.setEditMode(editMode)
    }

    private fun showWriteModeSnackbar() {
        try {
            Timber.d("스낵바 표시 시도")
            // 이미 표시 중인 스낵바가 있다면 닫기
            currentSnackbar?.dismiss()

            // 다이얼로그 내부의 root view 사용
            currentSnackbar = Snackbar.make(
                rootView,
                getString(R.string.write_mode_message),
                Snackbar.LENGTH_LONG
            ).setAction(getString(R.string.to_write_mode)) {
                memoManager.toggleEditMode()
            }.addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    super.onDismissed(transientBottomBar, event)
                    currentSnackbar = null  // 스낵바 참조 제거
                    Timber.d("스낵바 닫힘. 이벤트 코드: $event")
                }
            })
            
            // 스낵바 설정 및 표시
            currentSnackbar?.apply { 
                setActionTextColor(ContextCompat.getColor(requireContext(), R.color.write_mode_text))
                setAnchorView(rootView.findViewById(R.id.button_container))
                show()
            }
            
            Timber.d("스낵바 표시 완료")
            
        } catch (e: Exception) {
            Timber.e(e, "스낵바 표시 중 오류 발생")
            showWriteModeMessage() // 폴백: 기존 다이얼로그 방식으로 표시
        }
    }

    private fun showMemoDialog() {
        newInstance(
            markerId = markerId
        ).show(parentFragmentManager, "memo_list")
    }

    override fun onDestroyView() {
        try {
            Timber.d("MemoListDialog onDestroyView 시작")
            
            // 다이얼로그 취소
            dialog?.dismiss()
            
            // 현재 포커스 제거 - 메모리 누수의 주요 원인
            try {
                dialog?.window?.decorView?.rootView?.clearFocus()
                binding.root.clearFocus()
                binding.memoInput.clearFocus()
                binding.saveButton.clearFocus()
                binding.cancelButton.clearFocus()
                binding.deleteMarkerButton.clearFocus()
                binding.memoList.clearFocus()
                
                // 모든 뷰 계층의 포커스 제거
                clearFocusRecursively(binding.root)
            } catch (e: Exception) {
                Timber.e(e, "포커스 제거 중 오류 발생")
            }
            
            // 코루틴 작업 취소
            coroutineJob?.cancel()
            coroutineJob = null
            
            // 스낵바 참조 해제
            currentSnackbar?.dismiss()
            currentSnackbar = null
            
            // 어댑터 참조 해제 및 데이터 정리
            _adapter?.submitList(null)
            recyclerView?.adapter = null
            recyclerView = null
            _adapter = null
            
            // 바인딩 참조 해제 전 뷰 리스너 명시적 제거
            binding.apply {
                memoInput.setOnClickListener(null)
                saveButton.setOnClickListener(null)
                cancelButton.setOnClickListener(null)
                deleteMarkerButton.setOnClickListener(null)
                root.setOnClickListener(null)
                
                // 명시적으로 포커스 가능 속성 제거
                memoInput.isFocusable = false
                memoInput.isFocusableInTouchMode = false
                saveButton.isFocusable = false
                cancelButton.isFocusable = false
                deleteMarkerButton.isFocusable = false
            }
            
            // 텍스트 와처 제거
            textWatcher?.let { 
                memoInput?.removeTextChangedListener(it)
                textWatcher = null
            }
            
            // 모든 뷰의 애니메이션 제거
            binding.root.clearAnimation()
            binding.memoInput.clearAnimation()
            binding.saveButton.clearAnimation()
            binding.cancelButton.clearAnimation()
            binding.deleteMarkerButton.clearAnimation()
            binding.memoList.clearAnimation()
            
            // Window 참조 제거
            dialog?.window?.callback = null
            
            // FragmentManager의 OnAttachListener 제거 - 메모리 누수 방지
            try {
                // 모든 OnAttachListener 제거를 위한 리플렉션 코드
                val fragmentManagerClass = androidx.fragment.app.FragmentManager::class.java
                val onAttachListenersField = fragmentManagerClass.getDeclaredField("mOnAttachListeners")
                onAttachListenersField.isAccessible = true
                val listeners = onAttachListenersField.get(parentFragmentManager)
                if (listeners is MutableCollection<*>) {
                    listeners.clear()
                    Timber.d("FragmentManager의 OnAttachListener 제거 완료")
                }
            } catch (e: Exception) {
                Timber.e(e, "FragmentManager 리스너 제거 중 오류 발생")
            }
            
            // 레이아웃 인플레이터 참조 해제
            try {
                val layoutInflater = layoutInflater
                val layoutInflaterClass = android.view.LayoutInflater::class.java
                val factoryField = layoutInflaterClass.getDeclaredField("mFactory")
                factoryField.isAccessible = true
                factoryField.set(layoutInflater, null)
                
                val factory2Field = layoutInflaterClass.getDeclaredField("mFactory2")
                factory2Field.isAccessible = true
                factory2Field.set(layoutInflater, null)
                
                val privateFactoryField = layoutInflaterClass.getDeclaredField("mPrivateFactory")
                privateFactoryField.isAccessible = true
                privateFactoryField.set(layoutInflater, null)
                
                Timber.d("LayoutInflater 참조 해제 완료")
            } catch (e: Exception) {
                Timber.e(e, "LayoutInflater 참조 해제 중 오류 발생")
            }
            
            // 바인딩 참조 해제
            _binding = null
            
            // 뷰모델 참조 약화
            viewModelRef?.clear()
            mapViewModelRef?.clear()
            
            // TextView.mLastHoveredView 정적 필드 수동 제거
            try {
                val textViewClass = android.widget.TextView::class.java
                val lastHoveredViewField = textViewClass.getDeclaredField("mLastHoveredView")
                lastHoveredViewField.isAccessible = true
                val oldValue = lastHoveredViewField.get(null)
                lastHoveredViewField.set(null, null)
                Timber.d("TextView.mLastHoveredView 정적 필드 수동 제거 완료 (이전 값: $oldValue)")
            } catch (e: Exception) {
                Timber.e(e, "TextView.mLastHoveredView 정적 필드 제거 중 오류 발생")
            }
            
            // 메모리 누수 헬퍼를 통한 뷰 계층 정리
            dialog?.window?.decorView?.let {
                com.parker.hotkey.util.MemoryLeakHelper.clearViewListeners(it)
            }
            
            Timber.d("MemoListDialog onDestroyView 완료")
        } catch (e: Exception) {
            Timber.e(e, "MemoListDialog onDestroyView 중 오류 발생")
        } finally {
            super.onDestroyView()
        }
    }

    /**
     * 뷰 계층 구조에서 재귀적으로 모든 뷰의 포커스를 제거합니다.
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

    override fun onDismiss(dialog: DialogInterface) {
        try {
            Timber.d("MemoListDialog onDismiss 시작 - 쓰기모드 상태: ${isEditModeEnabled()}")
            
            // 포커스 제거
            view?.clearFocus()
            binding.root.clearFocus()
            
            // 모든 뷰 계층의 포커스 제거
            binding.root.let { clearFocusRecursively(it) }
            
            // 버튼들 명시적으로 비활성화 - 메모리 누수 방지를 위한 추가 조치
            try {
                binding.apply {
                    // 모든 버튼 비활성화 및 리스너 제거
                    saveButton.isEnabled = false
                    cancelButton.isEnabled = false
                    deleteMarkerButton.isEnabled = false
                    
                    saveButton.setOnClickListener(null)
                    cancelButton.setOnClickListener(null)
                    deleteMarkerButton.setOnClickListener(null)
                    
                    // 명시적으로 포커스 제거
                    saveButton.clearFocus()
                    cancelButton.clearFocus()
                    deleteMarkerButton.clearFocus()
                    
                    // 포커스 불가능하게 설정
                    saveButton.isFocusable = false
                    cancelButton.isFocusable = false
                    deleteMarkerButton.isFocusable = false
                    
                    // 애니메이션 제거
                    saveButton.clearAnimation()
                    cancelButton.clearAnimation()
                    deleteMarkerButton.clearAnimation()
                }
                Timber.d("모든 버튼 참조 명시적 정리 완료")
            } catch (e: Exception) {
                Timber.e(e, "버튼 정리 중 오류 발생")
            }
            
            // TextView.mLastHoveredView 정적 필드 명시적 제거 - 최우선 처리
            try {
                val textViewClass = android.widget.TextView::class.java
                val lastHoveredViewField = textViewClass.getDeclaredField("mLastHoveredView")
                lastHoveredViewField.isAccessible = true
                val oldValue = lastHoveredViewField.get(null)
                lastHoveredViewField.set(null, null)
                Timber.d("TextView.mLastHoveredView 정적 필드 명시적 제거 완료 (이전 값: $oldValue)")
            } catch (e: Exception) {
                Timber.e(e, "TextView.mLastHoveredView 정적 필드 제거 중 오류 발생")
            }
            
            // ViewRootImpl 및 메시지 큐 관련 참조 제거 (헬퍼 메서드 사용)
            if (dialog is Dialog) {
                com.parker.hotkey.util.MemoryLeakHelper.clearDialogReferences(dialog)
                Timber.d("Dialog 참조 정리 완료")
            }
            
            // MapViewModel에 다이얼로그 닫힘을 알림
            val activity = requireActivity()
            if (activity is MainActivity) {
                val memoViewModel = activity.getMemoViewModel()
                memoViewModel?.closeMemoDialog()
                Timber.d("MainActivity에서 MemoViewModel 가져와서 closeMemoDialog 호출 완료")
            }
            
            // 콜백 실행 후 참조 해제
            onDismissListener?.invoke()
            onDismissListener = null
            
            // 임시 마커 처리 - WeakReference 사용
            val mapVM = mapViewModelRef?.get()
            if (isTemporaryMarker) {
                mapVM?.onMemoDialogDismissed(shouldSaveMarker = saveButtonClicked)
            } else {
                mapVM?.onMemoDialogDismissed()
            }
            
            // TextView.mLastHoveredView 정적 필드 수동 제거
            com.parker.hotkey.util.MemoryLeakHelper.clearAllStaticTextViewReferences()
            
            // 쓰기모드 상태 유지 (dismiss 시에도 모드 변경하지 않음)
            // EditModeManager가 상태를 관리하도록 함
            
            Timber.d("MemoListDialog onDismiss 완료")
        } catch (e: Exception) {
            Timber.e(e, "MemoListDialog onDismiss 중 오류 발생")
        } finally {
            super.onDismiss(dialog)
        }
    }

    override fun onDestroy() {
        try {
            Timber.d("MemoListDialog onDestroy 시작")
            
            // 남아있는 코루틴 작업 취소
            coroutineJob?.cancel()
            coroutineJob = null
            
            // WeakReference 정리
            viewModelRef?.clear()
            viewModelRef = null
            
            mapViewModelRef?.clear()
            mapViewModelRef = null
            
            // TextView.mLastHoveredView 정적 필드 명시적 제거 - 최우선 처리
            try {
                val textViewClass = android.widget.TextView::class.java
                val lastHoveredViewField = textViewClass.getDeclaredField("mLastHoveredView")
                lastHoveredViewField.isAccessible = true
                val oldValue = lastHoveredViewField.get(null)
                lastHoveredViewField.set(null, null)
                Timber.d("TextView.mLastHoveredView 정적 필드 명시적 제거 완료 (이전 값: $oldValue)")
            } catch (e: Exception) {
                Timber.e(e, "TextView.mLastHoveredView 정적 필드 제거 중 오류 발생")
            }
            
            // 앱 수준에서 TextView 정적 참조 정리
            com.parker.hotkey.util.MemoryLeakHelper.clearAllStaticTextViewReferences()
            
            // 버튼의 클릭 리스너 명시적으로 제거
            saveButton?.setOnClickListener(null)
            cancelButton?.setOnClickListener(null)
            deleteMarkerButton?.setOnClickListener(null)
            
            // 버튼 포커스 제거
            saveButton?.clearFocus()
            cancelButton?.clearFocus()
            deleteMarkerButton?.clearFocus()
            
            // 버튼 포커스 불가능하게 설정
            saveButton?.isFocusable = false
            cancelButton?.isFocusable = false
            deleteMarkerButton?.isFocusable = false
            
            // 메모리 누수의 주 원인인 버튼 참조 해제
            saveButton = null
            cancelButton = null
            deleteMarkerButton = null
            
            // Handler 제거 및 콜백 제거
            handler.removeCallbacksAndMessages(null)
            
            // 텍스트 와처 제거
            textWatcher?.let { memoInput?.removeTextChangedListener(it) }
            textWatcher = null
            
            // 메모 입력 필드 포커스 및 참조 해제
            memoInput?.clearFocus()
            memoInput?.isFocusable = false
            memoInput?.isFocusableInTouchMode = false
            memoInput?.setOnClickListener(null)
            memoInput = null
            
            // 리사이클러뷰 정리
            recyclerView?.adapter = null
            recyclerView?.clearOnScrollListeners()
            recyclerView?.clearFocus()
            recyclerView = null
            
            // 남아있는 뷰 리스너 정리
            try {
                // 다이얼로그 뷰 참조가 있다면 리스너 제거
                dialog?.window?.decorView?.let { decorView ->
                    // 포커스 제거
                    decorView.rootView?.clearFocus()
                    
                    // ViewGroup.mFocused 필드 리플렉션으로 정리
                    try {
                        if (decorView is ViewGroup) {
                            val viewGroupClass = android.view.ViewGroup::class.java
                            val mFocusedField = viewGroupClass.getDeclaredField("mFocused")
                            mFocusedField.isAccessible = true
                            mFocusedField.set(decorView, null)
                            Timber.d("ViewGroup.mFocused 필드 제거 완료")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "ViewGroup.mFocused 필드 제거 중 오류 발생")
                    }
                    
                    // 모든 리스너 정리
                    com.parker.hotkey.util.MemoryLeakHelper.clearViewListeners(decorView)
                }
                
                // Window 참조 제거
                dialog?.window?.callback = null
                
                // Dialog 참조는 제거하지 않음 (val이라 재할당 불가)
            } catch (e: Exception) {
                Timber.e(e, "다이얼로그 뷰 리스너 정리 중 오류 발생")
            }
            
            // FragmentManager의 참조 제거
            try {
                val fragmentManagerClass = androidx.fragment.app.FragmentManager::class.java
                
                // OnAttachListeners 정리
                val onAttachListenersField = fragmentManagerClass.getDeclaredField("mOnAttachListeners")
                onAttachListenersField.isAccessible = true
                val listeners = onAttachListenersField.get(parentFragmentManager)
                if (listeners is MutableCollection<*>) {
                    listeners.clear()
                }
                
                Timber.d("FragmentManager 참조 제거 완료")
            } catch (e: Exception) {
                Timber.e(e, "FragmentManager 참조 제거 중 오류 발생")
            }
            
            Timber.d("MemoListDialog onDestroy 완료")
        } catch (e: Exception) {
            Timber.e(e, "MemoListDialog onDestroy 중 오류 발생")
        } finally {
            super.onDestroy()
        }
    }
    
    companion object {
        fun newInstance(markerId: String?, isTemporary: Boolean = false): MemoListDialog {
            val dialog = MemoListDialog()
            val args = Bundle()
            args.putString("markerId", markerId)
            args.putBoolean("isTemporary", isTemporary)
            dialog.arguments = args
            return dialog
        }
    }

    /**
     * 임시 마커 여부에 따라 UI 업데이트
     */
    private fun updateUIForTemporaryMarker() {
        binding.apply {
            if (isTemporaryMarker) {
                // 임시 마커일 경우 저장 버튼 스타일 변경
                saveButton.apply {
                    text = "저장"
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.purple_500))
                }
                
                // 취소 버튼 표시 및 설정 - 메모리 누수 방지를 위한 개선
                cancelButton.apply {
                    visibility = View.VISIBLE
                    text = "지도로 돌아가기"
                    // 이전 리스너 제거 (중요)
                    setOnClickListener(null)
                    // 새 리스너 설정 - 약한 참조로 전환
                    setOnClickListener { _ ->
                        saveButtonClicked = false
                        dismiss()
                    }
                }
            } else {
                // 일반 마커일 경우 기본 스타일
                saveButton.apply {
                    text = "저장"
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.teal_200))
                }
                
                // 지도로 돌아가기 버튼 표시 (읽기 모드에서도 보이게 함) - 메모리 누수 방지를 위한 개선
                cancelButton.apply {
                    visibility = View.VISIBLE
                    text = "지도로 돌아가기"
                    // 이전 리스너 제거 (중요)
                    setOnClickListener(null)
                    // 새 리스너 설정 - 약한 참조로 전환
                    setOnClickListener { _ ->
                        dismiss()
                    }
                }
            }
            
            // 저장 버튼 클릭 리스너 설정 - 이전 리스너 제거 후 설정
            saveButton.setOnClickListener(null)
            saveButton.setOnClickListener { _ ->
                val content = memoInput.text.toString().trim()
                if (content.isNotEmpty()) {
                    saveButtonClicked = true
                    
                    // markerId null 안전성 처리
                    val safeMarkerId = markerId ?: ""
                    
                    // 메모 생성 처리
                    // parentFragment가 MapFragment인 경우만 처리
                    (parentFragment as? MapFragment)?.let { mapFragment ->
                        // 현재 사용자 ID 가져오기
                        lifecycleScope.launch {
                            try {
                                // 사용자 ID 가져오기
                                val userId = mapFragment.viewModel.getUserId() ?: ""
                                
                                // 메모 생성 - 임시 마커 여부 전달
                                mapFragment.viewModel.createMemo(
                                    userId, 
                                    safeMarkerId, 
                                    content, 
                                    isTemporaryMarker // 임시 마커 여부 전달
                                )
                                
                                // 다이얼로그 내부에 저장 성공 메시지 표시
                                binding.saveMessage.visibility = View.VISIBLE
                                
                                // 기존 핸들러 콜백 제거
                                handler.removeCallbacksAndMessages(null)
                                
                                // 2초 후에 메시지 숨기기
                                handler.postDelayed({
                                    try {
                                        if (isAdded && !isDetached) { // Fragment가 아직 유효한지 확인
                                            binding.saveMessage.visibility = View.GONE
                                        }
                                    } catch (e: Exception) {
                                        Timber.e(e, "저장 메시지 숨김 처리 중 오류 발생")
                                    }
                                }, 2000) // 2초 후 사라짐
                            } catch (e: Exception) {
                                Timber.e(e, "메모 생성 중 오류 발생")
                                view?.let { safeView ->
                                    Snackbar.make(safeView, "메모 생성 중 오류가 발생했습니다", Snackbar.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    
                    memoInput.text?.clear()
                } else {
                    view?.let { safeView ->
                        Snackbar.make(safeView, "메모 내용을 입력해주세요", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // 메모 갯수 제한 안내 메시지 표시
    private fun showLimitWarning() {
        view?.let {
            Snackbar.make(it, MemoConstants.MEMO_LIMIT_EXCEEDED_MESSAGE, Snackbar.LENGTH_SHORT)
                .setAnchorView(it.findViewById(R.id.input_layout))
                .show()
        }
    }

    /**
     * EditModeManager 이벤트 구독 함수 추가
     */
    private fun observeEditModeEvents() {
        // 이벤트 구독 Job을 coroutineJob에 연결하여 DialogFragment 소멸 시 함께 취소되도록 함
        coroutineJob = lifecycleScope.launch {
            try {
                // 이벤트 구독 설정
                var active = true // 이벤트 처리 활성화 상태
                val job = memoManager.subscribeToEditModeEvents { event ->
                    // 이벤트 처리가 활성화 상태가 아니면 처리하지 않음
                    if (!active) return@subscribeToEditModeEvents
                    
                    when (event) {
                        is EditModeEvent.TimerExpired -> {
                            Timber.d("메모장 다이얼로그에서 타이머 만료 이벤트 수신: 읽기모드로 전환")
                            // 메인 스레드에서 UI 업데이트
                            if (isAdded && !isDetached) {
                                lifecycleScope.launch(Dispatchers.Main) {
                                    updateUIForEditMode(false)
                                }
                            }
                        }
                        is EditModeEvent.ModeChanged -> {
                            Timber.d("메모장 다이얼로그에서 모드 변경 이벤트 수신: ${if(event.isEditMode) "쓰기" else "읽기"}모드")
                            // 메인 스레드에서 UI 업데이트
                            if (isAdded && !isDetached) {
                                lifecycleScope.launch(Dispatchers.Main) {
                                    updateUIForEditMode(event.isEditMode)
                                }
                            }
                        }
                        else -> { /* 다른 이벤트는 무시 */ }
                    }
                }
                
                // 다이얼로그가 종료될 때 job이 취소되도록 추가
                this.coroutineContext.job.invokeOnCompletion {
                    active = false // 이벤트 처리 비활성화
                    job.cancel()
                    Timber.d("EditMode 이벤트 구독 취소됨")
                }
            } catch (e: Exception) {
                Timber.e(e, "EditMode 이벤트 구독 중 오류 발생")
            }
        }
    }

    /**
     * 다이얼로그가 일시 정지될 때 (백그라운드로 갈 때)
     * 쓰기모드 타이머 상태를 유지하도록 처리
     */
    override fun onPause() {
        val isEditMode = isEditModeEnabled()
        Timber.d("MemoListDialog onPause - 쓰기모드 상태: $isEditMode, 타이머 남은 시간: ${memoManager.getRemainingTimeMs()}ms")
        
        // 백그라운드로 갈 때 쓰기모드를 유지하기 위해 상태를 보존
        // EditModeManager가 상태를 관리하도록 하고 여기서는 추가 작업을 하지 않음
        
        // 현재 메모장 내에서의 쓰기모드 상태를 안전하게 저장
        if (isEditMode) {
            Timber.d("메모장이 백그라운드로 전환 - 쓰기모드 유지 중")
        }
        
        super.onPause()
    }
    
    /**
     * 다이얼로그가 다시 화면에 표시될 때
     * 쓰기모드 상태 확인 및 UI 업데이트
     */
    override fun onResume() {
        super.onResume()
        
        // 현재 EditMode 상태를 가져와 UI에 반영
        val isEditMode = isEditModeEnabled()
        Timber.d("MemoListDialog onResume - 쓰기모드 상태: $isEditMode, 타이머 남은 시간: ${memoManager.getRemainingTimeMs()}ms")
        
        // UI 상태 업데이트
        updateUIForEditMode(isEditMode)
        
        // 사용자가 다시 화면을 보는 것은 활동으로 간주
        // 쓰기모드인 경우에만 타이머 재시작
        if (isEditMode) {
            memoManager.restartEditModeTimer()
            Timber.d("메모장 다이얼로그 다시 표시 - 쓰기모드 타이머 재시작")
        }
    }
    
    /**
     * 다이얼로그가 백그라운드로 완전히 이동될 때
     * 쓰기모드 유지를 위한 처리
     */
    override fun onStop() {
        val isEditMode = isEditModeEnabled()
        Timber.d("MemoListDialog onStop - 쓰기모드 상태: $isEditMode, 타이머 남은 시간: ${memoManager.getRemainingTimeMs()}ms")
        
        // EditModeManager가 상태를 유지하도록 하고 
        // 여기서는 추가 작업을 하지 않음
        
        // 현재 쓰기모드면 명시적으로 로깅
        if (isEditMode) {
            Timber.d("메모장이 완전히 백그라운드로 전환 - 쓰기모드 상태를 유지해야 함")
        }
        
        super.onStop()
    }
    
    /**
     * 다이얼로그가 다시 화면에 표시되기 직전
     */
    override fun onStart() {
        super.onStart()
        
        // 최신 EditMode 상태를 가져와 UI에 즉시 반영
        val isEditMode = isEditModeEnabled()
        Timber.d("MemoListDialog onStart - 쓰기모드 상태: $isEditMode, 타이머 남은 시간: ${memoManager.getRemainingTimeMs()}ms")
        
        // UI만 업데이트 (타이머 재시작은 onResume에서 처리)
        updateUIForEditMode(isEditMode)
        
        // 상태 확인 로깅
        Timber.d("메모장이 백그라운드에서 복귀 - 이전 쓰기모드 상태가 유지되어야 함")
    }
} 