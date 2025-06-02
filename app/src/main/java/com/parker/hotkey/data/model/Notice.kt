package com.parker.hotkey.data.model

data class Notice(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val priority: Int = 0 // 중요도
) 