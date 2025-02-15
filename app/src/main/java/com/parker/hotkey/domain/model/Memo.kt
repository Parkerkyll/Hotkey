package com.parker.hotkey.domain.model

data class Memo(
    val id: String,
    val markerId: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val syncedAt: Long? = null,
    val version: Long = 1
) 