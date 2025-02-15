plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    kotlin("kapt")
    id("androidx.navigation.safeargs.kotlin")
}

android {
    namespace = "com.parker.hotkey"
    compileSdk = 34 // 컴파일 대상 Android SDK 버전 설정

    buildFeatures {
        buildConfig = true  // BuildConfig 파일 생성을 활성화하여 앱 빌드 정보를 포함
    }

    defaultConfig {
        applicationId = "com.parker.hotkey"
        minSdk = 29   // 최소 지원 Android SDK 버전 (Android 10)
        targetSdk = 34 // 앱이 테스트된 대상 SDK 버전 (Android 14)
        versionCode = 1 // 앱 버전 코드 (업데이트마다 증가)
        versionName = "1.0" // 앱 버전 이름 (사용자에게 표시)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // local.properties에서 API 키 가져오기
        val properties = com.android.build.gradle.internal.cxx.configure.gradleLocalProperties(rootDir)
        
        // Kakao API Key
        buildConfigField(
            "String",
            "KAKAO_NATIVE_APP_KEY",
            "\"${properties.getProperty("KAKAO_NATIVE_APP_KEY")}\""
        )
        
        // Naver API Keys
        buildConfigField(
            "String",
            "NAVER_CLIENT_ID",
            "\"${properties.getProperty("NAVER_CLIENT_ID")}\""
        )
        buildConfigField(
            "String",
            "NAVER_CLIENT_SECRET",
            "\"${properties.getProperty("NAVER_CLIENT_SECRET")}\""
        )

        // 매니페스트 플레이스홀더 설정
        manifestPlaceholders["KAKAO_NATIVE_APP_KEY"] = "kakao${properties.getProperty("KAKAO_NATIVE_APP_KEY")}"
        manifestPlaceholders["NAVER_CLIENT_ID"] = properties.getProperty("NAVER_CLIENT_ID")

        // Room 스키마 내보내기 설정
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf(
                    "room.schemaLocation" to "$projectDir/schemas",
                    "room.incremental" to "true"
                )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // 디버그 모드 유지
            isDebuggable = true
            // 디버그 빌드는 ProGuard 비활성화
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    kapt {
        correctErrorTypes = true
        useBuildCache = true
    }
}

dependencies {
    // androidx 라이브러리 버전 다운그레이드
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity:1.8.2")
    implementation("androidx.core:core:1.12.0")
    
    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.hilt:hilt-work:1.1.0")
    kapt("androidx.hilt:hilt-compiler:1.1.0")
    
    // EventBus
    implementation("org.greenrobot:eventbus:3.3.1")
    
    // 나머지 의존성은 그대로 유지
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // 카카오 SDK
    implementation("com.kakao.sdk:v2-user:2.20.6")
    implementation("com.kakao.sdk:v2-common:2.20.6")
    
    // Google Play Services Location
    implementation("com.google.android.gms:play-services-location:21.3.0")
    
    // 기타 필요한 의존성
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.jakewharton.timber:timber:5.0.1")

    // 네이버 지도
    implementation("com.naver.maps:map-sdk:3.18.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-android-compiler:2.48")
    
    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.5")
    implementation("androidx.navigation:navigation-dynamic-features-fragment:2.7.5")
    
    // 테스트 의존성
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("io.mockk:mockk:1.13.8")
    
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.navigation:navigation-testing:2.7.5")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}