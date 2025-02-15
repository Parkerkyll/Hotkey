# 개발 순서

1인개발자 프로젝트

# 작업내용 저장 시 디테일하게 작성

> GitHub Repository: https://github.com/Parkerkyll/Hotkey
> Latest Commit: 7c07027 (feat: 카카오 로그인 구현 및 보안 강화)
> 
> 프로젝트의 기본 구조 및 데이터 모델에 대한 상세 내용은 [data_structure.md](data_structure.md) 문서를 참조하세요.
> 데이터 흐름에 대한 상세 내용은 [data_flow.md](data_flow.md) 문서를 참조하세요.
> AWS Lambda 함수의 상세 구현 내용은 [lambda_functions.md](lambda_functions.md) 문서를 참조하세요.
> 테스트 시나리오에 대한 상세 내용은 [test_scenarios.md](test_scenarios.md) 문서를 참조하세요.
> 


## 개발 이력
| 날짜 | 커밋 | 설명 | 폴더 |
|------|------|------|------|
| 2025-02-07 | - | 초기 프로젝트 설정 및 기본 지도 기능 구현 | (018) |
| 2025-02-08 | - | 메모 다이얼로그 UI 개선 | (020) |
| 2025-02-10 | - | 맵 레벨 기준 만들기 / 메모 없는 마커 자동 삭제 기능 구현 | (029) |
| 2025-02-13 | - | Room DB Entity 설계 및 동기화 전략 구체화 | (030) |
| 2025-02-14 | - | ViewModel 구조 개선 및 의존성 분리 | (031) |
| 2025-02-15 | - | LocationManager 구현 및 에러 처리 개선 | (033) |
| 2025-02-16 | 16eb123 | 카카오 로그인 구현 및 보안 강화 | (034) |
| 2025-02-16 | 7c07027 | 자동 로그인 기능 개선 | (035) |
   - LoginTestActivity 구현
     - 카카오 로그인 버튼 UI 구현
     - 카카오톡/계정 로그인 분기 처리
     - 로그인 성공/실패 핸들링
   - 보안 강화
     - 해시키 코드 local.properties로 이동
     - API 키 노출 방지
   - 코드 정리
     - 불필요한 주석 및 로그 제거
     - 미사용 리소스 정리
     - 코드 포맷팅 개선
   - 문서 업데이트
     - 개발 순서 문서 갱신
     - 테스트 시나리오 추가
     - 데이터 구조 문서에 인증 컴포넌트 추가

## 구현 전략 문서 (완료)
- [x] [동기화 전략](strategies/sync_strategy.md)
  - [x] 증분 업데이트 전략
  - [x] 충돌 해결 전략
  - [x] 배치 처리 전략

- [x] [오프라인 모드 전략](strategies/offline_strategy.md)
  - [x] 네트워크 중단 대응
  - [x] 데이터 일관성 유지
  - [x] 자동 동기화 전략

- [x] [신규 사용자 시나리오](strategies/user_scenario.md)
  - [x] 초기 실행 시나리오
  - [x] 권한 획득 플로우
  - [x] 데이터 로딩 전략

- [x] [에러 처리 전략](strategies/error_handling_strategy.md)
  - [x] 레이어별 에러 처리
  - [x] 재시도 정책
  - [x] 사용자 피드백

- [x] [성능 최적화 전략](strategies/performance_strategy.md)
  - [x] 데이터베이스 최적화
  - [x] 네트워크 최적화
  - [x] 메모리 관리

## 1단계: 카카오 로그인 (완료)
1. **카카오 SDK 연동**
   - [x] SDK 설치 및 초기화
   - [x] 키 해시 등록
   - [x] 매니페스트 설정
   - [x] 기본 로그인 테스트

2. **로그인 화면 구현**
   - [x] 로그인 UI 구현
   - [x] 카카오 로그인 버튼 연동
   - [x] 로딩/에러 상태 처리
   - [x] UI 테스트

3. **인증 로직 및 토큰 관리**
   - [x] AuthRepository 구현
   - [x] JWT 토큰 관리
   - [x] 자동 로그인
   - [x] 유닛 테스트

