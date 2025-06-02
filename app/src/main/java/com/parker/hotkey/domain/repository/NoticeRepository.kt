package com.parker.hotkey.domain.repository

import com.parker.hotkey.data.model.Notice

interface NoticeRepository {
    suspend fun getNotices(): Result<List<Notice>>
    suspend fun getNoticeById(id: String): Result<Notice>
} 