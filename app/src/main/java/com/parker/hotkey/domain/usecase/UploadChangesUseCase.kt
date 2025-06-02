package com.parker.hotkey.domain.usecase

import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.model.Memo
import com.parker.hotkey.domain.repository.MarkerRepository
import com.parker.hotkey.domain.repository.SyncRepository
import com.parker.hotkey.data.remote.sync.util.SyncException
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject

/**
 * 마커와 메모의 생성 및 삭제를 서버에 업로드하는 UseCase
 */
class UploadChangesUseCase @Inject constructor(
    private val syncRepository: SyncRepository,
    private val markerRepository: MarkerRepository
) {
    /**
     * 마커 생성 및 서버 업로드
     * 
     * @param marker 생성할 마커
     * @return 서버에서 업데이트된 마커 또는 null (실패 시)
     */
    suspend fun uploadMarker(marker: Marker): Marker? {
        Timber.d("마커 업로드 시작: ${marker.id}")
        
        // 네트워크 연결 확인
        if (!syncRepository.isNetworkConnected()) {
            Timber.w("네트워크 연결이 없어 마커를 업로드할 수 없습니다.")
            return null
        }
        
        return try {
            // 마커 서버에 업로드
            val updatedMarker = syncRepository.createMarker(marker)
            
            if (updatedMarker != null) {
                Timber.d("마커 업로드 성공: ${updatedMarker.id}")
            } else {
                Timber.w("마커 업로드 실패: ${marker.id}")
            }
            
            updatedMarker
        } catch (e: Exception) {
            Timber.e(e, "마커 업로드 중 오류 발생: ${marker.id}")
            null
        }
    }
    
    /**
     * 메모 생성 및 서버 업로드
     * 메모 업로드 전에 관련 마커가 서버에 있는지 확인하고 필요하면 마커를 먼저 업로드합니다.
     * 
     * @param memo 생성할 메모
     * @param marker 연결된 마커 (옵션). 제공되면 마커 조회 과정을 건너뜁니다.
     * @return 서버에서 업데이트된 메모 또는 null (실패 시)
     */
    suspend fun uploadMemo(memo: Memo, marker: Marker? = null): Memo? {
        Timber.d("메모 업로드 시작: ${memo.id}, 마커: ${memo.markerId}")
        
        // 네트워크 연결 확인
        if (!syncRepository.isNetworkConnected()) {
            Timber.w("네트워크 연결이 없어 메모를 업로드할 수 없습니다.")
            return null
        }
        
        // 재시도 횟수 관리 
        var retriesLeft = 2
        
        while (retriesLeft >= 0) {
            try {
                // 메모 서버에 업로드 시도
                val updatedMemo = syncRepository.createMemo(memo)
                
                if (updatedMemo != null) {
                    Timber.d("메모 업로드 성공: ${updatedMemo.id}")
                    return updatedMemo
                } else {
                    // 업로드 실패했지만 예외가 발생하지 않은 경우
                    Timber.w("메모 업로드 실패: ${memo.id}")
                    return null
                }
            } catch (e: Exception) {
                // 마커가 존재하지 않는 경우 특별 처리
                if (e is SyncException.MarkerNotFoundError) {
                    if (retriesLeft > 0) {
                        // 마커 ID 추출
                        val markerId = e.markerId ?: memo.markerId
                        Timber.d("마커가 서버에 없음, 마커 업로드 시도: $markerId")
                        
                        // 마커를 가져와 업로드
                        val uploadedMarker = marker ?: uploadMarkerById(markerId)
                        
                        if (uploadedMarker != null) {
                            Timber.d("마커 업로드 성공, 메모 업로드 재시도")
                            retriesLeft--
                            // 잠시 대기 후 메모 업로드 재시도
                            kotlinx.coroutines.delay(500)
                            continue
                        } else {
                            Timber.e("마커 업로드 실패, 메모 업로드 중단")
                            return null
                        }
                    } else {
                        Timber.e("최대 재시도 횟수 초과: ${memo.id}")
                        return null
                    }
                } else {
                    // 다른 예외는 그대로 처리
                    Timber.e(e, "메모 업로드 중 오류 발생: ${memo.id}")
                    return null
                }
            }
        }
        
        return null
    }
    
    /**
     * ID로 마커를 조회하여 서버에 업로드
     * 
     * @param markerId 마커 ID
     * @return 업로드된 마커 또는 null (실패 시)
     */
    private suspend fun uploadMarkerById(markerId: String): Marker? {
        try {
            // 마커 객체를 로컬 DB에서 조회
            val marker = markerRepository.getById(markerId)
            
            if (marker != null) {
                Timber.d("마커를 찾았습니다. 업로드 시도: $markerId")
                // 마커 업로드
                return uploadMarker(marker)
            } else {
                Timber.e("마커를 로컬 DB에서 찾을 수 없음: $markerId")
                return null
            }
        } catch (e: Exception) {
            Timber.e(e, "마커 조회 및 업로드 중 오류 발생: $markerId")
            return null
        }
    }
    
    /**
     * 마커 삭제 및 서버 동기화
     * 
     * @param markerId 삭제할 마커 ID
     * @return 성공 여부
     */
    suspend fun deleteMarker(markerId: String): Boolean {
        Timber.d("마커 삭제 업로드 시작: $markerId")
        
        // 네트워크 연결 확인
        if (!syncRepository.isNetworkConnected()) {
            Timber.w("네트워크 연결이 없어 마커 삭제를 업로드할 수 없습니다.")
            return false
        }
        
        return try {
            // 마커 삭제 서버에 업로드
            val success = syncRepository.deleteMarker(markerId)
            
            if (success) {
                Timber.d("마커 삭제 업로드 성공: $markerId")
            } else {
                Timber.w("마커 삭제 업로드 실패: $markerId")
            }
            
            success
        } catch (e: Exception) {
            Timber.e(e, "마커 삭제 업로드 중 오류 발생: $markerId")
            false
        }
    }
    
    /**
     * 메모 삭제 및 서버 동기화
     * 
     * @param memoId 삭제할 메모 ID
     * @param markerId 메모가 속한 마커 ID
     * @return 성공 여부
     */
    suspend fun deleteMemo(memoId: String, markerId: String): Boolean {
        Timber.d("메모 삭제 업로드 시작: $memoId, 마커: $markerId")
        
        // 네트워크 연결 확인
        if (!syncRepository.isNetworkConnected()) {
            Timber.w("네트워크 연결이 없어 메모 삭제를 업로드할 수 없습니다.")
            return false
        }
        
        return try {
            // 메모 삭제 서버에 업로드
            val success = syncRepository.deleteMemo(memoId, markerId)
            
            if (success) {
                Timber.d("메모 삭제 업로드 성공: $memoId")
            } else {
                Timber.w("메모 삭제 업로드 실패: $memoId")
            }
            
            success
        } catch (e: Exception) {
            Timber.e(e, "메모 삭제 업로드 중 오류 발생: $memoId")
            false
        }
    }
} 