# MapViewModel 단계별 리팩토링 계획

## 1. 개요

MapViewModel 클래스는 현재 약 780줄에 달하는 코드로 다음과 같은 문제점을 가지고 있습니다:

1. **너무 많은 책임**: 마커 관리, 메모 관리, 위치 추적, 편집 모드 관리, 상태 관리 등 다양한 책임을 가짐
2. **코드 복잡성**: 다양한 이벤트 처리와 상태 관리 로직이 혼재되어 있음
3. **생명주기 관련 코드 분산**: 초기화, 구독, 해제 관련 코드가 여러 곳에 흩어져 있어 초기화 순서 문제 발생
4. **테스트 어려움**: 의존성이 많고 로직이 복잡하여 단위 테스트가 어려움
5. **유지보수 어려움**: 코드 크기와 복잡성으로 인해 새로운 기능 추가나 버그 수정이 어려움

이 문제를 해결하기 위해 MapViewModel의 책임을 더 작고 관리하기 쉬운 클래스들로 분리하는 리팩토링을 진행합니다. 리팩토링은 기능 오작동 위험을 최소화하기 위해 작은 단계로 진행됩니다.

## 2. 준비 단계

### 2.1 백업 및 초기 세팅

1. **백업 브랜치 생성**
   ```bash
   git checkout -b refactoring/mapviewmodel-backup
   git add .
   git commit -m "백업: MapViewModel 리팩토링 전 상태"
   git checkout -b refactoring/mapviewmodel-step1
   ```

2. **테스트 시나리오 정의**
   - 마커 생성 및 표시 테스트
   - 마커 삭제 테스트
   - 메모 생성 및 로드 테스트
   - 모드 전환(읽기/쓰기) 테스트
   - 위치 추적 테스트
   - 앱 최초 실행 시 마커 삭제 테스트 (버그 확인)

3. **현재 상태 문서화**
   - 주요 화면 스크린샷 캡처
   - 주요 기능 정상 작동 여부 체크리스트 작성

## 3. 단계별 리팩토링 계획

### 3.1 단계 1: 생명주기 관리 코드 통합 및 정리 (신규 최우선 작업)

**목표**: 분산된 생명주기 관련 코드(초기화, 구독, 해제)를 한 곳으로 모아 초기화 순서 문제 해결의 기반 마련

1. **MapViewModel 생명주기 코드 그룹화**
   ```kotlin
   class MapViewModel @Inject constructor(
       // 의존성...
   ) : ViewModel() {
       
       init {
           initializeManagers()
           setupStateCollectors()
           setupEventSubscriptions()
       }
       
       private fun initializeManagers() {
           // 모든 매니저 초기화 로직
           markerManager.initialize()
           memoManager.initialize()
           // 기타 매니저 초기화...
       }
       
       private fun setupStateCollectors() {
           viewModelScope.launch {
               // 상태 수집 로직 통합
               // 기존 setupStateCollectors() 코드...
           }
       }
       
       private fun setupEventSubscriptions() {
           // 모든 이벤트 구독 코드를 한 곳에 모음
           subscribeToMarkerEvents()
           subscribeToMemoEvents()
           subscribeToLocationEvents()
           subscribeToEditModeEvents()
       }
       
       private fun subscribeToMarkerEvents() {
           // 마커 관련 이벤트 구독 로직
       }
       
       private fun subscribeToMemoEvents() {
           // 메모 관련 이벤트 구독 로직
       }
       
       // 기타 구독 메서드...
       
       override fun onCleared() {
           super.onCleared()
           cleanupResources()
       }
       
       private fun cleanupResources() {
           // 모든 리소스 해제 로직
           locationTracker.stopTracking()
           // 기타 리소스 해제...
       }
   }
   ```

2. **MapLifecycleManager 클래스 도입 검토**
   ```kotlin
   @Singleton
   class MapLifecycleManager @Inject constructor(
       private val markerManager: MarkerManager,
       private val memoManager: MemoManager,
       private val editModeManager: EditModeManager,
       private val locationTracker: LocationTracker
   ) {
       fun initialize(scope: CoroutineScope) {
           // 초기화 로직
           markerManager.initialize()
           memoManager.initialize()
           editModeManager.initialize()
           locationTracker.initialize()
       }
       
       fun setupEventSubscriptions(scope: CoroutineScope, eventHandler: MapEventHandler) {
           // 모든 이벤트 구독 설정
           markerManager.subscribeToEvents(scope) { event ->
               // 이벤트 처리...
           }
           
           // 기타 구독 설정...
       }
       
       fun cleanup() {
           // 리소스 해제 로직
           locationTracker.stopTracking()
           // 기타 리소스 해제...
       }
   }
   ```

3. **초기화 순서 명시적 선언**
   ```kotlin
   private fun initializeManagers() {
       // 명시적인 초기화 순서 정의
       Timber.d("매니저 초기화 시작")
       
       // 1. 기본 매니저 초기화
       editModeManager.initialize()
       
       // 2. 위치 추적 초기화
       locationTracker.initialize()
       
       // 3. 마커 관련 초기화
       markerManager.initialize()
       
       // 4. 메모 관련 초기화
       memoManager.initialize()
       
       Timber.d("매니저 초기화 완료")
   }
   ```

