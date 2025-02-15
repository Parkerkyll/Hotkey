# 데이터 흐름 문서

## 1. 전체 아키텍처 개요

### 1.1 레이어 구조
```kotlin
/**
 * 클린 아키텍처 기반의 4계층 구조
 * 
 * 1. Presentation Layer (UI)
 *    - Activity/Fragment
 *    - Compose UI
 *    - ViewModel
 * 
 * 2. Domain Layer (비즈니스 로직)
 *    - UseCase
 *    - Repository Interface
 *    - Domain Model
 * 
 * 3. Data Layer (데이터 처리)
 *    - Repository Implementation
 *    - Data Source
 *    - Data Model
 * 
 * 4. Infrastructure Layer (외부 시스템)
 *    - Room Database
 *    - AWS Lambda
 *    - Kakao/Naver SDK
 */
```

### 1.2 주요 컴포넌트
```kotlin
/**
 * 핵심 컴포넌트 구성
 * 
 * 1. UI 컴포넌트
 *    - MapScreen: 지도 화면 및 마커 표시
 *    - MemoScreen: 메모 작성/조회 화면
 *    - LoginScreen: 카카오 로그인 화면
 * 
 * 2. ViewModel
 *    - MapViewModel: 지도/마커 상태 관리
 *    - MemoViewModel: 메모 CRUD 처리
 *    - LoginViewModel: 인증 상태 관리
 * 
 * 3. UseCase
 *    - GetMarkersUseCase: 마커 조회
 *    - SaveMemoUseCase: 메모 저장
 *    - SyncDataUseCase: 데이터 동기화
 * 
 * 4. Repository
 *    - MarkerRepository: 마커 데이터 관리
 *    - MemoRepository: 메모 데이터 관리
 *    - AuthRepository: 인증 정보 관리
 */
```

## 2. 주요 데이터 흐름

### 2.1 앱 시작 및 초기화
```kotlin
/**
 * 앱 실행 시퀀스
 * 
 * 1. 앱 시작
 *    Application
 *      → Hilt 초기화
 *      → Timber 초기화
 *      → SDK 초기화 (Kakao, Naver)
 * 
 * 2. 스플래시 화면
 *    SplashViewModel
 *      → 토큰 유효성 검사
 *      → 자동 로그인 처리
 *      → 초기 화면 결정
 * 
 * 3. 권한 체크
 *    PermissionManager
 *      → 위치 권한 확인
 *      → 권한 요청 처리
 *      → 결과 콜백
 */

class SplashViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val permissionManager: PermissionManager
) {
    suspend fun checkInitialState(): InitialState {
        // 1. 토큰 검증
        if (!authRepository.hasValidToken()) {
            return InitialState.RequireLogin
        }

        // 2. 위치 권한 체크
        if (!permissionManager.hasLocationPermission()) {
            return InitialState.RequirePermission
        }

        return InitialState.Ready
    }
}

sealed class InitialState {
    object Ready : InitialState()
    object RequireLogin : InitialState()
    object RequirePermission : InitialState()
}
```

### 2.2 인증 및 로그인
```kotlin
/**
 * 로그인 프로세스
 * 
 * 1. 카카오 로그인
 *    LoginScreen
 *      → 카카오 SDK 호출
 *      → 토큰 수신
 * 
 * 2. 서버 인증
 *    AuthRepository
 *      → 카카오 토큰으로 서버 API 호출
 *      → JWT 토큰 발급
 * 
 * 3. 토큰 저장
 *    TokenManager
 *      → EncryptedSharedPreferences 사용
 *      → 토큰 암호화 저장
 */

class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val tokenManager: TokenManager
) {
    suspend fun login(kakaoToken: String): Result<AuthToken> {
        return try {
            // 1. 서버 인증
            val authToken = authApi.authenticate(kakaoToken)
            
            // 2. 토큰 저장
            tokenManager.saveToken(authToken)
            
            Result.success(authToken)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

### 2.3 데이터 동기화
```kotlin
/**
 * 데이터 동기화 프로세스
 * 
 * 1. 동기화 트리거
 *    - 앱 시작 시
 *    - 위치 변경 시
 *    - 수동 새로고침 시
 * 
 * 2. 동기화 범위
 *    - Geohash 기반 영역 설정
 *    - 증분 업데이트 (lastSyncTime 기준)
 * 
 * 3. 동기화 순서
 *    - 로컬 DB 조회
 *    - 서버 API 호출
 *    - 데이터 병합
 *    - 로컬 DB 업데이트
 */

