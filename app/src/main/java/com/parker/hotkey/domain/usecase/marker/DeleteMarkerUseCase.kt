package com.parker.hotkey.domain.usecase.marker

import com.parker.hotkey.data.repository.MarkerRepository
import javax.inject.Inject

class DeleteMarkerUseCase @Inject constructor(
    private val markerRepository: MarkerRepository
) {
    suspend operator fun invoke(markerId: String): Result<Unit> {
        return try {
            markerRepository.deleteMarker(markerId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 