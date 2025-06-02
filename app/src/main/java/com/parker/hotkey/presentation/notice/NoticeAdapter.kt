package com.parker.hotkey.presentation.notice

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.parker.hotkey.data.model.Notice
import com.parker.hotkey.databinding.ItemNoticeBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoticeAdapter(private val onItemClick: (Notice) -> Unit) : 
    ListAdapter<Notice, NoticeAdapter.NoticeViewHolder>(NoticeDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoticeViewHolder {
        val binding = ItemNoticeBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return NoticeViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: NoticeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class NoticeViewHolder(private val binding: ItemNoticeBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }
        
        fun bind(notice: Notice) {
            binding.tvTitle.text = notice.title
            binding.tvDate.text = formatDate(notice.createdAt)
            
            // 우선순위에 따른 색상 설정
            val color = when(notice.priority) {
                0 -> Color.GRAY
                1 -> Color.BLUE
                2 -> Color.parseColor("#FFA500") // 주황색
                else -> Color.RED
            }
            binding.priorityIndicator.setBackgroundColor(color)
        }
        
        private fun formatDate(timestamp: Long): String {
            val sdf = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
            // 초 단위 타임스탬프를 밀리초로 변환
            return sdf.format(Date(timestamp * 1000))
        }
    }
    
    class NoticeDiffCallback : DiffUtil.ItemCallback<Notice>() {
        override fun areItemsTheSame(oldItem: Notice, newItem: Notice): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: Notice, newItem: Notice): Boolean {
            return oldItem == newItem
        }
    }
} 