4. **구독 순서 명시적 선언**
   ```kotlin
   private fun setupEventSubscriptions() {
       Timber.d("이벤트 구독 설정 시작")
       
       // 명시적인 구독 순서 정의
       // 1. 먼저 마커 이벤트 구독 설정
       subscribeToMarkerEvents()
       
       // 2. 그 다음 메모 이벤트 구독 설정
       subscribeToMemoEvents()
       
       // 3. 위치 이벤트 구독 설정
       subscribeToLocationEvents()
       
       // 4. 편집 모드 이벤트 구독 설정
       subscribeToEditModeEvents()
       
       Timber.d("이벤트 구독 설정 완료")
   }
   ```

5. **검증 및 테스트**
   - 앱 빌드 및 실행
   - 초기화 로그 확인
   - 앱 최초 실행 시 마커 삭제가 정상적으로 UI에 반영되는지 확인
   - 모드 전환, 메모 로드 등 기본 기능이 정상 작동하는지 확인

### 3.2 단계 2: 이벤트 버퍼링 메커니즘 구현

**목표**: 초기화 순서 문제 해결을 위한 이벤트 버퍼링 메커니즘 구현

#### 3.2.1 상세 구현 단계

이벤트 버퍼링 메커니즘은 테스트 가능한 작은 단위로 나누어서 구현합니다. 각 단계마다 테스트를 진행하여 문제를 조기에 발견하고 해결합니다.

1. **이벤트 이중 발행 패턴 확장 적용 (예상 소요: 1일) - 완료 (2025-03-18)

MarkerManagerImpl에 이미 적용된 이벤트 이중 발행 패턴을 다른 매니저 클래스에도 적용합니다.

코드 검토 결과:
- MemoManagerImpl은 이미 `bufferOrEmitEvent()`만 사용하는 단일 발행 패턴 적용됨
- EditModeManagerImpl은 이미 `bufferOrEmitEvent()`만 사용하는 단일 발행 패턴 적용됨
- LocationTrackerImpl은 이벤트 스트림이 없어서 적용할 필요가 없음

따라서, 이벤트 이중 발행 패턴은 필요한 곳에 모두 적용되어 있거나, 적용할 필요가 없는 상태입니다.

2. **구독 방식 순차적 변경** (예상 소요: 1.5일) - 완료 (2025-03-19)

MapViewModel의 이벤트 구독 방식을 한 클래스씩 순차적으로 변경합니다.

각 매니저 클래스별 변경 내용:

1. **MemoManager 구독 방식 변경** - ✅ 완료
   ```kotlin
   // 기존 코드
   /*
   private fun subscribeToMemoEvents() {
       memoManager.events
           .onEach { event: MemoEvent ->
               // 이벤트 처리...
           }
           .catch { e -> 
               Timber.e(e, "메모 이벤트 구독 중 오류 발생")
               handleError(e, "메모 이벤트 처리 중 오류가 발생했습니다.")
           }
           .launchIn(viewModelScope)
   }
   */
   
   // 새 코드
   private fun subscribeToMemoEvents() {
       memoManager.subscribeToEvents(viewModelScope) { event ->
           try {
               Timber.d("메모 이벤트 수신: $event")
               when (event) {
                   is MemoEvent.MemosLoaded -> {
                       Timber.d("메모 로드 성공 이벤트: ${event.markerId}, ${event.memos.size}개")
                   }
                   // 나머지 이벤트 처리...
               }
           } catch (e: Exception) {
               Timber.e(e, "메모 이벤트 처리 중 오류 발생")
               handleError(e, "메모 이벤트 처리 중 오류가 발생했습니다.")
           }
       }
   }
   ```

2. **EditModeManager 구독 방식 변경** - ✅ 완료
   ```kotlin
   // 기존 코드
   /*
   private fun subscribeToEditModeEvents() {
       editModeManager.events
           .onEach { event: EditModeEvent ->
               // 이벤트 처리...
           }
           .catch { e -> 
               Timber.e(e, "편집 모드 이벤트 구독 중 오류 발생")
               handleError(e, "편집 모드 이벤트 처리 중 오류가 발생했습니다.")
           }
           .launchIn(viewModelScope)
   }
   */
   
   // 새 코드
   private fun subscribeToEditModeEvents() {
       editModeManager.subscribeToEvents(viewModelScope) { event ->
           try {
               Timber.d("편집 모드 이벤트 수신: $event")
               when (event) {
                   is EditModeEvent.ModeChanged -> {
                       Timber.d("편집 모드 변경 이벤트: ${if (event.isEditMode) "쓰기모드" else "읽기모드"}")
                   }
                   // 나머지 이벤트 처리...
               }
           } catch (e: Exception) {
               Timber.e(e as Throwable, "편집 모드 이벤트 처리 중 오류 발생")
               handleError(e, "편집 모드 이벤트 처리 중 오류가 발생했습니다.")
           }
       }
   }
   ```

3. **LocationTracker 구독 방식 변경** - ✅ 해당 없음 (이벤트 스트림 없음)
   - LocationTracker는 이벤트 스트림이 없고 StateFlow만 사용하므로 변경이 필요하지 않음
   - 현재 `subscribeToLocationEvents()` 메소드는 비어있으며 필요한 경우 나중에 구현 예정