4. **통합 테스트**
   - [x] 전체 로그인 플로우 테스트
   - [x] 에러 케이스 테스트
   - [x] 토큰 갱신 테스트
   - [x] 성능 테스트

## 2단계: 개발 환경 설정 (완료)
1. **전체 개발 환경 설정**
   - [x] Kotlin 1.9.0 설정
   - [x] Android Gradle Plugin 설정
   - [x] 코드 스타일 및 린트 규칙 설정
   - [x] ProGuard 규칙 설정

2. **CI/CD 파이프라인 구성 (추후 필요시 구현)**
   ```yaml
   # 빌드 자동화
   - 브랜치별 자동 빌드
   - 테스트 자동 실행
   - 코드 품질 검사 (ktlint, detekt)
   
   # 배포 자동화
   - 릴리즈 브랜치 머지 시 자동 배포
   - 버전 태그 자동 생성
   - Firebase App Distribution 배포
   ```

3. **인프라 통합 테스트**
   - [x] AWS 리소스 연결 테스트
   - [x] API 엔드포인트 테스트
   - [x] 보안 설정 검증

## 3단계: 지도/마커 기능 (완료)
1. **Naver Maps SDK 연동**
   - [x] SDK 초기화 및 기본 설정
   - [x] 지도 표시 및 컨트롤

2. **마커 기능 구현**
   - [x] 마커 즉시 생성 (클릭)
   - [x] 메모장 자동 생성
   - [x] 메모 없는 마커 15초 후 자동 삭제
   - [x] 마커 투명도 0.4
   - [x] 마커당 메모 최대 10개
   - [x] Geohash 6자리 기반 검색
   - [ ] 수동 동기화 버튼 --(미진행) AWS 서버 연결 시 진행 

3. **지도 설정**
   - [x] 줌 레벨: 기본 17, 최소 14, 최대 19
   - [x] 마커 생성 시 줌 18, 선택 시 줌 18, 작업 후 지도로 복귀 시 17
   - [x] 포그라운드 전환 시 줌 17로 복귀
   - [x] 포그라운드 전환시 내위치 복귀
   
  

## 4단계: 데이터 관리 및 동기화 (진행중)

1. **Room DB 구현** (완료)
   - [x] Entity 구현
   - [x] DAO 구현
   - [x] Repository 구현
   - [x] 단위 테스트 구조 설계

2. **위치 관리 시스템 구현** (완료)
   ```kotlin
   주요 구현사항:
   1. LocationManager 인터페이스 정의
      - getLocationUpdates(): Flow<Location>
      - getLastLocation(): Location?

   2. LocationManagerImpl 구현
      - FusedLocationProviderClient 활용
      - 권한 기반 위치 정보 접근
      - 에러 처리 강화

   3. 에러 처리 개선
      - MapError sealed class 구현
      - 권한/위치/네트워크 에러 분리
      - 사용자 친화적 에러 메시지
   ```

3. **의존성 주입 구조 개선** (완료)
   ```kotlin
   변경사항:
   1. ManagerModule 개선
      - LocationManager 바인딩 추가
      - TokenManager 제공 로직 개선

   2. MapModule 업데이트
      - LocationManager 의존성 추가
      - MapViewModel 생성자 수정

   3. UseCaseModule 정리
      - DeleteMarkerWithValidationUseCase
      - ScheduleMarkerDeletionUseCase
   ```

4. **AWS 인프라 구성** (미진행)
   - [ ] DynamoDB 테이블 설계
   - [ ] Lambda 함수 구현
   - [ ] API Gateway 설정

### 4단계 완료 조건
- [x] Room DB 구현
  - [x] Entity 및 DAO 구현 완료
  - [x] Repository 패턴 적용
  - [x] 기본 단위 테스트 구조 설계
  
- [x] 위치 관리 시스템
  - [x] LocationManager 인터페이스 설계
  - [x] LocationManagerImpl 구현
  - [x] 권한 처리 및 에러 핸들링
  
