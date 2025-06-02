package com.parker.hotkey.data.repository

import com.parker.hotkey.data.local.dao.VisitedGeohashDao
import com.parker.hotkey.data.local.entity.VisitedGeohashEntity
import com.parker.hotkey.domain.repository.VisitedGeohashRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class VisitedGeohashRepositoryImpl @Inject constructor(
    private val visitedGeohashDao: VisitedGeohashDao
) : VisitedGeohashRepository {
    
    override fun getVisitedGeohashesFlow(): Flow<List<VisitedGeohashEntity>> {
        Timber.d("방문한 지역 정보 구독 요청")
        return visitedGeohashDao.getVisitedGeohashesFlow()
    }
    
    override suspend fun getVisitedGeohashes(): List<VisitedGeohashEntity> {
        Timber.d("방문한 지역 정보 요청")
        val geohashes = visitedGeohashDao.getVisitedGeohashes()
        Timber.d("방문한 지역 ${geohashes.size}개 조회 완료")
        return geohashes
    }
    
    override suspend fun getVisitedGeohashCount(): Int {
        val count = visitedGeohashDao.getVisitedGeohashCount()
        Timber.d("방문한 지역 수: $count")
        return count
    }
    
    override suspend fun getUnsyncedGeohashes(): List<VisitedGeohashEntity> {
        val unsyncedGeohashes = visitedGeohashDao.getUnsyncedGeohashes()
        Timber.d("동기화 안 된 지역 ${unsyncedGeohashes.size}개 조회 완료")
        return unsyncedGeohashes
    }
} 