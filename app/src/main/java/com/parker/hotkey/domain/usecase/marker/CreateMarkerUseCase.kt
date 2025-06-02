package com.parker.hotkey.domain.usecase.marker

import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.repository.MarkerRepository
import javax.inject.Inject

class CreateMarkerUseCase @Inject constructor(
    private val markerRepository: MarkerRepository
) {
    suspend operator fun invoke(userId: String, latitude: Double, longitude: Double): Result<Marker> = runCatching {
        markerRepository.createMarker(userId, latitude, longitude)
    }
} 