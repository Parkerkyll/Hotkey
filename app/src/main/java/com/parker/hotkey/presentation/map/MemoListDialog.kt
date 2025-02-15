package com.parker.hotkey.presentation.map

import android.app.Dialog
import android.os.Bundle
import android.widget.Button
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
import kotlinx.coroutines.launch
import timber.log.Timber

class MemoListDialog : DialogFragment() {
    private var onAddMemo: ((String) -> Unit)? = null
    private var onDeleteMemo: ((Memo) -> Unit)? = null
    private var onDeleteMarker: (() -> Unit)? = null
    private lateinit var markerId: String
    private lateinit var memos: List<Memo>
    private lateinit var mapViewModel: MapViewModel
    private val viewModel: MemoListViewModel by viewModels()
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var memoInput: TextInputEditText
    private lateinit var deleteMarkerButton: Button
    private lateinit var cancelButton: Button
    private lateinit var addButton: MaterialButton
    private lateinit var adapter: MemoAdapter
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_memo_list, null)
        
        recyclerView = view.findViewById(R.id.memo_list)
        memoInput = view.findViewById(R.id.memo_input)
        deleteMarkerButton = view.findViewById(R.id.delete_marker_button)
        cancelButton = view.findViewById(R.id.cancel_button)
        
        // 저장 버튼 설정
        view.findViewById<MaterialButton>(R.id.save_button).setOnClickListener {
            addMemo()
        }
        
        setupRecyclerView()
        setupMemoInput()
        setupDeleteMarkerButton()
        setupCancelButton()
        
        return MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .create()
            .apply {
                setOnShowListener {
                    val width = resources.displayMetrics.widthPixels
                    val height = resources.displayMetrics.heightPixels
                    window?.setLayout((width * 0.95).toInt(), (height * 0.9).toInt())
                }
            }
    }

    override fun onStart() {
        super.onStart()
        // Dialog가 시작되면 메모 목록 관찰 시작
        lifecycleScope.launch {
            mapViewModel.mapState.collect { state ->
                when (state) {
                    is MapState.Success -> {
                        adapter.submitList(state.selectedMarkerMemos)
                    }
                    is MapState.Error -> {
                        showError(state.error.message)
                    }
                    else -> {
                        // Initial과 Loading 상태는 무시
                    }
                }
            }
        }
    }
    
    private fun setupRecyclerView() {
        adapter = MemoAdapter { memo ->
            onDeleteMemo?.invoke(memo)
        }
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        adapter.submitList(memos)
    }
    
    private fun setupMemoInput() {
        memoInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun setupDeleteMarkerButton() {
        deleteMarkerButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("마커 삭제")
                .setMessage("이 마커를 삭제하시겠습니까?\n모든 메모도 함께 삭제됩니다.")
                .setPositiveButton("삭제") { _, _ ->
                    onDeleteMarker?.invoke()
                    viewModel.returnToMapView()
                    dismiss()
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    private fun setupCancelButton() {
        cancelButton.setOnClickListener {
            viewModel.returnToMapView()
            // 지도로 돌아갈 때 기본 줌 레벨로 복귀
            (parentFragment as? MapFragment)?.setMapZoomLevel(MapFragment.DEFAULT_ZOOM)
            dismiss()
        }
    }

    private fun addMemo() {
        val content = memoInput.text?.toString()?.trim() ?: ""
        Timber.d("메모 추가 시도: $content")
        if (content.isNotEmpty()) {
            onAddMemo?.let { callback ->
                Timber.d("메모 추가 콜백 실행")
                callback(content)
                memoInput.text?.clear()
            } ?: run {
                Timber.e("메모 추가 콜백이 null입니다")
                showError("메모를 추가할 수 없습니다")
            }
        }
    }

    private fun showError(message: String) {
        view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
    
    companion object {
        fun newInstance(
            markerId: String,
            memos: List<Memo>,
            mapViewModel: MapViewModel,
            onAddMemo: (String) -> Unit,
            onDeleteMemo: (Memo) -> Unit,
            onDeleteMarker: () -> Unit
        ): MemoListDialog {
            return MemoListDialog().apply {
                this.markerId = markerId
                this.memos = memos
                this.mapViewModel = mapViewModel
                this.onAddMemo = onAddMemo
                this.onDeleteMemo = onDeleteMemo
                this.onDeleteMarker = onDeleteMarker
            }
        }
    }
} 