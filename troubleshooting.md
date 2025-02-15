# 문제 해결 가이드라인

## 일반적인 디버깅 절차
1. 로그 확인
   - Logcat 필터링: `com.parker.hotkey`
   - 에러 로그 레벨: ERROR, WARN
   - 동기화 로그 태그: `SyncManager`
   - 위치 관련 태그: `LocationPermissionDelegate`

2. 네트워크 상태 확인
   - API 응답 코드
   - 요청/응답 본문
   - 토큰 유효성

3. 데이터베이스 확인
   - Room DB 조회
   - 동기화 상태
   - 버전 충돌

## 자주 발생하는 문제

### 1. 위치 권한 및 추적 문제
- 증상: 
  1. 위치 권한은 있으나 위치 소스 활성화 실패
  2. 위치 업데이트가 수신되지 않음
  3. 첫 위치 업데이트 후 이동 안됨
- 원인:
  1. 권한 획득 타이밍 문제
  2. 위치 소스 초기화 실패
  3. 콜백 등록 누락
- 해결:
  1. `LocationPermissionDelegate.resetPermissionCheck()` 호출
  2. 위치 소스 재활성화
  3. 로그 확인 후 콜백 재설정

### 2. 인증 관련
- 증상: 401 Unauthorized
- 원인:
  1. 토큰 만료
  2. 잘못된 토큰
  3. 카카오 로그인 실패
- 해결:
  1. `AuthManager.refreshToken()` 호출
  2. 토큰 재발급
  3. 강제 로그아웃 후 재로그인

### 3. 동기화 문제
- 증상: 데이터 불일치
- 원인:
  1. 버전 충돌
  2. 네트워크 오류
  3. 동기화 실패
- 해결:
  1. `SyncManager.forceSyncAll()` 호출
  2. 로컬 데이터 리셋
  3. 충돌 해결 다이얼로그 표시

### 4. 지도 관련
- 증상: 마커 표시 안됨
- 원인:
  1. 잘못된 좌표
  2. 줌 레벨 문제
  3. 메모리 부족
- 해결:
  1. 좌표 유효성 검사
  2. 줌 레벨 리셋
  3. 메모리 캐시 정리

## 로그 확인 방법

### 1. 개발 로그
```kotlin
// 디버그 로그
timber.d("마커 생성: id=${marker.id}")

// 에러 로그
timber.e(e, "마커 생성 실패")

// 정보 로그
timber.i("동기화 시작")
```

### 2. 크래시 로그
```kotlin
try {
    // 위험 작업
} catch (e: Exception) {
    FirebaseCrashlytics.getInstance().recordException(e)
    timber.e(e, "크래시 발생")
}
```

### 3. 네트워크 로그
```kotlin
// OkHttp 인터셉터
class LoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        timber.d("API 요청: ${request.url}")
        // ... 로깅 로직
    }
}
```

## 성능 모니터링

### 1. 메모리 사용량
```kotlin
private fun checkMemory() {
    val runtime = Runtime.getRuntime()
    val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024
    timber.d("사용 메모리: ${usedMemory}KB")
}
```

### 2. 응답 시간
```kotlin
private fun measureApiCall(block: suspend () -> Unit) {
    val startTime = System.currentTimeMillis()
    block()
    val endTime = System.currentTimeMillis()
    timber.d("API 호출 시간: ${endTime - startTime}ms")
}
```

## 문제 해결 시나리오

### 시나리오 1: 마커 생성 실패
1. 증상: 마커 생성 버튼 클릭 후 반응 없음
2. 확인사항:
   - 로그캣에서 에러 확인
   - 네트워크 요청/응답 확인
   - 로컬 DB 상태 확인
3. 해결방법:
   - 네트워크 오류: 재시도
   - DB 오류: 캐시 정리
   - 위치 오류: GPS 재설정

### 시나리오 2: 동기화 충돌
1. 증상: 409 Conflict 응답
2. 확인사항:
   - 서버/로컬 버전 비교
   - 변경 내역 확인
   - 충돌 데이터 확인
3. 해결방법:
   - 자동 병합 시도
   - 사용자 선택 요청
   - 강제 동기화

### 시나리오 3: 메모리 부족
1. 증상: OutOfMemoryError
2. 확인사항:
   - 메모리 사용량 모니터링
   - 이미지 캐시 크기
   - 메모리 누수 지점
3. 해결방법:
   - 캐시 정리
   - 이미지 리사이징
   - 메모리 누수 수정 