# 개발 환경 설정

## 필수 도구
1. Android Studio
   - 버전: Hedgehog | 2023.1.1
   - JDK: 17
   - Gradle: 8.0
   - Kotlin: 1.9.0

2. SDK 설정
   - minSdk: 29 (Android 10)
   - targetSdk: 34 (Android 14)
   - compileSdk: 34
   - buildTools: 34.0.0

3. 필수 플러그인
   - Kotlin
   - Android
   - Navigation SafeArgs
   - Hilt

## 환경 변수 설정
```properties
# local.properties
sdk.dir=/Users/username/Library/Android/sdk
naver.map.client_id=5ch4gspzih
kakao.native_app_key=85a066568647a5a34d53dc178a1c2a66
kakao.rest_api_key=9c2b048325cde65b82ec5bc73779005a
```

## 빌드 설정

### 1. Gradle 설정
```kotlin
// build.gradle.kts (프로젝트)
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.1.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.0")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.48")
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.7.5")
    }
}
```

### 2. 앱 모듈 설정
```kotlin
// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("dagger.hilt.android.plugin")
    id("androidx.navigation.safeargs.kotlin")
}

android {
    namespace = "com.parker.hotkey"
    compileSdk = 34
    
    defaultConfig {
        applicationId = "com.parker.hotkey"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
```

## 주요 라이브러리

### 1. UI/탐색
```kotlin
dependencies {
    // AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.5")
}
```

### 2. 데이터베이스
```kotlin
dependencies {
    // Room
    implementation("androidx.room:room-runtime:2.6.0")
    implementation("androidx.room:room-ktx:2.6.0")
    kapt("androidx.room:room-compiler:2.6.0")
    
    // SQLCipher
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
}
```

### 3. 네트워크
```kotlin
dependencies {
    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    
    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
}
```

### 4. 의존성 주입
```kotlin
dependencies {
    // Hilt
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-android-compiler:2.48")
}
```

### 5. 지도/이미지
```kotlin
dependencies {
    // Naver Maps
    implementation("com.naver.maps:map-sdk:3.17.0")
    
    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")
}
```

## 초기 설정 체크리스트

### 1. Android Studio 설정
- [ ] JDK 17 설치 및 설정
- [ ] Gradle JDK 설정
- [ ] Android SDK 설치
- [ ] 필수 플러그인 설치

### 2. 프로젝트 설정
- [ ] Git 저장소 초기화
- [ ] .gitignore 설정
- [ ] local.properties 설정
- [ ] Gradle 동기화

### 3. SDK 설정
- [ ] Naver Maps SDK 키 등록
- [ ] Kakao SDK 키 등록
- [ ] Firebase 설정
- [ ] ProGuard 규칙 설정 