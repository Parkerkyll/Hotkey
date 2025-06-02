# Hotkey 앱

## 📱 앱 소개
안드로이드용 Hotkey 애플리케이션입니다.

## 🔽 다운로드

### 최신 버전: v1.0.0.639

**[📱 APK 다운로드](https://github.com/Parkerkyll/-Hotkey-Release/releases/latest)**

## 📋 시스템 요구사항
- Android 10.0 (API 29) 이상
- ARM 프로세서 (arm64-v8a, armeabi-v7a)

## 🛠 설치 방법

### 일반 설치
1. 위 링크에서 최신 APK 파일을 다운로드합니다
2. 안드로이드 기기의 **설정 > 보안 > 알 수 없는 소스** 를 허용합니다
3. 다운로드한 APK 파일을 실행하여 설치합니다

### 개발자를 위한 빌드 방법
```bash
# 프로젝트 클론
git clone https://github.com/Parkerkyll/-Hotkey-Release.git
cd -Hotkey-Release

# local.properties 파일 생성 및 필요한 API 키 설정
# (KAKAO_NATIVE_APP_KEY, NAVER_CLIENT_ID 등)

# 릴리스 빌드
./gradlew assembleRelease
```

## 🔧 기능
- 안드로이드 기기의 핫키 관리
- 사용자 정의 단축키 설정
- 빠른 앱 실행 및 기능 접근

## 📝 버전 히스토리
### v1.0.0.639 (2025-06-02)
- 초기 릴리스 버전
- 안정성 개선 및 성능 최적화
- 핵심 기능 구현 완료

## ⚠️ 보안 정보
- 이 저장소는 APK 배포 전용입니다
- 소스코드는 별도로 관리됩니다
- 모든 민감한 정보(API 키, 키스토어 등)는 제외되어 있습니다

## 📞 지원
문제가 발생하면 [Issues](https://github.com/Parkerkyll/-Hotkey-Release/issues)에 등록해주세요. 