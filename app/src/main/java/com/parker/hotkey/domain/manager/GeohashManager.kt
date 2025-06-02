package com.parker.hotkey.domain.manager

import com.parker.hotkey.data.local.dao.VisitedGeohashDao
import com.parker.hotkey.data.local.entity.VisitedGeohashEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface GeohashManager {
    suspend fun recordVisit(geohash: String)
    suspend fun isVisited(geohash: String): Boolean
    suspend fun markAsSynced(geohash: String)
    suspend fun markAsHavingLocalData(geohash: String)
    suspend fun hasLocalData(geohash: String): Boolean
    fun getVisitedGeohashesFlow(): Flow<List<VisitedGeohashEntity>>
    suspend fun getVisitedGeohashes(): List<VisitedGeohashEntity>
    suspend fun getUnsyncedGeohashes(): List<VisitedGeohashEntity>
}

@Singleton
class GeohashManagerImpl @Inject constructor(
    private val visitedGeohashDao: VisitedGeohashDao
) : GeohashManager {
    
    override suspend fun recordVisit(geohash: String) {
        val existingEntity = visitedGeohashDao.getVisitedGeohash(geohash)
        val currentTime = System.currentTimeMillis()
        
        if (existingEntity == null) {
            visitedGeohashDao.insertVisitedGeohash(
                VisitedGeohashEntity(
                    geohash = geohash,
                    firstVisitTime = currentTime,
                    lastVisitTime = currentTime
                )
            )
        } else {
            visitedGeohashDao.updateVisitInfo(geohash, currentTime)
        }
    }
    
    override suspend fun isVisited(geohash: String): Boolean {
        return visitedGeohashDao.getVisitedGeohash(geohash) != null
    }
    
    override suspend fun markAsSynced(geohash: String) {
        visitedGeohashDao.markAsSynced(geohash)
    }
    
    override suspend fun markAsHavingLocalData(geohash: String) {
        visitedGeohashDao.markAsHavingLocalData(geohash)
    }
    
    override suspend fun hasLocalData(geohash: String): Boolean {
        val entity = visitedGeohashDao.getVisitedGeohash(geohash)
        return entity?.hasLocalData == true
    }
    
    override fun getVisitedGeohashesFlow(): Flow<List<VisitedGeohashEntity>> {
        return visitedGeohashDao.getVisitedGeohashesFlow()
    }
    
    override suspend fun getVisitedGeohashes(): List<VisitedGeohashEntity> {
        return visitedGeohashDao.getVisitedGeohashes()
    }
    
    override suspend fun getUnsyncedGeohashes(): List<VisitedGeohashEntity> {
        return visitedGeohashDao.getUnsyncedGeohashes()
    }
} 