4. **MarkerManager 구독 방식 변경** - ✅ 완료
   ```kotlin
   // 기존 코드
   /*
   private fun subscribeToMarkerEvents() {
       markerManager.markerEvents
           .onEach { event: MarkerEvent ->
               // 이벤트 처리...
           }
           .catch { e -> 
               Timber.e(e, "마커 이벤트 구독 중 오류 발생")
               handleError(e, "마커 이벤트 처리 중 오류가 발생했습니다.")
           }
           .launchIn(viewModelScope)
   }
   */
   
   // 새 코드
   private fun subscribeToMarkerEvents() {
       markerManager.subscribeToEvents(viewModelScope) { event ->
           try {
               Timber.d("마커 이벤트 수신: $event")
               when (event) {
                   is MarkerEvent.MarkerCreationSuccess -> {
                       // 이벤트 처리...
                   }
                   // 나머지 이벤트 처리...
               }
           } catch (e: Exception) {
               Timber.e(e, "마커 이벤트 처리 중 오류 발생")
               handleError(e, "마커 이벤트 처리 중 오류가 발생했습니다.")
           }
       }
   }
   ```

### 3.3 단계 3: 이중 발행 패턴 최적화 (예상 소요: 0.5일) - 완료 (2025-03-20)

앱이 안정적으로 작동하는 것을 확인한 후 이중 발행 패턴을 최적화합니다.

1. **BaseManager 클래스의 버퍼링 메커니즘 검토 및 개선** - ✅ 완료
   ```kotlin
   // 이벤트 버퍼 크기 제한 추가
   private val MAX_BUFFER_SIZE = 100 // 최대 버퍼 크기 제한
   private val eventBuffer = ConcurrentLinkedQueue<E>()
   
   protected suspend fun bufferOrEmitEvent(event: E) {
       try {
           if (_hasSubscribers.value) {
               Timber.d("${javaClass.simpleName} 실시간 이벤트 발행: $event")
               _events.emit(event)
           } else {
               eventBufferLock.withLock {
                   // 버퍼 크기 제한 검사
                   if (eventBuffer.size >= MAX_BUFFER_SIZE) {
                       Timber.w("${javaClass.simpleName} 이벤트 버퍼 최대 크기 도달, 가장 오래된 이벤트 제거")
                       eventBuffer.poll()
                   }
                   
                   Timber.d("${javaClass.simpleName} 이벤트 버퍼링 중: $event (구독자 없음)")
                   eventBuffer.add(event)
               }
           }
       } catch (e: Exception) {
           Timber.e(e, "${javaClass.simpleName} 이벤트 발행 실패: $event")
       }
   }
   ```

2. **MarkerManagerImpl 클래스 이중 발행 패턴 최적화** - ✅ 완료
   ```kotlin
   // 기존 코드
   /*
   coroutineScope.launch {
       bufferOrEmitEvent(MarkerEvent.MarkerSelected(markerId))
       // 같은 이벤트를 markerEvents로도 발행
       _markerEvents.emit(MarkerEvent.MarkerSelected(markerId))
   }
   */
   
   // 새 코드
   coroutineScope.launch {
       bufferOrEmitEvent(MarkerEvent.MarkerSelected(markerId))
       // 이중 발행 제거
       // _markerEvents.emit(MarkerEvent.MarkerSelected(markerId))
   }
   
   // 호환성을 위해 events를 markerEvents로 자동 전달하는 코드 추가
   init {
       // events를 markerEvents로 전달하여 호환성 유지
       coroutineScope.launch {
           events.collect { event ->
               try {
                   _markerEvents.emit(event)
                   Timber.d("BaseManager events를 markerEvents로 전달: $event")
               } catch (e: Exception) {
                   Timber.e(e, "이벤트 전달 중 오류 발생: $event")
               }
           }
       }
       
       // 기존 코드...
   }
   ```

#### 3.3.4 단계 4: 통합 테스트 및 버그 수정 (예상 소요: 1일)

모든 변경이 완료된 후 앱 전체를 테스트하고 발견된 버그를 수정합니다.

1. **앱 삭제 후 재설치 테스트** - 진행 중
   - 앱을 완전히 삭제하고 새로 설치
   - 마커 생성 및 삭제 테스트
   - UI 업데이트가 즉시 반영되는지 확인

2. **기능별 테스트**
   - 쓰기모드 타이머 및 전환 테스트
   - 메모 생성, 로드, 수정 테스트
   - 마커 생성, 삭제, 선택 테스트
   - 위치 추적 기능 테스트

3. **예외 상황 테스트**
   - 네트워크 연결 해제 시 동작 테스트
   - 권한 거부 시 동작 테스트
   - 백그라운드-포그라운드 전환 시 동작 테스트

4. **버그 수정**
   - 발견된 버그 목록 작성
   - 우선순위에 따라 버그 수정 진행
   - 수정 후 재테스트

## 4. 검증 방법

각 단계마다 다음 검증 절차를 따릅니다:

1. **빌드 및 실행 검증**
   - 앱이 오류 없이 빌드되고 실행되는지 확인

2. **기능 테스트**
   - 마커 생성/표시/삭제
   - 메모 생성/로드/삭제
   - 읽기/쓰기 모드 전환
   - 위치 추적

3. **에러 케이스 테스트**
   - 네트워크 오류 처리
   - 권한 거부 처리

4. **성능 테스트**
   - 메모리 사용량 확인
   - UI 응답성 확인

## 5. 롤백 계획

문제 발생 시 다음 절차를 따릅니다:

1. **단계별 롤백**
   - 각 단계는 별도의 커밋으로 관리
   - 문제 발생 시 해당 단계만 롤백
   ```bash
   git revert <해당_단계_커밋_해시>
   ```

2. **전체 롤백 (긴급 상황)**
   - 복구 불가능한 문제 발생 시 백업 브랜치로 복원
   ```bash
   git checkout refactoring/mapviewmodel-backup
   ```

