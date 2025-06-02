package com.parker.hotkey.domain.manager

import com.parker.hotkey.di.qualifier.ApplicationScope
import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.model.MarkerState
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber
import dagger.Lazy

/**
 * 기존 코드와 새로운 상태 기반 접근법을 연결하는 브릿지 역할을 하는 어댑터 클래스
 * 순환 의존성을 해결하기 위해 Lazy 패턴 적용
 */
@Singleton
class MarkerStateAdapter @Inject constructor(
    private val markerManagerLazy: Lazy<MarkerManager>,
    private val temporaryMarkerManagerLazy: Lazy<TemporaryMarkerManager>,
    @ApplicationScope private val coroutineScope: CoroutineScope
) {
    // 필요할 때만 초기화되는 속성
    private val markerManager by lazy { markerManagerLazy.get() }
    private val temporaryMarkerManager by lazy { temporaryMarkerManagerLazy.get() }
    
    /**
     * 마커가 임시 마커인지 확인
     *
     * @param markerId 마커 ID
     * @return 임시 마커 여부
     */
    fun isTemporaryMarker(markerId: String): Boolean {
        return temporaryMarkerManager.isTemporaryMarker(markerId)
    }
    
    /**
     * 마커의 상태 반환 (기존 시스템의 상태 기반으로)
     *
     * @param markerId 마커 ID
     * @return 마커 상태
     */
    fun getMarkerState(markerId: String): MarkerState {
        return if (temporaryMarkerManager.isTemporaryMarker(markerId)) {
            MarkerState.TEMPORARY
        } else {
            val marker = markerManager.getMarkerById(markerId)
            when {
                marker != null -> MarkerState.PERSISTED
                else -> MarkerState.DELETED // 찾을 수 없는 경우
            }
        }
    }
    
    /**
     * 마커를 영구 마커로 상태 전이
     *
     * @param markerId 마커 ID
     */
    fun makeMarkerPersisted(markerId: String) {
        if (isTemporaryMarker(markerId)) {
            temporaryMarkerManager.makeMarkerPermanent(markerId)
            Timber.d("마커 ID: $markerId - 영구 마커로 전환됨")
        }
    }
    
    /**
     * 마커 객체에 상태 정보 추가
     *
     * @param marker 마커 객체
     * @return 상태 정보가 추가된 마커 객체
     */
    fun enrichMarkerWithState(marker: Marker): Marker {
        val state = getMarkerState(marker.id)
        return marker.copy(state = state)
    }
} 