package com.parker.hotkey.domain.repository

import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.model.Memo
import kotlinx.coroutines.flow.Flow

/**
 * 서버 동기화 기능을 제공하는 Repository 인터페이스
 */
interface SyncRepository {
    /**
     * 지역 데이터 초기 로딩
     * 
     * @param geohash 지역 코드
     * @return 성공 여부
     */
    suspend fun loadInitialData(geohash: String): Boolean
    
    /**
     * LastSync 기반 개별 동기화 수행
     * 
     * @param geohash 지역 코드(컨텍스트)
     * @param markers 동기화할 마커 목록
     * @param memos 동기화할 메모 목록
     * @return 성공 여부
     */
    suspend fun syncDataWithLastSync(
        geohash: String, 
        markers: List<Marker>,
        memos: List<Memo>
    ): Boolean
    
    /**
     * 마커 생성 및 서버 동기화
     * 
     * @param marker 생성할 마커
     * @return 서버에서 반환한 마커 (modifiedAt 등 업데이트됨)
     */
    suspend fun createMarker(marker: Marker): Marker?
    
    /**
     * 마커 삭제 및 서버 동기화
     * 
     * @param markerId 삭제할 마커 ID
     * @return 성공 여부
     */
    suspend fun deleteMarker(markerId: String): Boolean
    
    /**
     * 메모 생성 및 서버 동기화
     * 
     * @param memo 생성할 메모
     * @return 서버에서 반환한 메모 (modifiedAt 등 업데이트됨)
     */
    suspend fun createMemo(memo: Memo): Memo?
    
    /**
     * 메모 삭제 및 서버 동기화
     * 
     * @param memoId 삭제할 메모 ID
     * @param markerId 메모가 속한 마커 ID
     * @return 성공 여부
     */
    suspend fun deleteMemo(memoId: String, markerId: String): Boolean
    
    /**
     * 네트워크 연결 상태 관찰
     * 
     * @return 네트워크 연결 상태 Flow (true: 연결됨, false: 연결 안됨)
     */
    fun observeNetworkState(): Flow<Boolean>
    
    /**
     * 현재 네트워크 연결 상태 확인
     * 
     * @return 연결 상태 (true: 연결됨, false: 연결 안됨)
     */
    fun isNetworkConnected(): Boolean
} 