class SyncDataUseCase @Inject constructor(
    private val markerRepository: MarkerRepository,
    private val locationManager: LocationManager
) {
    suspend fun execute() {
        try {
            // 1. 현재 위치 획득
            val location = locationManager.getCurrentLocation()
            
            // 2. Geohash 계산
            val geohash = GeohashUtil.encode(
                location.latitude,
                location.longitude
            )
            
            // 3. 데이터 동기화
            markerRepository.syncGeohashArea(geohash)
        } catch (e: Exception) {
            Timber.e(e, "동기화 실패")
            throw e
        }
    }
}
```

### 2.4 마커 및 메모 관리
```kotlin
/**
 * 마커/메모 데이터 흐름
 * 
 * 1. 마커 생성
 *    MapScreen
 *      → 지도 클릭
 *      → MapViewModel.createMarker()
 *      → MarkerRepository.saveMarker()
 *      → Local DB + Server API
 * 
 * 2. 메모 작성
 *    MemoScreen
 *      → 메모 입력
 *      → MemoViewModel.saveMemo()
 *      → MemoRepository.saveMemo()
 *      → Local DB + Server API
 * 
 * 3. 데이터 제약
 *    - 마커당 메모 최대 10개
 *    - 메모 없는 마커 15초 후 삭제
 */

class MarkerRepository @Inject constructor(
    private val markerApi: MarkerApi,
    private val markerDao: MarkerDao
) {
    suspend fun saveMarker(marker: MarkerData): Result<MarkerEntity> {
        return try {
            // 1. 서버 저장
            val response = markerApi.createMarker(marker)
            
            // 2. 로컬 DB 저장
            val entity = response.toEntity()
            markerDao.insertMarker(entity)
            
            // 3. 15초 타이머 시작
            startDeletionTimer(entity.id)
            
            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun startDeletionTimer(markerId: String) {
        viewModelScope.launch {
            delay(15000) // 15초 대기
            val hasMemos = markerDao.getMemosCount(markerId) > 0
            if (!hasMemos) {
                deleteMarker(markerId)
            }
        }
    }
}
```

## 3. 에러 처리 전략

### 3.1 에러 타입 정의
```kotlin
sealed class AppError : Exception() {
    data class NetworkError(
        override val message: String,
        val isRetryable: Boolean = true
    ) : AppError()
    
    data class ServerError(
        override val message: String,
        val code: String
    ) : AppError()
    
    data class DatabaseError(
        override val message: String
    ) : AppError()
    
    data class LocationError(
        override val message: String
    ) : AppError()
}
```

### 3.2 레이어별 에러 처리
```kotlin
/**
 * 레이어별 에러 처리 전략
 * 
 * 1. Data Layer
 *    - 구체적 예외 발생
 *    - 적절한 에러 타입으로 변환
 * 
 * 2. Domain Layer
 *    - 비즈니스 로직 예외 처리
 *    - 복구 가능한 경우 재시도
 * 
 * 3. Presentation Layer
 *    - 사용자 친화적 메시지 결정
 *    - UI 상태 업데이트
 * 
 * 4. UI Layer
 *    - 에러 메시지 표시
 *    - 재시도 옵션 제공
 */
```

## 4. 성능 최적화

### 4.1 데이터베이스 최적화
```kotlin
/**
 * Room DB 최적화 전략
 * 
 * 1. 인덱스 최적화
 *    - geohash 인덱스 (필수)
 *    - createdAt 인덱스 (선택)
 * 
 * 2. 쿼리 최적화
 *    - 페이징 처리
 *    - 배치 처리
 * 
 * 3. 트랜잭션 관리
 *    - 원자성 보장
 *    - 일관성 유지
 */

@Dao
interface MarkerDao {
    @Transaction
    suspend fun updateMarkersAndMemos(
        markers: List<MarkerEntity>,
        memos: List<MemoEntity>
    ) {
        markers.chunked(100).forEach { batch ->
            insertMarkers(batch)
        }
        memos.chunked(100).forEach { batch ->
            insertMemos(batch)
        }
    }
}
```

### 4.2 네트워크 최적화
```kotlin
/**
 * 네트워크 최적화 전략
 * 
 * 1. 증분 업데이트
 *    - lastSyncTime 기반
 *    - 변경된 데이터만 전송
 * 
 * 2. 배치 처리
 *    - 데이터 묶음 전송
 *    - 요청 횟수 최소화
 * 
 * 3. 캐싱 전략
 *    - HTTP 캐시 활용
 *    - 메모리 캐시 활용
 */
```

## 5. 모니터링 및 디버깅

### 5.1 로깅 전략
```kotlin
/**
 * 로깅 정책
 * 
 * 1. 로그 레벨
 *    - DEBUG: 개발용 상세 로그
 *    - INFO: 주요 이벤트
 *    - ERROR: 에러 상황
 * 
 * 2. 로그 내용
 *    - 시간
 *    - 컴포넌트
 *    - 이벤트 종류
 *    - 상세 정보
 */

object Logger {
    fun logNetworkCall(call: String, duration: Long) {
        Timber.d("Network: $call, Duration: ${duration}ms")
    }
    
    fun logDatabaseOperation(operation: String, count: Int) {
        Timber.d("DB: $operation, Count: $count")
    }
    
    fun logError(e: Exception, message: String) {
        Timber.e(e, message)
    }
}
```

### 5.2 성능 모니터링
```kotlin
/**
 * 성능 모니터링 항목
 * 
 * 1. 응답 시간
 *    - API 호출 시간
 *    - DB 쿼리 시간
 * 
 * 2. 리소스 사용
 *    - 메모리 사용량
 *    - 디스크 사용량
 * 
 * 3. 에러율
 *    - API 에러
 *    - DB 에러
 */
``` 