3. **단계 재분할**
   - 특정 단계에서 문제가 지속적으로 발생하면 해당 단계를 더 작은 단계로 분할

## 6. 예상 일정

1. **준비 단계**: 0.5일
2. **단계 1** (생명주기 관리 코드 통합 및 정리): 0.5일
3. **단계 2** (이벤트 버퍼링 메커니즘 구현): 1일
4. **단계 3** (이중 발행 패턴 최적화): 0.5일 - **완료**
5. **단계 4** (통합 테스트 및 버그 수정): 1일 - **진행 중**
6. **단계 5** (불필요한 의존성 제거): 0.5일 - **미시작**
7. **단계 6** (복잡한 메서드 최적화): 1일 - **미시작**

**총 예상 소요 기간**: 4일

## 7. 리팩토링 진행 상황 (2025-03-14)

### 7.1 현재까지 파악된 작업 상황

MapViewModel 리팩토링을 계획하고 진행 상황을 확인한 결과, 일부 작업이 이미 완료되어 있음을 확인했습니다:

1. **이미 구현된 컴포넌트**:
   - `MapEventHandler` 인터페이스 및 `MapEventHandlerImpl` 구현체가 이미 존재
   - `MapStateProcessor` 클래스가 이미 구현되어 있음
   - 기본적인 `BaseManager` 클래스에 `isInitialized()` 함수 추가 완료 (2025-03-05)
   - 모든 매니저 클래스(EditModeManager, MarkerManager, MemoManager, LocationTracker)의 생명주기 관리 코드 개선 완료

2. **이미 리팩토링된 코드**:
   - MapViewModel의 여러 메서드들이 이미 위임 패턴으로 변경되어 있음:
     - `onMemoDialogDismissed()` → `mapEventHandler.handleMemoDialogDismissed()`
     - `toggleEditMode()` → `mapEventHandler.toggleEditMode()`
     - `deleteMarker()` → `mapEventHandler.handleDeleteMarker(markerId)`
   - 상태 처리 로직이 `MapStateProcessor`로 분리되어 있음
   - MapViewModel의 `initializeLocationTracking()` 메서드가 `locationTracker.initialize()`를 호출하도록 수정 완료

3. **남은 중요 작업**:
   - **생명주기 관리 코드 최종 검증** - 모든 매니저 클래스의 초기화 순서와 안정성 확인
   - 이벤트 버퍼링 메커니즘 구현 (앱 최초 실행 시 마커 삭제 문제 해결을 위해 필요)
   - 일부 남은 비즈니스 로직의 위임 완료
   - 코드 정리 및 불필요한 코드 제거
   - 테스트 케이스 추가

### 7.2 수정된 리팩토링 계획

기존에 세웠던 단계별 계획 중 일부가 이미 구현되어 있으므로, 남은 작업을 중심으로 계획을 조정합니다:

1. **단계 1: 생명주기 관리 코드 최종 검증** (완료)
   - 모든 매니저 클래스에 통일된 초기화 패턴 적용 완료
   - 명시적인 초기화 순서 정의: MapViewModel의 initializeManagers() 메서드에서 초기화 순서 명시적 정의
   - isInitialized() 메서드 추가: 중복 초기화 방지 및 초기화 상태 추적 기능 추가
   - 이벤트 버퍼링과 결합 (예정): 생명주기 관리와 이벤트 버퍼링을 결합하여 초기화 순서 문제 해결

2. **단계 2: 이벤트 버퍼링 메커니즘 구현** (완료)
   - BaseManager 클래스에 이벤트 버퍼 추가
   - 구독자 상태 추적 기능 구현
   - 이벤트 버퍼 처리 로직 구현
   
   ```kotlin
   abstract class BaseManager<E>(
       protected val coroutineScope: CoroutineScope
   ) {
       // 기존 코드...
       
       private val eventBuffer = ConcurrentLinkedQueue<E>()
       private val eventBufferLock = Mutex()
       private val _hasSubscribers = MutableStateFlow(false)
       
       open fun subscribeToEvents(scope: CoroutineScope, handler: suspend (E) -> Unit): Job {
           return scope.launch {
               _hasSubscribers.value = true
               
               // 버퍼링된 이벤트 처리
               processBufferedEvents(handler)
               
               // 실시간 이벤트 구독
               events.collect { event ->
                   Timber.d("${javaClass.simpleName} 실시간 이벤트 수신: $event")
                   handler(event)
               }
           }.also {
               it.invokeOnCompletion {
                   _hasSubscribers.value = false
               }
           }
       }
       
       private suspend fun processBufferedEvents(handler: suspend (E) -> Unit) {
           eventBufferLock.withLock {
               Timber.d("${javaClass.simpleName} 버퍼링된 이벤트 처리 시작 (${eventBuffer.size}개)")
               while (eventBuffer.isNotEmpty()) {
                   val event = eventBuffer.poll()
                   event?.let { handler(it) }
               }
               Timber.d("${javaClass.simpleName} 버퍼링된 이벤트 처리 완료")
           }
       }
       
       protected suspend fun bufferOrEmitEvent(event: E) {
           try {
               if (_hasSubscribers.value) {
                   Timber.d("${javaClass.simpleName} 실시간 이벤트 발행: $event")
                   _events.emit(event)
               } else {
                   eventBufferLock.withLock {
                       // 버퍼 크기 제한 검사
                       if (eventBuffer.size >= MAX_BUFFER_SIZE) {
                           Timber.w("${javaClass.simpleName} 이벤트 버퍼 최대 크기 도달, 가장 오래된 이벤트 제거")
                           eventBuffer.poll()
                       }
                       
                       Timber.d("${javaClass.simpleName} 이벤트 버퍼링 중: $event (구독자 없음)")
                       eventBuffer.add(event)
                   }
               }
           } catch (e: Exception) {
               Timber.e(e, "${javaClass.simpleName} 이벤트 발행 실패: $event")
           }
       }
   }
   ```

