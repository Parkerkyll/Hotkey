# 동기화 전략 (Sync Strategy)

## 1. 개요 및 전제 조건

### 1.1 사용자 패턴 분석
- 대부분의 사용자는 비슷한 지역에서 반복적으로 활동
- 활동 빈도: 매일 ~ 월 1회 방문
- 가끔 새로운 지역 방문 가능성 존재
- 대부분 오프라인에서 마커/메모 작성 후 동기화

### 1.2 주요 목표
- 최소한의 서버 호출
- 오프라인 우선 작동 보장
- 배터리 및 데이터 사용량 최적화
- 간단한 충돌 해결

## 2. 동기화 시나리오별 전략

### 2.1 신규 사용자 첫 실행
```kotlin
/**
 * 1. 실행 순서
 *    1) 카카오 로그인
 *    2) 위치 권한 획득
 *    3) 현재 위치 기반 geohash6 생성
 *    4) 해당 지역 데이터만 우선 다운로드
 *    5) Room DB에 저장
 *    6) 지도에 마커 표시
 * 
 * 2. 최적화
 *    - 주변 8개 geohash 영역은 백그라운드로 프리페치
 *    - 프리페치 실패는 무시 (필요시 나중에 다시 시도)
 */
class InitialDataLoader {
    suspend fun loadInitialData() {
        // 1. 현재 위치 기반 데이터 로드
        val currentLocation = locationManager.getCurrentLocation()
        val geohash = GeohashUtil.encode(currentLocation, precision = 6)
        
        // 2. 해당 지역 데이터 다운로드 및 저장
        val data = api.getRegionData(geohash)
        roomDb.withTransaction {
            markerDao.insertAll(data.markers)
            memoDao.insertAll(data.memos)
        }
        
        // 3. 백그라운드에서 주변 지역 프리페치
        launch(Dispatchers.IO) {
            prefetchNeighborRegions(geohash)
        }
    }
}
```

### 2.2 기존 사용자 시나리오

#### 2.2.1 동시 변경사항 처리
```kotlin
/**
 * 1. 시나리오
 *    - 로컬에서 오프라인 상태로 변경
 *    - 동시에 서버에서도 데이터 변경
 *    - 네트워크 복구 시 동기화
 * 
 * 2. 처리 전략
 *    - 엔티티별 버전 관리
 *    - 타임스탬프 기반 충돌 해결
 *    - 삭제 작업 우선 처리
 */
class ConcurrentChangeHandler {
    suspend fun handleConcurrentChanges(
        localChanges: List<EntityChange>,
        serverChanges: List<EntityChange>
    ) {
        roomDb.withTransaction {
            // 1. 삭제 작업 우선 처리
            processDeletions(localChanges, serverChanges)
            
            // 2. 나머지 변경사항 처리
            val remainingChanges = (localChanges + serverChanges)
                .filterNot { it is DeletionChange }
                .groupBy { it.entityId }
            
            remainingChanges.forEach { (entityId, changes) ->
                when {
                    // 서버만 변경된 경우
                    changes.size == 1 && changes[0].isFromServer ->
                        applyServerChange(changes[0])
                    
                    // 로컬만 변경된 경우
                    changes.size == 1 ->
                        applyLocalChange(changes[0])
                    
                    // 양쪽 모두 변경된 경우
                    else -> resolveConflict(
                        localChange = changes.first { !it.isFromServer },
                        serverChange = changes.first { it.isFromServer }
                    )
                }
            }
        }
    }
    
    private suspend fun resolveConflict(
        localChange: EntityChange,
        serverChange: EntityChange
    ) {
        when {
            // 1. 타임스탬프가 다른 경우 - 최신 것 우선
            localChange.timestamp > serverChange.timestamp ->
                applyLocalChange(localChange)
            localChange.timestamp < serverChange.timestamp ->
                applyServerChange(serverChange)
            
            // 2. 타임스탬프가 같은 경우
            else -> when {
                // 다른 필드 수정 - 병합
                !hasFieldConflict(localChange, serverChange) ->
                    mergeChanges(localChange, serverChange)
                
                // 같은 필드 수정 - 서버 우선
                else -> applyServerChange(serverChange)
            }
        }
    }
}

#### 2.2.2 기존 지역 재방문
```kotlin
/**
 * 1. 동기화 조건
 *    - 앱 실행 시: 마지막 동기화로부터 1시간 이상 경과
 *    - 포그라운드 전환 시: 마지막 동기화로부터 5분 이상 경과
 *    - 명시적 새로고침 요청 시
 * 
 * 2. 동기화 범위
 *    - 현재 보이는 지역의 geohash만 동기화
 *    - 로컬 변경사항 있는 경우만 서버로 전송
 */
