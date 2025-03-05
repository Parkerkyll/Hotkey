package com.parker.hotkey.presentation.map

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import androidx.fragment.app.DialogFragment
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
import com.parker.hotkey.presentation.state.MemoState
import com.parker.hotkey.presentation.map.MapConstants.DEFAULT_ZOOM
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import androidx.core.content.ContextCompat
import com.parker.hotkey.presentation.memo.MemoViewModel

@AndroidEntryPoint
class MemoListDialog : DialogFragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var memoInput: TextInputEditText
    private lateinit var deleteMarkerButton: Button
    private lateinit var cancelButton: Button
    private lateinit var saveButton: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: MemoAdapter
    private lateinit var markerId: String
    private lateinit var mapViewModel: MapViewModel
    private val viewModel: MemoViewModel by viewModels()
    private lateinit var rootView: View
    private var currentSnackbar: Snackbar? = null  // 스낵바 인스턴스 추적을 위한 변수 추가

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        
        // 레이아웃 인플레이트
        val inflater = requireActivity().layoutInflater
        rootView = inflater.inflate(R.layout.dialog_memo_list, null)
        
        // 뷰 바인딩
        try {
            recyclerView = rootView.findViewById(R.id.memo_list)
                ?: throw IllegalStateException("memo_list view not found")
            memoInput = rootView.findViewById(R.id.memo_input)
                ?: throw IllegalStateException("memo_input view not found")
            deleteMarkerButton = rootView.findViewById(R.id.delete_marker_button)
                ?: throw IllegalStateException("delete_marker_button view not found")
            cancelButton = rootView.findViewById(R.id.cancel_button)
                ?: throw IllegalStateException("cancel_button view not found")
            saveButton = rootView.findViewById(R.id.save_button)
                ?: throw IllegalStateException("save_button view not found")
            progressBar = rootView.findViewById(R.id.progress_bar)
                ?: throw IllegalStateException("progress_bar view not found")
            
            // 입력 필드 초기화
            memoInput.setText("")
            
            // 어댑터 초기화 및 RecyclerView 설정
            adapter = MemoAdapter(
                onDeleteClick = { memo ->
                    mapViewModel.editModeManager.validateEditMode(rootView) {
                        showDeleteMemoDialog(memo)
                    }
                }
            )
            recyclerView.layoutManager = LinearLayoutManager(context)
            recyclerView.adapter = adapter
            
            setupButtons(rootView)
            setupMemoInput()
            observeMemoState()
            observeEditMode()
            
            // 메모 데이터 로드
            viewModel.loadMemos(markerId)
            
        } catch (e: Exception) {
            Timber.e(e, "뷰 바인딩 중 오류 발생")
            throw e
        }
        
        return MaterialAlertDialogBuilder(requireContext())
            .setView(rootView)
            .create()
            .apply {
                setOnShowListener {
                    val width = resources.displayMetrics.widthPixels
                    val height = resources.displayMetrics.heightPixels
                    window?.setLayout(
                        (width * MapConstants.DIALOG_WIDTH_RATIO).toInt(),
                        (height * MapConstants.DIALOG_HEIGHT_RATIO).toInt()
                    )
                }
            }
    }

    private fun setupButtons(view: View) {
        deleteMarkerButton.setOnClickListener {
            mapViewModel.editModeManager.validateEditMode(view) {
                showDeleteMarkerDialog()
            }
        }
        
        cancelButton.setOnClickListener {
            // 지도로 돌아갈 때 기본 줌 레벨로 복귀
            (parentFragment as? MapFragment)?.setMapZoomLevel(DEFAULT_ZOOM)
            
            // 쓰기 모드인 경우 타이머 재시작
            if (isEditModeEnabled()) {
                mapViewModel.editModeManager.restartEditModeTimer()
                Timber.d("메모장 닫기 - 쓰기 모드 타이머 재시작")
            }
            
            dismiss()
        }
        
        saveButton.setOnClickListener {
            if (isEditModeEnabled()) {
                addMemo()
            } else {
                showWriteModeMessage()
            }
        }
    }
    
    private fun setupMemoInput() {
        // 입력 필드는 항상 활성화 상태로 유지
        memoInput.isEnabled = true
        
        // 클릭 리스너 설정
        memoInput.setOnClickListener {
            if (!isEditModeEnabled()) {
                showWriteModeSnackbar()
            }
        }
    }

    private fun observeMemoState() {
        lifecycleScope.launch {
            viewModel.memoState.collect { state ->
                when (state) {
                    is MemoState.Initial -> {
                        progressBar.visibility = View.GONE
                    }
                    is MemoState.Loading -> {
                        progressBar.visibility = View.VISIBLE
                    }
                    is MemoState.Success -> {
                        progressBar.visibility = View.GONE
                        adapter.submitList(state.memos)
                    }
                    is MemoState.Error -> {
                        progressBar.visibility = View.GONE
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
                viewModel.deleteMemo(memo)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showDeleteMarkerDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("마커 삭제")
            .setMessage("이 마커를 삭제하시겠습니까?\n모든 메모도 함께 삭제됩니다.")
            .setPositiveButton("삭제") { _, _ ->
                mapViewModel.deleteMarker(markerId)
                dismiss()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun addMemo() {
        val content = memoInput.text?.toString()?.trim() ?: ""
        if (content.isNotEmpty()) {
            viewModel.addMemo(markerId, content)
            memoInput.setText("")
            memoInput.clearFocus()
            
            // 메모 추가 후 타이머 재시작
            mapViewModel.editModeManager.restartEditModeTimer()
            Timber.d("메모 추가 완료 - 쓰기 모드 타이머 재시작")
        }
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
                mapViewModel.setEditMode(true)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun isEditModeEnabled(): Boolean {
        return mapViewModel.editModeManager.getCurrentMode()
    }

    private fun observeEditMode() {
        lifecycleScope.launch {
            mapViewModel.editModeManager.editModeState.collect { editMode ->
                updateUIForEditMode(editMode)
            }
        }
    }

    private fun updateUIForEditMode(editMode: Boolean) {
        // 입력 필드는 항상 활성화 상태로 유지하고, 배경만 변경
        memoInput.setBackgroundResource(
            if (editMode) R.drawable.edit_text_enabled_background
            else R.drawable.edit_text_disabled_background
        )
        
        // 입력 가능 상태 설정
        memoInput.isFocusable = editMode
        memoInput.isFocusableInTouchMode = editMode
        
        if (!editMode) {
            memoInput.clearFocus()
        }
        
        // 저장 버튼 활성화/비활성화
        saveButton.isEnabled = editMode
        
        // 마커 삭제 버튼 표시/숨김
        deleteMarkerButton.visibility = if (editMode) View.VISIBLE else View.GONE
        
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
                "메모를 작성하려면 쓰기 모드로 전환해주세요",
                Snackbar.LENGTH_LONG
            ).setAction("모드 전환") {
                mapViewModel.editModeManager.toggleEditMode()
            }.addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    super.onDismissed(transientBottomBar, event)
                    if (event != Snackbar.Callback.DISMISS_EVENT_ACTION) {
                        mapViewModel.editModeManager.setEditMode(false)
                    }
                    currentSnackbar = null  // 스낵바 참조 제거
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
        MemoListDialog.newInstance(
            markerId = markerId,
            mapViewModel = mapViewModel
        ).show(parentFragmentManager, "memo_list")
    }

    override fun onDestroyView() {
        currentSnackbar?.dismiss()  // 다이얼로그가 닫힐 때 스낵바도 함께 닫기
        currentSnackbar = null
        // 다이얼로그가 파괴될 때 쓰기 모드면 타이머 재시작
        if (isEditModeEnabled()) {
            mapViewModel.editModeManager.restartEditModeTimer()
            Timber.d("메모장 파괴 - 쓰기 모드 타이머 재시작")
        }
        super.onDestroyView()
    }
    
    companion object {
        fun newInstance(
            markerId: String,
            mapViewModel: MapViewModel
        ): MemoListDialog {
            return MemoListDialog().apply {
                this.markerId = markerId
                this.mapViewModel = mapViewModel
            }
        }
    }
} 