#### 2.1 단계 1: 이벤트 이중 발행 패턴 확장 적용 (완료)

MarkerManagerImpl에 이미 적용된 이벤트 이중 발행 패턴을 다른 매니저 클래스에도 적용합니다.

코드 검토 결과:
- MemoManagerImpl은 이미 `bufferOrEmitEvent()`만 사용하는 단일 발행 패턴 적용됨
- EditModeManagerImpl은 이미 `bufferOrEmitEvent()`만 사용하는 단일 발행 패턴 적용됨
- LocationTrackerImpl은 이벤트 스트림이 없어서 적용할 필요가 없음

따라서, 이벤트 이중 발행 패턴은 필요한 곳에 모두 적용되어 있거나, 적용할 필요가 없는 상태입니다.

#### 2.2 구독 방식 순차적 변경 (완료)

MapViewModel의 이벤트 구독 방식을 한 클래스씩 순차적으로 변경했습니다.

각 매니저 클래스별 변경 내용:

1. **MemoManager 구독 방식 변경** - ✅ 완료
   ```kotlin
   // 기존 코드
   /*
   private fun subscribeToMemoEvents() {
       memoManager.events
           .onEach { event: MemoEvent ->
               // 이벤트 처리...
           }
           .catch { e -> 
               Timber.e(e, "메모 이벤트 구독 중 오류 발생")
               handleError(e, "메모 이벤트 처리 중 오류가 발생했습니다.")
           }
           .launchIn(viewModelScope)
   }
   */
   
   // 새 코드
   private fun subscribeToMemoEvents() {
       memoManager.subscribeToEvents(viewModelScope) { event ->
           try {
               Timber.d("메모 이벤트 수신: $event")
               when (event) {
                   is MemoEvent.MemosLoaded -> {
                       Timber.d("메모 로드 성공 이벤트: ${event.markerId}, ${event.memos.size}개")
                   }
                   // 나머지 이벤트 처리...
               }
           } catch (e: Exception) {
               Timber.e(e, "메모 이벤트 처리 중 오류 발생")
               handleError(e, "메모 이벤트 처리 중 오류가 발생했습니다.")
           }
       }
   }
   ```

2. **EditModeManager 구독 방식 변경** - ✅ 완료
   ```kotlin
   // 기존 코드
   /*
   private fun subscribeToEditModeEvents() {
       editModeManager.events
           .onEach { event: EditModeEvent ->
               // 이벤트 처리...
           }
           .catch { e -> 
               Timber.e(e, "편집 모드 이벤트 구독 중 오류 발생")
               handleError(e, "편집 모드 이벤트 처리 중 오류가 발생했습니다.")
           }
           .launchIn(viewModelScope)
   }
   */
   
   // 새 코드
   private fun subscribeToEditModeEvents() {
       editModeManager.subscribeToEvents(viewModelScope) { event ->
           try {
               Timber.d("편집 모드 이벤트 수신: $event")
               when (event) {
                   is EditModeEvent.ModeChanged -> {
                       Timber.d("편집 모드 변경 이벤트: ${if (event.isEditMode) "쓰기모드" else "읽기모드"}")
                   }
                   // 나머지 이벤트 처리...
               }
           } catch (e: Exception) {
               Timber.e(e as Throwable, "편집 모드 이벤트 처리 중 오류 발생")
               handleError(e, "편집 모드 이벤트 처리 중 오류가 발생했습니다.")
           }
       }
   }
   ```

3. **LocationTracker 구독 방식 변경** - ✅ 해당 없음 (이벤트 스트림 없음)
   - LocationTracker는 이벤트 스트림이 없고 StateFlow만 사용하므로 변경이 필요하지 않음
   - 현재 `subscribeToLocationEvents()` 메소드는 비어있으며 필요한 경우 나중에 구현 예정

4. **MarkerManager 구독 방식 변경** - ✅ 완료
   ```kotlin
   // 기존 코드
   /*
   private fun subscribeToMarkerEvents() {
       markerManager.markerEvents
           .onEach { event: MarkerEvent ->
               // 이벤트 처리...
           }
           .catch { e -> 
               Timber.e(e, "마커 이벤트 구독 중 오류 발생")
               handleError(e, "마커 이벤트 처리 중 오류가 발생했습니다.")
           }
           .launchIn(viewModelScope)
   }
   */
   
   // 새 코드
   private fun subscribeToMarkerEvents() {
       markerManager.subscribeToEvents(viewModelScope) { event ->
           try {
               Timber.d("마커 이벤트 수신: $event")
               when (event) {
                   is MarkerEvent.MarkerCreationSuccess -> {
                       // 이벤트 처리...
                   }
                   // 나머지 이벤트 처리...
               }
           } catch (e: Exception) {
               Timber.e(e, "마커 이벤트 처리 중 오류 발생")
               handleError(e, "마커 이벤트 처리 중 오류가 발생했습니다.")
           }
       }
   }
   ```

### 3.3 단계 3: 이중 발행 패턴 최적화 (완료)

앱이 안정적으로 작동하는 것을 확인한 후 이중 발행 패턴을 최적화합니다.