- [ ] AWS 인프라 구성 (미진행)
  - [ ] DynamoDB 테이블 생성
  - [ ] Lambda 함수 구현
  - [ ] API Gateway 설정
  
- [ ] 동기화 시스템 (진행중)
  - [x] SyncManager 기본 구조 설계
  - [ ] 충돌 해결 테스트
  - [ ] 네트워크 상태 모니터링

## 5단계: 부가 기능 구현 (미진행)
1. [ ] **메모 기능**
   - [ ] 메모 CRUD 기능 구현
   - [ ] 마커와 메모 연동
   - [ ] UI/UX 최적화

2. [ ] **검색 기능**
   - [ ] Geohash 기반 위치 검색
   - [ ] 기타 검색/필터링 기능은 추후 필요시 구현

3. [ ] **사용성 개선**
   - [ ] 기본적인 에러 처리
   - [ ] 사용자 피드백 구현

## 6단계: 최적화 (미진행)
1. [ ] **성능 최적화**
   - [ ] Room DB 인덱싱 최적화
   - [ ] 쿼리 성능 개선
   - [ ] 메모리 사용량 최적화
   - [ ] 네트워크 최적화

2. [ ] **안정성 개선**
   - [ ] 에러 처리 강화
   - [ ] 예외 상황 대응
   - [ ] 테스트 보강

## 전체 체크리스트
- [x] 카카오 로그인
  - [x] SDK 초기화
  - [x] 로그인 UI
  - [x] 토큰 관리
  - [x] 자동 로그인
  - [x] 테스트 통과

- [x] 지도/마커 기능
  - [x] 지도 표시
  - [x] 마커 CRUD
  - [x] 줌 레벨 제어
  - [x] 메모 연동 (최대 10개)
  - [x] 자동 삭제 (15초)

- [ ] Room DB 구현 (진행중)
- [ ] AWS 인프라 구축 (미진행)
- [ ] 동기화 시스템 구현 (진행중)
- [ ] 부가 기능 구현 (미진행)
- [ ] 최적화 및 안정성 개선 (미진행)
- [ ] 최적화 및 안정성 개선 (미진행)

### 메모 다이얼로그 UI 개선 (2024-02-08)
1. **레이아웃 통합 및 정리**
   - [x] `dialog_memo_title.xml` 삭제 (중복 UI 제거)
   - [x] `dialog_memo_list.xml`에 헤더 통합
   - [x] 버튼 레이아웃 최적화
     ```kotlin
     변경사항:
     - MEMO 헤더와 저장 버튼을 하나로 통합
     - 지도로 돌아가기 버튼 위치 변경
     - 마커 삭제 버튼 스타일 유지
     ```

2. **코드 정리**
   - [x] `MemoListDialog.kt` 커스텀 타이틀 뷰 관련 코드 제거
   - [x] 저장 버튼 이벤트 핸들러 통합
   - [x] 불필요한 코드 정리

### 메모 없는 마커 자동 삭제 기능 구현 (2024-02-10)
1. **WorkManager 설정**
   - [x] WorkManager 의존성 추가
   - [x] Hilt Worker 설정
   - [x] Application 클래스에 WorkManager 초기화 추가

2. **EmptyMarkerDeleteWorker 구현**
   - [x] 마커 자동 삭제 Worker 클래스 구현
   - [x] Hilt 의존성 주입 설정
   - [x] 마커 삭제 로직 구현
     ```kotlin
     주요 기능:
     - 15초 후 자동 삭제 예약
     - 메모 추가 시 삭제 작업 취소
     - 메모가 있는 마커는 삭제하지 않음
     - 삭제 결과에 따른 UI 자동 업데이트
     ```

3. **MapViewModel 개선**
   - [x] WorkManager 작업 상태 관찰 추가
   - [x] 마커 삭제 후 UI 자동 업데이트 구현
   - [x] 에러 처리 및 로깅 개선

4. **테스트 및 안정성**
   - [x] 자동 삭제 기능 테스트
   - [x] UI 업데이트 검증
   - [x] 에러 케이스 처리 확인