class RegionSyncManager {
    suspend fun syncRegion(geohash: String) {
        // 1. 로컬 변경사항 확인
        val localChanges = roomDb.getLocalModifications(geohash)
        
        // 2. 서버 변경사항 확인 (lastSyncTime 기준)
        val serverChanges = api.getChanges(
            geohash = geohash,
            since = getLastSyncTime(geohash)
        )
        
        // 3. 변경사항 있는 경우만 동기화
        if (localChanges.isNotEmpty() || serverChanges.isNotEmpty()) {
            performSync(localChanges, serverChanges)
        }
    }
}
```

#### 2.2.3 새로운 지역 방문
```kotlin
/**
 * 1. 트리거 조건
 *    - 지도 이동으로 새로운 geohash 영역 진입
 *    - 현재 위치가 새로운 geohash 영역으로 변경
 * 
 * 2. 처리 방식
 *    - 새로운 영역 데이터 즉시 다운로드
 *    - 오래된 영역 데이터는 LRU 방식으로 제거
 */
class NewRegionHandler {
    suspend fun handleNewRegion(newGeohash: String) {
        // 1. 캐시 확인
        if (!isRegionCached(newGeohash)) {
            // 2. 새로운 지역 데이터 다운로드
            val regionData = api.getRegionData(newGeohash)
            
            // 3. Room DB에 저장
            roomDb.withTransaction {
                markerDao.insertAll(regionData.markers)
                memoDao.insertAll(regionData.memos)
                
                // 4. 캐시 정리 (오래된 지역 데이터 삭제)
                cleanupOldRegions()
            }
        }
    }
}
```

### 2.3 변경사항 처리 전략

#### 2.3.1 로컬 변경사항
```kotlin
/**
 * 1. 처리 방식
 *    - 즉시 로컬 DB에 반영
 *    - 변경사항 큐에 저장
 *    - 주기적 동기화 시점에 일괄 처리
 * 
 * 2. 우선순위
 *    - 삭제 > 수정 > 생성
 */
class LocalChangeManager {
    suspend fun handleLocalChange(change: EntityChange) {
        roomDb.withTransaction {
            // 1. 로컬 DB 업데이트
            when (change) {
                is MarkerChange -> markerDao.update(change.marker)
                is MemoChange -> memoDao.update(change.memo)
            }
            
            // 2. 변경사항 큐에 저장
            changeQueueDao.insert(change.toQueueItem())
        }
    }
}
```

#### 2.3.2 서버 변경사항
```kotlin
/**
 * 1. 감지 시점
 *    - 주기적 동기화 시
 *    - 포그라운드 전환 시
 * 
 * 2. 처리 방식
 *    - 로컬 변경사항 없음 -> 서버 변경사항 적용
 *    - 충돌 -> 최신 타임스탬프 우선
 */
class ServerChangeHandler {
    suspend fun handleServerChanges(changes: List<EntityChange>) {
        changes.forEach { change ->
            val localVersion = roomDb.getLocalVersion(change.entityId)
            
            when {
                // 로컬 변경 없음 -> 서버 변경사항 적용
                localVersion == null -> applyServerChange(change)
                
                // 충돌 -> 최신 타임스탬프 우선
                isConflict(localVersion, change) -> 
                    resolveConflict(localVersion, change)
                
                // 그 외 -> 서버 변경사항 적용
                else -> applyServerChange(change)
            }
        }
    }
}
```

## 3. 동기화 트리거 포인트

### 3.1 자동 동기화
```kotlin
/**
 * 1. 주기적 동기화 (WorkManager)
 *    - 15분 간격
 *    - 네트워크 연결시에만
 *    - 배터리 부족하지 않을 때
 * 
 * 2. 상태 변경 시
 *    - 포그라운드 전환
 *    - 위치 변경
 *    - 네트워크 복구
 */
