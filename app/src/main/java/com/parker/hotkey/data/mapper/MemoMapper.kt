package com.parker.hotkey.data.mapper

import com.parker.hotkey.data.local.entity.MemoEntity
import com.parker.hotkey.data.remote.sync.dto.MemoDto
import com.parker.hotkey.domain.model.LastSync
import com.parker.hotkey.domain.model.Memo
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Memo 모델과 DTO, Entity 간 변환을 담당하는 Mapper 클래스
 */
@Singleton
class MemoMapper @Inject constructor() {
    
    /**
     * MemoDto를 도메인 모델로 변환
     */
    fun fromDto(dto: MemoDto): Memo {
        return Memo(
            id = dto.id,
            userId = "", // API 응답에는 userId가 없으므로 빈 값으로 설정
            markerId = dto.markerId,
            content = dto.content,
            modifiedAt = dto.modifiedAt,
            lastSync = LastSync.createSuccess(dto.modifiedAt) // 서버에서 받은 데이터는 동기화된 상태로 간주
        )
    }
    
    /**
     * 도메인 모델을 MemoDTO로 변환
     */
    fun toDto(memo: Memo): MemoDto {
        return MemoDto(
            id = memo.id,
            markerId = memo.markerId,
            content = memo.content,
            modifiedAt = memo.modifiedAt
        )
    }
    
    /**
     * MemoEntity를 도메인 모델로 변환
     */
    fun fromEntity(entity: MemoEntity): Memo {
        return Memo(
            id = entity.id,
            userId = entity.userId,
            markerId = entity.markerId,
            content = entity.content,
            modifiedAt = entity.modifiedAt,
            lastSync = LastSync(
                status = LastSync.SyncStatus.values()[entity.syncStatus],
                timestamp = entity.syncTimestamp,
                serverVersion = null
            )
        )
    }
    
    /**
     * 도메인 모델을 MemoEntity로 변환
     */
    fun toEntity(memo: Memo): MemoEntity {
        return MemoEntity(
            id = memo.id,
            userId = memo.userId,
            markerId = memo.markerId,
            content = memo.content,
            modifiedAt = memo.modifiedAt,
            syncStatus = memo.lastSync.status.ordinal,
            syncTimestamp = memo.lastSync.timestamp,
            syncError = null
        )
    }
    
    /**
     * 새로운 Memo 생성
     */
    fun createNewMemo(
        userId: String,
        markerId: String,
        content: String
    ): Memo {
        return Memo(
            id = UUID.randomUUID().toString(),
            userId = userId,
            markerId = markerId,
            content = content,
            modifiedAt = System.currentTimeMillis(),
            lastSync = LastSync.createInitial()
        )
    }
} 