package com.parker.hotkey.domain.usecase.marker

import com.parker.hotkey.data.local.entity.MarkerEntity
import com.parker.hotkey.data.repository.MarkerRepository
import javax.inject.Inject

class CreateMarkerUseCase @Inject constructor(
    private val markerRepository: MarkerRepository
) {
    suspend operator fun invoke(latitude: Double, longitude: Double, geohash: String): Result<MarkerEntity> {
        return try {
            val marker = markerRepository.createMarker(latitude, longitude, geohash)
            Result.success(marker)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 