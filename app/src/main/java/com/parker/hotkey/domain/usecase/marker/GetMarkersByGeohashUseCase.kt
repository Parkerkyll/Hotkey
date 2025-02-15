package com.parker.hotkey.domain.usecase.marker

import com.parker.hotkey.data.local.entity.MarkerEntity
import com.parker.hotkey.data.repository.MarkerRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMarkersByGeohashUseCase @Inject constructor(
    private val markerRepository: MarkerRepository
) {
    operator fun invoke(geohashPrefix: String): Flow<List<MarkerEntity>> {
        return markerRepository.getMarkersByGeohash(geohashPrefix)
    }
} 