1. **BaseManager 클래스의 버퍼링 메커니즘 검토 및 개선** - ✅ 완료
   ```kotlin
   // 이벤트 버퍼 크기 제한 추가
   private val MAX_BUFFER_SIZE = 100 // 최대 버퍼 크기 제한
   private val eventBuffer = ConcurrentLinkedQueue<E>()
   
   protected suspend fun bufferOrEmitEvent(event: E) {
       try {
           if (_hasSubscribers.value) {
               Timber.d("${javaClass.simpleName} 실시간 이벤트 발행: $event")
               _events.emit(event)
           } else {
               eventBufferLock.withLock {
                   // 버퍼 크기 제한 검사
                   if (eventBuffer.size >= MAX_BUFFER_SIZE) {
                       Timber.w("${javaClass.simpleName} 이벤트 버퍼 최대 크기 도달, 가장 오래된 이벤트 제거")
                       eventBuffer.poll()
                   }
                   
                   Timber.d("${javaClass.simpleName} 이벤트 버퍼링 중: $event (구독자 없음)")
                   eventBuffer.add(event)
               }
           }
       } catch (e: Exception) {
           Timber.e(e, "${javaClass.simpleName} 이벤트 발행 실패: $event")
       }
   }
   ```

2. **MarkerManagerImpl 클래스 이중 발행 패턴 최적화** - ✅ 완료
   ```kotlin
   // 기존 코드
   /*
   coroutineScope.launch {
       bufferOrEmitEvent(MarkerEvent.MarkerSelected(markerId))
       // 같은 이벤트를 markerEvents로도 발행
       _markerEvents.emit(MarkerEvent.MarkerSelected(markerId))
   }
   */
   
   // 새 코드
   coroutineScope.launch {
       bufferOrEmitEvent(MarkerEvent.MarkerSelected(markerId))
       // 이중 발행 제거
       // _markerEvents.emit(MarkerEvent.MarkerSelected(markerId))
   }
   
   // 호환성을 위해 events를 markerEvents로 자동 전달하는 코드 추가
   init {
       // events를 markerEvents로 전달하여 호환성 유지
       coroutineScope.launch {
           events.collect { event ->
               try {
                   _markerEvents.emit(event)
                   Timber.d("BaseManager events를 markerEvents로 전달: $event")
               } catch (e: Exception) {
                   Timber.e(e, "이벤트 전달 중 오류 발생: $event")
               }
           }
       }
       
       // 기존 코드...
   }
   ```

#### 3.3.4 단계 4: 통합 테스트 및 버그 수정 (예상 소요: 1일)

모든 변경이 완료된 후 앱 전체를 테스트하고 발견된 버그를 수정합니다.

1. **앱 삭제 후 재설치 테스트** - 진행 중
   - 앱을 완전히 삭제하고 새로 설치
   - 마커 생성 및 삭제 테스트
   - UI 업데이트가 즉시 반영되는지 확인

2. **기능별 테스트**
   - 쓰기모드 타이머 및 전환 테스트
   - 메모 생성, 로드, 수정 테스트
   - 마커 생성, 삭제, 선택 테스트
   - 위치 추적 기능 테스트

3. **예외 상황 테스트**
   - 네트워크 연결 해제 시 동작 테스트
   - 권한 거부 시 동작 테스트
   - 백그라운드-포그라운드 전환 시 동작 테스트

4. **버그 수정**
   - 발견된 버그 목록 작성
   - 우선순위에 따라 버그 수정 진행
   - 수정 후 재테스트

## 8. 결론 및 추가 개선 사항 (2025-03-21)

### 8.1 완료된 작업 정리

1. **생명주기 관리 코드 개선**
   - 초기화 순서 명시화
   - 중복 초기화 방지 로직 추가
   - 각 매니저 클래스에 통일된 초기화 패턴 적용

2. **이벤트 버퍼링 메커니즘 구현**
   - 이벤트 버퍼 추가 (ConcurrentLinkedQueue 사용)
   - 구독자 상태 추적 기능 구현
   - 버퍼 크기 제한 추가 (MAX_BUFFER_SIZE = 100)
   - 각 매니저 클래스의 이벤트 발행 방식 표준화

3. **구독 방식 개선**
   - 각 Manager 인터페이스에 subscribeToEvents 메서드 추가
   - BaseManager 클래스의 subscribeToEvents 메서드를 open으로 변경
   - MapViewModel의 이벤트 구독 코드를 subscribeToEvents 사용 방식으로 통일

4. **UI 관련 개선**
   - 스낵바 위치 조정
   - 모드변경 버튼 재추가
   - 스낵바 스타일 및 콜백 복원

### 8.2 남은 개선 사항

1. **코드 품질 개선**
   - MapViewModel 추가 정리
   - 불필요한 코드 제거
   - 주석 보강 및 문서화 개선

2. **테스트 추가**
   - 단위 테스트 구현
   - 통합 테스트 구현
   - 성능 테스트 구현

3. **추가 리팩토링 기회**
   - MapInitializer 클래스 추출
   - MapResourceCleaner 클래스 추출
   - 동시성 관리 개선

### 8.3 효과 및 이점

1. **버그 해결**
   - 앱 최초 설치 시 마커 삭제 문제 해결
   - 스낵바 위치 및 모드변경 버튼 문제 해결
   - 기타 UI 관련 버그 해결

