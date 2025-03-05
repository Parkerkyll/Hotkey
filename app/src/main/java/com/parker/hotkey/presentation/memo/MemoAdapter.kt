package com.parker.hotkey.presentation.map

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.parker.hotkey.R
import com.parker.hotkey.domain.model.Memo

class MemoAdapter(
    private val onDeleteClick: (Memo) -> Unit
) : ListAdapter<Memo, MemoAdapter.MemoViewHolder>(MemoDiffCallback) {

    private var isEditMode = false

    fun setEditMode(editMode: Boolean) {
        isEditMode = editMode
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_memo, parent, false)
        return MemoViewHolder(view, onDeleteClick)
    }

    override fun onBindViewHolder(holder: MemoViewHolder, position: Int) {
        holder.bind(getItem(position), isEditMode)
    }

    class MemoViewHolder(
        itemView: View,
        private val onDeleteClick: (Memo) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val contentTextView: TextView = itemView.findViewById(R.id.memo_content)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.delete_button)

        fun bind(memo: Memo, isEditMode: Boolean) {
            contentTextView.text = memo.content
            
            // 편집 모드에 따라 삭제 버튼 표시/숨김
            deleteButton.visibility = if (isEditMode) View.VISIBLE else View.GONE
            
            deleteButton.setOnClickListener { 
                MaterialAlertDialogBuilder(itemView.context)
                    .setTitle("메모 삭제")
                    .setMessage("이 메모를 삭제하시겠습니까?")
                    .setPositiveButton("삭제") { _, _ ->
                        onDeleteClick(memo)
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }
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