package com.parker.hotkey.data.model

import com.parker.hotkey.domain.model.LastSync

data class EntityMetadata(
    val id: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val lastSync: LastSync,
    val version: Long
) 