package com.parker.hotkey.presentation.memo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.parker.hotkey.R
import com.parker.hotkey.domain.model.Memo
import timber.log.Timber
import java.lang.ref.WeakReference

class MemoAdapter(
    private var onDeleteClick: (Memo) -> Unit
) : ListAdapter<Memo, MemoAdapter.MemoViewHolder>(MemoDiffCallback) {

    private var isEditMode = false
    
    // 리스너 참조를 교체하기 위한 프로퍼티
    var listener: ((Memo) -> Unit)? 
        get() = onDeleteClick
        set(value) {
            onDeleteClick = value ?: { _ -> }
        }
    
    // 아이템 목록 정리 메서드
    fun clearItems() {
        submitList(null)
        currentList.clear()
        Timber.d("MemoAdapter - 아이템 목록 정리 완료")
    }

    fun setEditMode(editMode: Boolean) {
        isEditMode = editMode
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_memo, parent, false)
        return MemoViewHolder(view) { memo ->
            onDeleteClick(memo)
        }
    }

    override fun onBindViewHolder(holder: MemoViewHolder, position: Int) {
        holder.bind(getItem(position), isEditMode)
    }
    
    // 뷰 홀더가 재활용될 때 리소스 정리
    override fun onViewRecycled(holder: MemoViewHolder) {
        holder.unbind()
        super.onViewRecycled(holder)
    }

    class MemoViewHolder(
        itemView: View,
        private val onDeleteClick: (Memo) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val contentTextView: TextView = itemView.findViewById(R.id.memo_content)
        private val moreButton: TextView = itemView.findViewById(R.id.more_button)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.delete_button)
        private var currentMemo: WeakReference<Memo>? = null
        private var expanded = false

        fun bind(memo: Memo, isEditMode: Boolean) {
            // 약한 참조로 현재 메모 저장
            currentMemo = WeakReference(memo)
            
            contentTextView.text = memo.content

            // 텍스트 확장 상태 초기화
            expanded = false
            contentTextView.maxLines = 3
            moreButton.text = "더 보기"
            
            // 텍스트뷰가 레이아웃에 배치된 후 라인 수 계산
            contentTextView.post {
                setupExpandableText()
            }
            
            // 편집 모드에 따라 삭제 버튼 표시/숨김
            deleteButton.visibility = if (isEditMode) View.VISIBLE else View.GONE
            
            // 리스너 설정 전 기존 리스너 제거
            deleteButton.setOnClickListener(null)
            
            // 새 리스너 설정 - 편집 모드일 때만 클릭 가능하도록 수정
            if (isEditMode) {
                deleteButton.setOnClickListener { 
                    currentMemo?.get()?.let { memo ->
                        onDeleteClick(memo)
                    }
                }
            }
        }
        
        private fun setupExpandableText() {
            // 현재 레이아웃에서 라인 수 계산
            val lineCount = contentTextView.lineCount
            
            if (lineCount <= 3) {
                // 3줄 이하면 더보기 버튼 숨김
                moreButton.visibility = View.GONE
                contentTextView.maxLines = lineCount
            } else {
                // 3줄 초과면 더보기 버튼 표시
                contentTextView.maxLines = 3
                moreButton.visibility = View.VISIBLE
                
                // 더보기/접기 버튼 클릭 이벤트
                moreButton.setOnClickListener {
                    expanded = !expanded
                    if (expanded) {
                        // 펼치기
                        contentTextView.maxLines = Integer.MAX_VALUE
                        moreButton.text = "접기"
                    } else {
                        // 접기
                        contentTextView.maxLines = 3
                        moreButton.text = "더 보기"
                    }
                    
                    // 애니메이션 효과 추가
                    TransitionManager.beginDelayedTransition(
                        itemView as ViewGroup, 
                        AutoTransition().setDuration(200)
                    )
                }
            }
        }
        
        // 바인딩 해제 및 리소스 정리
        fun unbind() {
            deleteButton.setOnClickListener(null)
            moreButton.setOnClickListener(null)
            currentMemo?.clear()
            currentMemo = null
        }
    }

    object MemoDiffCallback : DiffUtil.ItemCallback<Memo>() {
        override fun areItemsTheSame(oldItem: Memo, newItem: Memo): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Memo, newItem: Memo): Boolean {
            return oldItem == newItem
        }
    }
} 