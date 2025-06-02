package com.parker.hotkey.data.remote.sync.api

import com.parker.hotkey.data.remote.network.SyncConfig
import com.parker.hotkey.data.remote.sync.dto.request.*
import com.parker.hotkey.data.remote.sync.dto.response.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Hotkey API 서비스 인터페이스
 */
interface HotkeyApi {
    /**
     * 증분 동기화 API
     * 
     * 두 가지 동기화 방식을 지원:
     * 1. 지역(geohash) 기반 증분 동기화: lastSyncTimestamp 사용
     * 2. 개별 항목 기반 동기화: markerSyncInfo, memoSyncInfo 사용
     * 
     * @param request 동기화 요청 객체 (SyncRequestDto)
     * @return 동기화 응답
     */
    @POST(SyncConfig.SYNC_ENDPOINT)
    suspend fun syncData(
        @Body request: SyncRequestDto
    ): Response<SyncResponseDto>
    
    /**
     * 지역 데이터 초기 로딩 API
     * 
     * @param geohash 지역 코드
     * @return 지역 데이터 응답
     */
    @GET(SyncConfig.GEOHASH_ENDPOINT)
    suspend fun getInitialData(
        @Path("geohash") geohash: String
    ): Response<GeohashResponseDto>
    
    /**
     * 마커 생성 API
     * 
     * @param request 마커 생성 요청 객체
     * @return 마커 생성 응답
     */
    @POST(SyncConfig.MARKERS_ENDPOINT)
    suspend fun createMarker(
        @Body request: CreateMarkerRequestDto
    ): Response<CreateMarkerResponseDto>
    
    /**
     * 마커 삭제 API
     * 
     * @param request 마커 삭제 요청 객체
     * @return 삭제 응답
     */
    @HTTP(method = "DELETE", path = SyncConfig.MARKERS_ENDPOINT, hasBody = true)
    suspend fun deleteMarker(
        @Body request: DeleteMarkerRequestDto
    ): Response<DeleteResponseDto>
    
    /**
     * 메모 생성 API
     * 
     * @param request 메모 생성 요청 객체
     * @return 메모 생성 응답
     */
    @POST(SyncConfig.MEMOS_ENDPOINT)
    suspend fun createMemo(
        @Body request: CreateMemoRequestDto
    ): Response<CreateMemoResponseDto>
    
    /**
     * 메모 삭제 API
     * 
     * @param request 메모 삭제 요청 객체
     * @return 삭제 응답
     */
    @HTTP(method = "DELETE", path = SyncConfig.MEMOS_ENDPOINT, hasBody = true)
    suspend fun deleteMemo(
        @Body request: DeleteMemoRequestDto
    ): Response<DeleteResponseDto>
} 