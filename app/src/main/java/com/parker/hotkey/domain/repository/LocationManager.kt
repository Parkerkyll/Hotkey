package com.parker.hotkey.domain.repository

import com.parker.hotkey.domain.model.Location
import kotlinx.coroutines.flow.Flow

interface LocationManager {
    fun getLocationUpdates(): Flow<Location>
    fun hasLocationPermission(): Boolean
} 