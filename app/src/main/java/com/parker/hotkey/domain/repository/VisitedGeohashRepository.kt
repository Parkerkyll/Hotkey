package com.parker.hotkey.domain.repository

import com.parker.hotkey.data.local.entity.VisitedGeohashEntity
import kotlinx.coroutines.flow.Flow

interface VisitedGeohashRepository {
    fun getVisitedGeohashesFlow(): Flow<List<VisitedGeohashEntity>>
    suspend fun getVisitedGeohashes(): List<VisitedGeohashEntity>
    suspend fun getVisitedGeohashCount(): Int
    suspend fun getUnsyncedGeohashes(): List<VisitedGeohashEntity>
}