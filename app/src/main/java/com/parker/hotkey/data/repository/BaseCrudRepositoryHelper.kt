package com.parker.hotkey.data.repository

import com.parker.hotkey.domain.model.Synchronizable
import com.parker.hotkey.domain.model.LastSync
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

/**
 * 기본 CRUD 작업을 추상화한 Repository 헬퍼 클래스
 * 
 * @param T 도메인 모델 타입 (Synchronizable 구현체)
 * @param E 데이터베이스 엔티티 타입
 */
abstract class BaseCrudRepositoryHelper<T : Synchronizable, E : Any> : BaseRepositoryHelper() {
    
    // 추상 메서드: 구현체에서 정의해야 하는 메서드들
    protected abstract fun getAllEntities(): Flow<List<E>>
    protected abstract suspend fun getEntityById(id: String): E?
    protected abstract fun mapToDomain(entity: E): T
    protected abstract fun mapToEntity(domain: T): E
    protected abstract suspend fun insertEntity(entity: E)
    protected abstract suspend fun updateEntity(entity: E)
    protected abstract suspend fun deleteEntity(id: String)
    protected abstract suspend fun getUnsyncedEntities(): List<E>
    protected abstract suspend fun updateEntitiesLastSync(ids: List<String>, syncStatus: Int, syncTimestamp: Long, syncError: String?)
    
    // 공통 구현: 모든 아이템 조회
    fun getAll(): Flow<List<T>> = getAllEntities().map { entities ->
        entities.map { mapToDomain(it) }.also {
            logOperation("모든 아이템 조회", "${it.size}개 로드됨")
        }
    }
    
    // 공통 구현: ID로 아이템 조회
    suspend fun getById(id: String): T? {
        return try {
            getEntityById(id)?.let { entity ->
                mapToDomain(entity).also {
                    logOperation("ID로 아이템 조회", "ID=$id, 성공")
                }
            }
        } catch (e: Exception) {
            logError(e, "ID로 아이템 조회", "ID=$id")
            null
        }
    }
    
    // 공통 구현: 아이템 삽입
    suspend fun insert(item: T) {
        try {
            logOperation("아이템 삽입", "ID=${item.id} 시작")
            insertEntity(mapToEntity(item))
            logOperation("아이템 삽입", "ID=${item.id} 완료")
        } catch (e: Exception) {
            logError(e, "아이템 삽입", "ID=${item.id}")
            throw e
        }
    }
    
    // 공통 구현: 아이템 업데이트
    suspend fun update(item: T) {
        try {
            logOperation("아이템 업데이트", "ID=${item.id} 시작")
            updateEntity(mapToEntity(item))
            logOperation("아이템 업데이트", "ID=${item.id} 완료")
        } catch (e: Exception) {
            logError(e, "아이템 업데이트", "ID=${item.id}")
            throw e
        }
    }
    
    // 공통 구현: 아이템 삭제
    suspend fun delete(id: String) {
        try {
            logOperation("아이템 삭제", "ID=$id 시작")
            deleteEntity(id)
            logOperation("아이템 삭제", "ID=$id 완료")
        } catch (e: Exception) {
            logError(e, "아이템 삭제", "ID=$id")
            throw e
        }
    }
    
    // 공통 구현: 동기화되지 않은 아이템 조회
    suspend fun getUnsyncedItems(): List<T> {
        return try {
            getUnsyncedEntities().map { mapToDomain(it) }.also {
                logOperation("동기화되지 않은 아이템 조회", "${it.size}개 찾음")
            }
        } catch (e: Exception) {
            logError(e, "동기화되지 않은 아이템 조회", "")
            emptyList()
        }
    }
    
    // 공통 구현: 동기화 상태 업데이트
    suspend fun updateLastSync(ids: List<String>, lastSync: LastSync) {
        try {
            logOperation("동기화 상태 업데이트", "${ids.size}개 아이템, 상태=${lastSync.status}")
            updateEntitiesLastSync(ids, lastSync.status.ordinal, lastSync.timestamp, null)
        } catch (e: Exception) {
            logError(e, "동기화 상태 업데이트", "${ids.size}개 아이템")
            throw e
        }
    }
} 