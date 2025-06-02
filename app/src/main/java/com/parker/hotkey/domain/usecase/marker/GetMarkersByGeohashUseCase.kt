package com.parker.hotkey.domain.usecase.marker

import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.repository.MarkerRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMarkersByGeohashUseCase @Inject constructor(
    private val markerRepository: MarkerRepository
) {
    operator fun invoke(geohashPrefix: String, neighbors: List<String>): Flow<List<Marker>> {
        return markerRepository.getMarkers(geohashPrefix, neighbors)
    }
} 