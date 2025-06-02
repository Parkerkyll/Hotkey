package com.parker.hotkey.domain.usecase

import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.model.Memo
import com.parker.hotkey.domain.repository.MarkerRepository
import com.parker.hotkey.domain.repository.MemoRepository
import com.parker.hotkey.domain.repository.SyncRepository
import timber.log.Timber
import javax.inject.Inject
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.DelicateCoroutinesApi

/**
 * 지역 데이터 초기 로딩 및 증분 동기화를 수행하는 UseCase
 */
class SyncDataUseCase @Inject constructor(
    private val syncRepository: SyncRepository,
    private val markerRepository: MarkerRepository,
    private val memoRepository: MemoRepository
) {
    /**
     * 지역 데이터 초기 로딩
     * 
     * @param geohash 지역 코드
     * @return 성공 여부
     */
    suspend operator fun invoke(geohash: String): Boolean {
        Timber.d("지역 데이터 초기 로딩 시작: $geohash")
        
        // 네트워크 연결 확인
        if (!syncRepository.isNetworkConnected()) {
            Timber.w("네트워크 연결이 없어 초기 데이터를 로드할 수 없습니다.")
            return false
        }
        
        return try {
            // 지역 데이터 초기 로딩
            val success = syncRepository.loadInitialData(geohash)
            
            if (success) {
                Timber.d("지역 데이터 초기 로딩 성공: $geohash")
            } else {
                Timber.w("지역 데이터 초기 로딩 실패: $geohash")
            }
            
            success
        } catch (e: Exception) {
            Timber.e(e, "지역 데이터 초기 로딩 중 오류 발생: $geohash")
            false
        }
    }
    
    /**
     * LastSync 기반 증분 동기화 수행
     * 
     * @param geohash 지역 코드
     * @return 성공 여부
     */
    suspend fun syncIncrementalData(geohash: String): Boolean {
        Timber.d("LastSync 기반 증분 동기화 시작: geohash=$geohash")
        
        try {
            // geohash 관련 마커 로드 (항상 로컬 DB에서 먼저 로드)
            val markers = markerRepository.getMarkersSync(geohash, listOf(geohash))
            Timber.d("로컬 DB에서 ${markers.size}개 마커 로드됨")
            
            // 실제 필요한 메모만 포함하도록 (현재 구현에서는 모든 메모를 사용)
            val memos = memoRepository.getMemosByMarkersSync(markers.map { it.id })
            Timber.d("로컬 DB에서 ${memos.size}개 메모 로드됨")
            
            // 네트워크 연결 확인
            val isNetworkConnected = syncRepository.isNetworkConnected()
            
            // 네트워크 연결 여부와 상관없이 로컬 데이터 반환 성공으로 간주
            if (markers.isNotEmpty()) {
                Timber.d("로컬 데이터 반환 (${markers.size}개 마커)")
                
                // 네트워크 연결된 경우에만 백그라운드 동기화 실행
                if (isNetworkConnected) {
                    Timber.d("백그라운드에서 동기화 시작")
                    @OptIn(DelicateCoroutinesApi::class)
                    GlobalScope.launch {
                        try {
                            val syncSuccess = syncRepository.syncDataWithLastSync(geohash, markers, memos)
                            Timber.d("백그라운드 동기화 결과: $syncSuccess")
                        } catch (e: Exception) {
                            Timber.e(e, "백그라운드 동기화 오류")
                        }
                    }
                } else {
                    Timber.w("네트워크 연결 없음 - 백그라운드 동기화 생략")
                }
                
                return true
            }
            
            // 로컬 데이터가 없는 경우 동기화 시도
            if (isNetworkConnected) {
                Timber.d("로컬 데이터 없음, 네트워크 동기화 시도")
                return syncRepository.syncDataWithLastSync(geohash, markers, memos)
            } else {
                Timber.w("로컬 데이터 없음 & 네트워크 연결 없음 - 동기화 실패")
                return false
            }
        } catch (e: Exception) {
            Timber.e(e, "LastSync 기반 증분 동기화 중 오류 발생")
            return false
        }
    }
} 