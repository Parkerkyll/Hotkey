package com.parker.hotkey.domain.model

import com.naver.maps.geometry.LatLng

data class Marker(
    override val id: String,
    val userId: String,
    override val lastSync: LastSync = LastSync.createInitial(),
    override val modifiedAt: Long = System.currentTimeMillis(),
    override val latitude: Double,
    override val longitude: Double,
    override val geohash: String,
    val position: LatLng = LatLng(latitude, longitude),
    val memos: List<Memo> = emptyList(),
    
    val state: MarkerState = MarkerState.TEMPORARY,
    val lastSyncedAt: Long? = null
) : Synchronizable, LocationBased {
    
    val isPersisted: Boolean
        get() = state == MarkerState.PERSISTED
        
    fun markAsPersisted(): Marker = copy(
        state = MarkerState.PERSISTED,
        lastSyncedAt = System.currentTimeMillis()
    )
    
    fun markAsDeleted(): Marker = copy(
        state = MarkerState.DELETED
    )
    
    override fun markAsSynced(serverVersion: Long): Marker {
        return copy(
            lastSync = LastSync.createSuccess(serverVersion),
            state = MarkerState.PERSISTED,
            lastSyncedAt = System.currentTimeMillis()
        )
    }
    
    override fun markAsFailed(): Marker {
        return copy(
            lastSync = LastSync.createError()
        )
    }
    
    override fun markAsConflict(serverVersion: Long): Marker {
        return copy(
            lastSync = LastSync.createConflict(serverVersion)
        )
    }
} 