@HiltWorker
class SyncWorker : CoroutineWorker {
    override suspend fun doWork(): Result {
        // 1. 동기화 필요성 확인
        if (!shouldSync()) return Result.success()
        
        try {
            // 2. 현재 지역 동기화
            val currentGeohash = locationManager.getCurrentGeohash()
            regionSyncManager.syncRegion(currentGeohash)
            
            // 3. 로컬 변경사항 처리
            processLocalChanges()
            
            return Result.success()
        } catch (e: Exception) {
            return if (shouldRetry(e)) Result.retry()
            else Result.failure()
        }
    }
}
```

### 3.2 수동 동기화
```kotlin
/**
 * 1. 트리거 시점
 *    - 사용자 새로고침 요청
 *    - 오류 후 재시도
 * 
 * 2. 처리 방식
 *    - 현재 보이는 영역만 동기화
 *    - 진행률 표시
 */
class ManualSyncManager {
    suspend fun performManualSync() {
        try {
            // 1. 현재 보이는 영역의 geohash 목록
            val visibleGeohashes = mapController.getVisibleGeohashes()
            
            // 2. 순차적 동기화
            visibleGeohashes.forEach { geohash ->
                regionSyncManager.syncRegion(geohash)
                updateProgress()
            }
        } catch (e: Exception) {
            handleSyncError(e)
        }
    }
}
```

## 4. 오프라인 지원

### 4.1 오프라인 작업 처리
```kotlin
/**
 * 1. 저장 정책
 *    - 모든 변경사항 로컬 우선 저장
 *    - 작업 큐에 기록
 * 
 * 2. 네트워크 복구 시
 *    - 큐의 작업 순차 처리
 *    - 실패 시 재시도 정책 적용
 */
class OfflineManager {
    suspend fun processOfflineQueue() {
        val queue = offlineQueueDao.getAll()
        
        queue.sortedBy { it.priority }.forEach { operation ->
            try {
                processOperation(operation)
                offlineQueueDao.delete(operation)
            } catch (e: Exception) {
                handleProcessingError(operation, e)
            }
        }
    }
}
```

## 5. 데이터베이스 스키마

### 5.1 Room DB
```kotlin
/**
 * 1. 엔티티
 *    - Marker: 위치 정보
 *    - Memo: 메모 내용
 *    - SyncMetadata: 동기화 정보
 *    - OfflineQueue: 오프라인 작업
 * 
 * 2. 인덱스
 *    - geohash: 지역 검색
 *    - lastModified: 변경사항 추적
 */
@Entity
data class Marker(
    @PrimaryKey val id: String,
    val geohash: String,
    val latitude: Double,
    val longitude: Double,
    val lastModified: Long,
    val syncState: SyncState,
    val version: Long
)

@Entity
data class Memo(
    @PrimaryKey val id: String,
    val markerId: String,
    val content: String,
    val lastModified: Long,
    val syncState: SyncState,
    val version: Long
)
```

### 5.2 DynamoDB
```kotlin
/**
 * 1. 테이블 구조
 *    - Markers: 마커 정보
 *    - Memos: 메모 정보
 * 
 * 2. 인덱스
 *    - geohash-index: 지역별 조회
 *    - lastModified-index: 변경사항 조회
 */
// Markers 테이블
{
    id: String (PK),
    geohash: String (GSI),
    latitude: Number,
    longitude: Number,
    lastModified: Number (GSI),
    version: Number
}

// Memos 테이블
{
    id: String (PK),
    markerId: String (GSI),
    content: String,
    lastModified: Number (GSI),
    version: Number
}