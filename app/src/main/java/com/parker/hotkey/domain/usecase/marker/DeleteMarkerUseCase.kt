package com.parker.hotkey.domain.usecase.marker

import com.parker.hotkey.domain.repository.MarkerRepository
import timber.log.Timber
import javax.inject.Inject

class DeleteMarkerUseCase @Inject constructor(
    private val markerRepository: MarkerRepository
) {
    suspend operator fun invoke(markerId: String): Result<Unit> = runCatching {
        Timber.d("DeleteMarkerUseCase: 마커 삭제 시작 - ID=$markerId")
        markerRepository.delete(markerId)
        Timber.d("DeleteMarkerUseCase: 마커 삭제 완료 - ID=$markerId")
    }
} 