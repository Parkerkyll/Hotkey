# 테스트 시나리오

## 단위 테스트

### 1. 도메인 로직
```kotlin
class MarkerUseCaseTest {
    @Test
    fun `마커 생성 시 geohash가 정상적으로 생성되어야 한다`() {
        val useCase = MarkerUseCase()
        val marker = useCase.createMarker(
            latitude = 37.123,
            longitude = 127.123,
            title = "테스트"
        )
        
        assertEquals("wydm9q", marker.geohash)
    }
}
```

### 2. 데이터 로직
```kotlin
class MarkerRepositoryTest {
    @Test
    fun `오프라인 상태에서 마커 생성 시 임시 ID가 할당되어야 한다`() {
        val repository = MarkerRepository()
        val marker = repository.createMarker(
            title = "테스트",
            isOffline = true
        )
        
        assertNotNull(marker.localId)
        assertNull(marker.serverId)
    }
}
```

### 3. 유틸리티
```kotlin
class GeohashUtilTest {
    @Test
    fun `좌표로 geohash 생성이 정상적으로 되어야 한다`() {
        val geohash = GeohashUtil.encode(37.123, 127.123)
        assertEquals("wydm9q", geohash)
    }
}
```

## 통합 테스트

### 1. 데이터 동기화
```kotlin
class SyncManagerTest {
    @Test
    fun `오프라인에서 생성된 마커가 온라인 상태에서 정상적으로 동기화되어야 한다`() {
        // Given: 오프라인 상태에서 마커 생성
        // When: 온라인 상태로 전환
        // Then: 서버 ID가 할당되고 동기화 상태가 SYNCED여야 함
    }
}
```

### 2. 인증 흐름
```kotlin
class AuthFlowTest {
    @Test
    fun `토큰 만료 시 자동으로 갱신되어야 한다`() {
        // Given: 만료된 토큰
        // When: API 호출
        // Then: 토큰이 갱신되고 API 호출 성공
    }
}
```

## UI 테스트

### 1. 마커 관련
- [ ] 마커 생성
  ```kotlin
  // 테스트 데이터
  val testMarker = Marker(
      title = "테스트 마커",
      latitude = 37.123,
      longitude = 127.123
  )
  
  // 예상 결과
  - 지도에 마커 표시됨
  - 마커 정보 창 표시됨
  - DB에 저장됨
  ```

- [ ] 마커 수정
  ```kotlin
  // 테스트 데이터
  val updatedTitle = "수정된 마커"
  
  // 예상 결과
  - 마커 정보 업데이트됨
  - UI 반영됨
  - 동기화 상태 PENDING
  ```

- [ ] 마커 삭제
  ```kotlin
  // 예상 결과
  - 지도에서 마커 제거됨
  - DB에서 soft delete
  - 연관 메모도 함께 삭제
  ```

### 2. 메모 관련
- [ ] 메모 생성
  ```kotlin
  // 테스트 데이터
  val testMemo = Memo(
      content = "테스트 메모",
      markerId = "test_marker_id"
  )
  
  // 예상 결과
  - 메모 목록에 추가됨
  - 마커에 메모 카운트 업데이트
  - DB에 저장됨
  ```

- [ ] 메모 수정/삭제
  ```kotlin
  // 예상 결과
  - UI 업데이트
  - DB 반영
  - 동기화 상태 변경
  ```

## 성능 테스트

### 1. 지도 성능
- [ ] 마커 로딩
  ```kotlin
  // 테스트 조건
  - 1000개 마커 동시 표시
  - 다양한 줌 레벨
  - 빠른 지도 이동
  
  // 성능 기준
  - 프레임 드롭 없음
  - 메모리 사용량 안정적
  - 응답성 유지
  ```

### 2. 동기화 성능
- [ ] 대량 데이터
  ```kotlin
  // 테스트 조건
  - 1000개 마커
  - 5000개 메모
  - 오프라인 → 온라인 전환
  
  // 성능 기준
  - 동기화 시간 < 1분
  - 배터리 소모 최소화
  - 네트워크 사용량 최적화
  ```

## 에지 케이스

### 1. 네트워크 상태
- [ ] 불안정한 연결
  ```kotlin
  // 테스트 시나리오
  - 연결 끊김/복구 반복
  - 응답 지연
  - 부분 응답
  ```

### 2. 데이터 충돌
- [ ] 동시 수정
  ```kotlin
  // 테스트 시나리오
  - 두 기기에서 동시 수정
  - 오프라인 상태에서 수정 후 동기화
  - 서버 데이터 변경 후 로컬 수정
  ```

### 3. 리소스 제한
- [ ] 저장 공간 부족
- [ ] 메모리 부족
- [ ] CPU 부하

## 테스트 데이터
```kotlin
object TestData {
    val TEST_MARKERS = listOf(
        Marker(id = "1", title = "집", lat = 37.123, lon = 127.123),
        Marker(id = "2", title = "회사", lat = 37.456, lon = 127.456)
    )
    
    val TEST_MEMOS = listOf(
        Memo(id = "1", markerId = "1", content = "테스트 메모 1"),
        Memo(id = "2", markerId = "1", content = "테스트 메모 2")
    )
} 