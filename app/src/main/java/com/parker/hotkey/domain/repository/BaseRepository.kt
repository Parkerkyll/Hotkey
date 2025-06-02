package com.parker.hotkey.domain.repository

import com.parker.hotkey.domain.model.LastSync
import com.parker.hotkey.domain.model.Synchronizable
import kotlinx.coroutines.flow.Flow

interface BaseRepository<T : Synchronizable> {
    fun getAll(): Flow<List<T>>
    suspend fun getById(id: String): T?
    suspend fun insert(item: T)
    suspend fun update(item: T)
    suspend fun delete(id: String)
    suspend fun getUnsyncedItems(): List<T>
    suspend fun updateLastSync(ids: List<String>, lastSync: LastSync)
} 