package com.parker.hotkey.data.repository

import com.parker.hotkey.data.local.dao.MemoDao
import com.parker.hotkey.data.local.dao.MarkerDao
import com.parker.hotkey.data.local.entity.MemoEntity
import com.parker.hotkey.data.mapper.MemoEntityMapper
import com.parker.hotkey.domain.model.LastSync
import com.parker.hotkey.domain.model.Memo
import com.parker.hotkey.domain.repository.MemoRepository
import com.parker.hotkey.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID

@Singleton
class MemoRepositoryImpl @Inject constructor(
    private val memoDao: MemoDao,
    private val markerDao: MarkerDao,
    private val syncRepository: SyncRepository,
    private val memoMapper: MemoEntityMapper
) : MemoRepository, BaseCrudRepositoryHelper<Memo, MemoEntity>() {
    
    // BaseCrudRepositoryHelper 구현
    override protected fun getAllEntities(): Flow<List<MemoEntity>> = memoDao.getAllMemos()
    
    override protected suspend fun getEntityById(id: String): MemoEntity? = memoDao.getMemoById(id)
    
    override protected fun mapToDomain(entity: MemoEntity): Memo = memoMapper.toDomain(entity)
    
    override protected fun mapToEntity(domain: Memo): MemoEntity = memoMapper.toEntity(domain)
    
    override protected suspend fun insertEntity(entity: MemoEntity) {
        memoDao.insertMemo(entity)
    }
    
    override protected suspend fun updateEntity(entity: MemoEntity) {
        memoDao.updateMemo(entity)
    }
    
    override protected suspend fun deleteEntity(id: String) {
        memoDao.getMemoById(id)?.let { memoDao.deleteMemo(it) }
    }
    
    override protected suspend fun getUnsyncedEntities(): List<MemoEntity> = memoDao.getUnsyncedMemos()
    
    override protected suspend fun updateEntitiesLastSync(ids: List<String>, syncStatus: Int, syncTimestamp: Long, syncError: String?) {
        memoDao.updateMemosLastSync(ids, syncStatus, syncTimestamp, syncError)
    }
    
    override fun getMemosByMarkerId(markerId: String): Flow<List<Memo>> =
        memoDao.getMemosByMarkerId(markerId).map { entities ->
            entities.map { memoMapper.toDomain(it) }
        }
    
    override suspend fun getMemoCount(markerId: String): Int =
        memoDao.getMemoCount(markerId)
    
    override suspend fun createMemo(userId: String, markerId: String, content: String): Memo {
        try {
            logOperation("메모 생성", "사용자ID=$userId, 마커ID=$markerId")
            
            // 마커 존재 여부 확인
            val marker = markerDao.getMarkerById(markerId)
            logOperation("메모 생성 전 마커 확인", "${marker != null}, 마커ID=$markerId")
            
            val memo = Memo(
                id = UUID.randomUUID().toString(),
                userId = userId,
                markerId = markerId,
                content = content,
                lastSync = LastSync.createInitial(),
                modifiedAt = System.currentTimeMillis()
            )
            logOperation("메모 객체 생성 완료", "ID=${memo.id}")
            
            insert(memo)
            logOperation("메모 삽입 호출 완료", "ID=${memo.id}")
            
            // 생성 후 확인
            val created = getById(memo.id)
            logOperation("메모 생성 후 확인", "${created != null}, ID=${memo.id}")
            
            return memo
        } catch (e: Exception) {
            logError(e, "메모 생성", "마커ID=$markerId")
            throw e
        }
    }
    
    override suspend fun deleteMemo(memoId: String, markerId: String) {
        try {
            logOperation("메모 삭제", "memoId=$memoId, markerId=$markerId")
            
            // 로컬 DB에서 메모 삭제
            memoDao.deleteMemoAndUpdateMarker(memoId, System.currentTimeMillis())
            
            // 서버에 메모 삭제 요청
            val serverDeleteSuccess = syncRepository.deleteMemo(memoId, markerId)
            if (!serverDeleteSuccess) {
                Timber.w("서버에서 메모 삭제 실패: memoId=$memoId - 나중에 동기화될 예정")
            } else {
                Timber.d("서버에서 메모 삭제 성공: memoId=$memoId")
            }
            
            // 메모 삭제 후 마커의 메모 개수 확인
            val remainingMemos = getMemoCount(markerId)
            if (remainingMemos == 0) {
                // 남은 메모가 없으면 마커도 삭제
                Timber.d("마커에 연결된 메모가 없음, 마커 삭제 예정: markerId=$markerId")
                
                // 서버에 마커 삭제 요청 먼저 수행
                val markerDeleteSuccess = syncRepository.deleteMarker(markerId)
                if (!markerDeleteSuccess) {
                    Timber.w("서버에서 마커 삭제 실패: markerId=$markerId - 나중에 동기화될 예정")
                } else {
                    Timber.d("서버에서 마커 삭제 성공: markerId=$markerId")
                }
                
                // 로컬 DB에서 마커 삭제 (서버 통신 결과와 관계없이 로컬 DB는 업데이트)
                markerDao.deleteMarkerWithMemos(markerId)
            }
        } catch (e: Exception) {
            logError(e, "메모 삭제", "memoId=$memoId, markerId=$markerId")
            throw e
        }
    }
    
    override suspend fun getMemosByMarkersSync(markerIds: List<String>): List<Memo> {
        if (markerIds.isEmpty()) {
            return emptyList()
        }
        
        try {
            // 최적화: markerIds 목록이 크면 배치 처리
            val MAX_BATCH_SIZE = 50 // DB 쿼리 최적화 (너무 많은 IN 절 파라미터 방지)
            
            // 결과를 담을 변수
            val allMemos = mutableListOf<Memo>()
            
            // markerIds를 배치로 나누어 처리
            val batches = markerIds.chunked(MAX_BATCH_SIZE)
            Timber.d("메모 조회: ${markerIds.size}개 마커 ID, ${batches.size}개 배치로 분할")
            
            for (batch in batches) {
                // 배치별로 처리
                val batchMemos = mutableListOf<MemoEntity>()
                
                // 각 마커 ID에 대해 메모 조회 
                batch.forEach { markerId ->
                    // 동기 함수가 없어서 Flow를 수집해야 함
                    val markerMemos = memoDao.getMemosByMarkerId(markerId).firstOrNull() ?: emptyList()
                    batchMemos.addAll(markerMemos)
                }
                
                // 배치 결과를 도메인 객체로 변환하여 추가
                val domainMemos = batchMemos.map { memoMapper.toDomain(it) }
                allMemos.addAll(domainMemos)
            }
            
            Timber.d("메모 조회 완료: ${allMemos.size}개 메모 로드됨")
            return allMemos
        } catch (e: Exception) {
            Timber.e(e, "메모 조회 중 오류 발생: markerIds=${markerIds.size}개")
            return emptyList()
        }
    }
} 