### 오늘의 작업 내용 (2024-02-15)
1. **LocationManager 구현**
   - [x] LocationManager 인터페이스 정의
   - [x] LocationManagerImpl 구현
   - [x] FusedLocationProviderClient 연동
   - [x] 권한 처리 로직 구현

2. **에러 처리 시스템 개선**
   - [x] MapError sealed class 구현
   - [x] 에러 타입 세분화
     ```kotlin
     sealed class MapError {
         class PermissionError
         class LocationError
         class NetworkError
         class UnknownError
     }
     ```
   - [x] 에러 메시지 현지화

3. **의존성 주입 구조 개선**
   - [x] ManagerModule 업데이트
   - [x] MapModule 의존성 정리
   - [x] UseCaseModule 구조화

4. **테스트 구조 설계**
   - [x] LocationManager 테스트 케이스 정의
   - [x] 에러 처리 테스트 시나리오 작성
   - [x] 의존성 주입 테스트 준비

## 현재 진행 상황 (2024-02-15)

### 완료된 작업
1. 위치 권한 및 추적 기능
   - [x] 위치 권한 요청 및 처리 로직 개선
   - [x] 위치 소스 활성화 안정성 향상
   - [x] 첫 위치 업데이트 시 자동 이동 구현
   - [x] 포그라운드/백그라운드 전환 처리

2. 지도 기본 기능
   - [x] 네이버 지도 초기화 및 설정
   - [x] 줌 레벨 제어 (14-19)
   - [x] 나침반 기능
   - [x] 현재 위치 표시
   - [x] geohash6 반경 표시 (1.2km)

3. 마커 기본 기능
   - [x] 마커 생성/표시
   - [x] 마커 투명도 설정
   - [x] 마커 클릭 이벤트
   - [x] 메모장 연동

### 진행 중인 작업
1. 메모 기능
   - [ ] 마커당 메모 최대 10개 제한
   - [ ] 메모 없는 마커 자동 삭제 (15초)
   - [ ] 마지막 메모 삭제 시 마커 자동 삭제

2. 데이터 동기화
   - [ ] Geohash 기반 마커 검색
   - [ ] 위치 이동 시 마커 업데이트
   - [ ] 백그라운드 동기화

### 다음 작업 계획
1. Room DB 최적화
   - [ ] 인덱스 설정
   - [ ] 쿼리 성능 개선
   - [ ] 트랜잭션 관리

2. 성능 개선
   - [ ] 메모리 사용량 최적화
   - [ ] 위치 업데이트 최적화
   - [ ] UI 렌더링 최적화

### 자동 로그인 기능 개선 (2025-02-16)
1. **자동 로그인 로직 개선**
   - [x] MainActivity를 LAUNCHER 액티비티로 변경
   - [x] 앱 시작 시 자동 로그인 체크 로직 구현
   - [x] 토큰 관리 및 갱신 로직 개선
   - [x] 로그인 상태 체크 및 처리 개선

2. **데이터 보존 개선**
   - [x] 앱 시작 시 데이터 초기화 로직 제거
   - [x] 토큰 및 사용자 데이터 보존 처리
   - [x] SharedPreferences 암호화 저장 구현

3. **로깅 시스템 강화**
   - [x] 자동 로그인 프로세스 상세 로깅 추가
   - [x] 토큰 관리 관련 로그 개선
   - [x] 에러 상황 상세 로깅 구현

4. **안정성 개선**
   - [x] 토큰 만료 시 자동 갱신 처리
   - [x] 네트워크 오류 처리 개선
   - [x] 예외 상황 처리 강화

### 다음 작업 계획
1. **AWS 인프라 구축**
   - [ ] DynamoDB 테이블 설계
   - [ ] Lambda 함수 구현
   - [ ] API Gateway 설정

2. **동기화 시스템 구현**
   - [ ] 오프라인 데이터 처리
   - [ ] 충돌 해결 전략 구현
   - [ ] 자동 동기화 구현