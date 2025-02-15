# 데이터 구조 문서

## 개발 환경
```kotlin
/**
 * 1. Android Studio
 *    - 버전: Hedgehog | 2023.1.1
 *    - JDK: 17
 *    - Gradle: 8.0
 *    - Kotlin: 1.9.0
 * 
 * 2. SDK 설정
 *    - minSdk: 29 (Android 10)
 *    - targetSdk: 34 (Android 14)
 *    - compileSdk: 34
 * 
 * 3. 주요 라이브러리
 *    - AndroidX Core: 1.12.0
 *    - WorkManager: 2.9.0
 *    - EventBus: 3.3.1
 *    - Navigation: 2.7.5
 *    - Room: 2.6.1
 *    - Hilt: 2.48
 *    - Naver Maps: 3.18.0
 *    - Kakao SDK: 2.20.6
 */

// local.properties 설정
sdk.dir=/Users/username/Library/Android/sdk
naver.map.client_id=YOUR_CLIENT_ID
kakao.native_app_key=YOUR_APP_KEY
```

## 프로젝트 구조 다이어그램
```
app/
├── build/                      # 빌드 출력 디렉토리
├── libs/                       # 외부 라이브러리
└── src/
    ├── main/                   # 메인 소스 코드
    │   ├── java/com/parker/hotkey/
    │   │   ├── di/            # 의존성 주입
    │   │   │   ├── MapModule.kt       # 지도 관련 의존성
    │   │   │   ├── ManagerModule.kt   # 매니저 관련 의존성
    │   │   │   ├── UseCaseModule.kt   # UseCase 의존성
    │   │   │   └── WorkManagerModule.kt# WorkManager 설정
    │   │   ├── data/          # 데이터 계층
    │   │   │   ├── local/     # 로컬 데이터 소스
    │   │   │   │   ├── dao/   # Room DAO
    │   │   │   │   └── entity/# Room Entity
    │   │   │   ├── manager/   # 매니저 구현체
    │   │   │   └── repository/# Repository 구현체
    │   │   ├── domain/        # 도메인 계층
    │   │   │   ├── model/     # 도메인 모델
    │   │   │   ├── repository/# Repository 인터페이스
    │   │   │   └── usecase/   # UseCase
    │   │   ├── util/          # 유틸리티
    │   │   │   └── GeohashUtil.kt    # Geohash 변환/검색
    │   │   └── presentation/  # 프레젠테이션 계층
    │   │       └── map/       # 지도 관련 UI
    │   │           ├── MapFragment.kt     # 메인 지도 화면
    │   │           ├── MapViewModel.kt    # 지도 뷰모델
    │   │           ├── MemoListDialog.kt  # 메모 목록 다이얼로그
    │   │           ├── MarkerUIDelegate.kt# 마커 UI 처리
    │   │           └── MapConfigDelegate.kt# 지도 설정 처리
    │   ├── res/               # 리소스 파일
    │   └── AndroidManifest.xml
    └── test/                  # 테스트 코드


## 주요 컴포넌트 설명

### 1. 지도 관련 컴포넌트
```kotlin
/**
 * MapFragment
 * - 네이버 지도 초기화 및 설정
 * - 위치 권한 처리
 * - 마커 및 메모 UI 처리
 * - geohash6 범위 표시 (1.2km 반경)
 */
class MapFragment : Fragment(), OnMapReadyCallback {
    companion object {
        const val DEFAULT_ZOOM = 17.2
        const val MARKER_CREATION_ZOOM = 18.0
        const val MIN_ZOOM = 14.0
        const val MAX_ZOOM = 19.0
        const val GEOHASH_RADIUS = 1200.0 // 1.2km
    }
}

/**
 * MapViewModel
 * - 마커/메모 상태 관리
 * - 위치 업데이트 처리
 * - WorkManager를 통한 마커 자동 삭제
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    private val markerRepository: MarkerRepository,
    private val memoRepository: MemoRepository,
    private val locationManager: LocationManager
)

/**
 * LocationManager
 * - 위치 업데이트 제공
 * - 권한 기반 위치 정보 접근
 */
interface LocationManager {
    fun getLocationUpdates(): Flow<Location>
    suspend fun getLastLocation(): Location?
}
```

### 2. 마커/메모 관리
```kotlin
/**
 * MarkerRepository
 * - 마커 CRUD 작업
 * - Geohash 기반 마커 검색
 */
interface MarkerRepository {
    fun getAllMarkers(): Flow<List<MarkerEntity>>
    fun getMarkersByGeohash(geohashPrefix: String): Flow<List<MarkerEntity>>
    fun getMarkersInGeohashRange(centerGeohash: String, neighbors: List<String>): Flow<List<MarkerEntity>>
    suspend fun createMarker(latitude: Double, longitude: Double, geohash: String): MarkerEntity
    suspend fun deleteMarker(markerId: String)
}

/**
 * MemoRepository
 * - 메모 CRUD 작업
 * - 마커별 메모 관리
 */
interface MemoRepository {
    fun getMemosByMarkerId(markerId: String): Flow<List<MemoEntity>>
    suspend fun createMemo(markerId: String, content: String): MemoEntity
    suspend fun deleteMemo(memoId: String, markerId: String)
    suspend fun getMemoCount(markerId: String): Int
}
```

### 3. 자동 삭제 시스템
```kotlin
/**
 * EmptyMarkerDeleteWorker
 * - 빈 마커 자동 삭제 (15초 후)
 * - EventBus를 통한 UI 업데이트
 */
@HiltWorker
class EmptyMarkerDeleteWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val markerRepository: MarkerRepository,
    private val memoRepository: MemoRepository
) : CoroutineWorker(appContext, workerParams)
```

### 4. 에러 처리
```kotlin
/**
 * MapError
 * - 위치 권한 에러
 * - 위치 정보 에러
 * - 네트워크 에러
 * - 기타 에러
 */
sealed class MapError(val message: String) {
    class PermissionError(message: String) : MapError(message)
    class LocationError(message: String) : MapError(message)
    class NetworkError(message: String) : MapError(message)
    class UnknownError(message: String) : MapError(message)
}
```

## 주요 기능

### 1. 위치 기반 마커 관리
- 현재 위치 geohash6 기준 1.2km 반경 표시
- 해당 범위 내 마커만 표시 (다른 마커는 숨김)
- 마커 생성/삭제/수정 기능

### 2. 메모 시스템
- 마커당 최대 10개 메모 지원
- 메모 없는 마커 15초 후 자동 삭제
- 메모 추가/삭제 시 자동 삭제 예약/취소

### 3. UI/UX
- 기본 줌 레벨: 17.2
- 마커 생성 시 줌 레벨: 18.0
- 마커 투명도: 0.4
- 포그라운드 전환 시 현재 위치로 자동 이동

### 4. 권한 관리
- 위치 권한 요청 및 처리
- 권한 거부 시 적절한 피드백
- 백그라운드 위치 권한 관리