2. **코드 품질 향상**
   - 단일 책임 원칙 준수
   - 코드 가독성 및 유지보수성 향상
   - 테스트 용이성 증가
   - 의존성 주입 단순화
   - 불필요한 로깅 제거로 중요 로그 식별 용이

## 9. MapViewModel 코드 정리 계획 (2025-03-22)

MapViewModel 코드를 검토한 결과, 다음과 같은 코드 정리 작업이 필요합니다. 리팩토링의 안정성을 위해 작업을 단계별로 진행합니다.

### 9.1 단계별 코드 정리 체크리스트

#### 단계 1: 불필요한 상수 및 변수 제거 (예상 소요: 0.5일)
- [x] `MARKER_CREATION_INTERVAL`: 이미 다른 곳에서 관리되는 상수 제거 (코드에 존재하지 않음)
- [x] `MEMO_DIALOG_INTERVAL`: 사용되지 않는 상수 제거 (코드에 존재하지 않음)
- [x] 사용되지 않는 임시 변수 및 카운터 변수 제거
- [x] 빌드 및 테스트 (마커 생성/삭제, 메모 작성/읽기, 모드 전환)

#### 단계 2: 중복 메서드 제거 및 통합 (예상 소요: 0.5일)
- [x] `loadMemos(markerId: String)` vs `loadMemosByMarkerId(markerId: String)` 통합
- [ ] `addMemo(markerId: String, content: String)` vs `createMemo(markerId: String, content: String)` 통합
- [ ] 메모장 표시 관련 중복 코드 정리
- [ ] 빌드 및 테스트 (마커 생성/삭제, 메모 작성/읽기, 모드 전환)

### 9.2 단계 2: 중복 메서드 제거 및 통합 상세 계획 (2025-03-22)

MapViewModel 리팩토링의 두 번째 단계로, 중복된 메서드를 통합하고 불필요한 코드를 제거하는 작업을 수행합니다. 이 작업을 통해 코드의 일관성을 높이고 유지보수성을 개선합니다.

#### 9.2.1 준비 작업

1. **작업 브랜치 생성**
   ```bash
   git checkout -b refactoring/mapviewmodel-step2
   ```

2. **백업 확인**
   - 기존 백업 브랜치가 있는지 확인
   - 필요시 추가 백업 생성

3. **테스트 시나리오 정의**
   - 메모 로드 테스트
   - 메모 생성 테스트
   - 마커 클릭 시 메모장 표시 테스트

#### 9.2.2 메모 로드 관련 중복 메서드 통합 (예상 소요: 0.5일) - 완료 (2025-03-23)

1. **코드 분석** - ✅ 완료
   - `loadMemos(markerId: String)`와 `loadMemosByMarkerId(markerId: String)` 메서드의 구현 비교
   - 호출 패턴 분석: 어디서 어떻게 사용되는지 파악

2. **통합 구현** - ✅ 완료
   ```kotlin
   // 변경 전 예시:
   private fun loadMemos(markerId: String) {
       // 구현...
   }
   
   private fun loadMemosByMarkerId(markerId: String) {
       // 유사한 구현...
   }
   
   // 변경 후 예시:
   fun loadMemos(markerId: String) {
       viewModelScope.launch {
           try {
               Timber.d("메모 로딩 시작: markerId=$markerId")
               memoManager.loadMemosByMarkerId(markerId)
               
               // 메모 로딩 시 현재 선택된 마커 ID 업데이트 로깅
               Timber.d("메모 로딩 후 현재 마커 ID: $markerId")
           } catch (e: Exception) {
               Timber.e(e, "메모 로딩 중 오류 발생")
               handleError(e, "메모 로딩 중 오류가 발생했습니다.")
           }
       }
   }
   ```

3. **호출 부분 업데이트** - ✅ 완료
   - 모든 `loadMemosByMarkerId` 호출을 `loadMemos`로 변경
   - `MapEventHandlerImpl` 클래스의 `loadMemosByMarkerId` 메서드는 유지하되 주석으로 이 메서드가 `MapViewModel.loadMemos`와 동일한 기능임을 명시

4. **테스트** - ✅ 완료
   - 앱 빌드 후 실행
   - 마커 클릭 시 메모 로드가 정상적으로 작동하는지 확인
   - 메모 업데이트 후 목록 갱신이 정상적으로 작동하는지 확인
   
   테스트 결과: 모든 기능 정상 작동 확인

#### 9.2.3 메모 생성 관련 중복 메서드 통합 (예상 소요: 0.5일)

1. **코드 분석**
   - `addMemo(markerId: String, content: String)`와 `createMemo(markerId: String, content: String)` 메서드 비교
   - 호출 패턴 및 사용 컨텍스트 분석

2. **통합 구현**
   ```kotlin
   // 변경 전 예시:
   fun addMemo(markerId: String, content: String) {
       // 구현...
   }
   
   fun createMemo(markerId: String, content: String) {
       // 유사한 구현...
   }
   
   // 변경 후 예시 (mapEventHandler 사용):
   fun createMemo(markerId: String, content: String) {
       // 로깅 추가
       Timber.d("메모 생성 시작: 마커 ID $markerId")
       
       // mapEventHandler로 위임
       mapEventHandler.handleCreateMemo(markerId, content)
   }
   ```

3. **호출 부분 업데이트**
   - 모든 `addMemo` 호출을 `createMemo`로 변경
   - 영향받는 메서드들 확인: `onMemoSubmit`, `onPositiveButtonClick` 등

4. **테스트**
   - 앱 빌드 후 실행
   - 메모 생성이 정상적으로 작동하는지 확인
   - 생성된 메모가 목록에 표시되는지 확인

#### 9.2.4 메모장 표시 관련 중복 코드 정리 (예상 소요: 1일)

1. **코드 분석**
   - `showMemoDialogForMarker(markerId: String)`, `onMarkerClick(markerId: String)` 등에서 메모장 표시 관련 중복 코드 분석
   - 공통 패턴 식별

2. **공통 로직 추출**
   ```kotlin
   // 공통 로직을 별도 메서드로 추출
   private fun prepareMemoDialogForMarker(markerId: String) {
       // 마커 선택 및 메모 로드 준비 로직
       viewModelScope.launch {
           try {
               // 마커 선택 로직
               markerManager.selectMarker(markerId)
               
               // 상태 업데이트
               _uiState.update { currentState ->
                   currentState.copy(
                       currentMarkerId = markerId,
                       isMemoDialogVisible = true
                   )
               }
               
               // 메모 로드
               loadMemos(markerId)
               
               Timber.d("마커 $markerId 선택 및 메모 대화상자 준비 완료")
           } catch (e: Exception) {
               Timber.e(e, "메모 대화상자 준비 실패: $markerId")
               handleError(e, "메모 대화상자를 준비하는 중 오류가 발생했습니다.")
           }
       }
   }
   ```

3. **기존 메서드 수정**
   ```kotlin
   // 변경 전:
   fun showMemoDialogForMarker(markerId: String) {
       // 기존 구현...
   }
   
   fun onMarkerClick(markerId: String) {
       // 비슷한 구현을 포함...
   }
   
   // 변경 후:
   fun showMemoDialogForMarker(markerId: String) {
       Timber.d("메모 대화상자 표시 요청: 마커 ID $markerId")
       prepareMemoDialogForMarker(markerId)
       // 추가적인 UI 관련 로직 (필요시)
   }
   
   fun onMarkerClick(markerId: String) {
       Timber.d("마커 클릭 이벤트: 마커 ID $markerId")
       
       // 편집 모드 타이머 재시작 (필요시)
       restartEditModeTimerIfNeeded()
       
       // 공통 로직 사용
       prepareMemoDialogForMarker(markerId)
       
       // 마커 클릭 전용 추가 로직 (필요시)
   }
   ```

4. **테스트**
   - 앱 빌드 후 실행
   - 마커 클릭 시 메모장이 정상적으로 표시되는지 확인
   - 다른 경로(지도 UI 등)를 통한 메모장 표시도 정상적으로 작동하는지 확인

#### 9.2.5 통합 테스트 및 버그 수정 (예상 소요: 0.5일)

1. **마커 생성-메모 저장 시나리오 테스트**
   - 쓰기 모드 진입
   - 마커 생성
   - 메모 작성 및 저장
   - 마커 및 메모가 정상적으로 표시되는지 확인

2. **마커 선택-메모 로드 시나리오 테스트**
   - 기존 마커 선택
   - 메모 목록이 정상적으로 로드되는지 확인
   - 메모 내용이 정확하게 표시되는지 확인

3. **예외 상황 테스트**
   - 네트워크 연결 해제 시 동작 테스트
   - 권한 거부 시 동작 테스트
   - 백그라운드-포그라운드 전환 시 동작 테스트

4. **버그 수정**
   - 발견된 버그 목록 작성
   - 우선순위에 따라 버그 수정 진행
   - 수정 후 재테스트

#### 9.2.6 롤백 계획

1. **단계별 롤백**
   ```bash
   # 롤백이 필요한 경우
   git stash
   git checkout main  # 또는 이전 작업 브랜치
   ```

2. **부분 롤백**
   - 문제가 발생한 메서드만 원래 구현으로 복원
   - 영향받는 호출 코드도 함께 복원

#### 9.2.7 예상 일정 및 진행 상황 추적

| 작업 | 예상 소요 시간 | 시작일 | 완료일 | 상태 |
|-----|--------------|-------|-------|------|
| 메모 로드 관련 중복 메서드 통합 | 0.5일 | | | 미시작 |
| 메모 생성 관련 중복 메서드 통합 | 0.5일 | | | 미시작 |
| 메모장 표시 관련 중복 코드 정리 | 1일 | | | 미시작 |
| 통합 테스트 및 버그 수정 | 0.5일 | | | 미시작 |

**총 예상 소요 기간**: 2.5일

### 9.3 예상 결과

- MapViewModel 코드 크기 약 30% 감소 예상 (780줄 → 약 550줄)
- 코드 가독성 및 유지보수성 향상
- 책임 분리 명확화
- 테스트 용이성 증가
- 의존성 주입 단순화
- 불필요한 로깅 제거로 중요 로그 식별 용이

### 9.4 진행 상황 추적

| 단계 | 시작일 | 완료일 | 상태 | 커밋 해시 |
|------|-------|-------|------|-----------|
| 단계 1: 불필요한 상수 및 변수 제거 | 2025-03-22 | 2025-03-22 | 완료 | - |
| 단계 2: 중복 메서드 제거 및 통합 | | | 미시작 | |
| 단계 3: 이미 위임된 메서드 최적화 | | | 미시작 | |
| 단계 4: 이벤트 핸들링 코드 정리 | | | 미시작 | |
| 단계 5: 로깅 코드 정리 및 주석 개선 | | | 미시작 | |
| 단계 6: 불필요한 의존성 제거 | | | 미시작 | |
| 단계 7: 복잡한 메서드 최적화 | | | 미시작 | |
