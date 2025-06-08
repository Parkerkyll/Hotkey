plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22"
    id("com.google.devtools.ksp") version "1.9.22-1.0.17"
    id("androidx.navigation.safeargs.kotlin")
    id("com.google.gms.google-services")
    kotlin("kapt")
}

// Moshi의 Kapt 사용을 명시적으로 비활성화
System.setProperty("moshi.useMoshiKotlinCodeGenKsp", "true")
System.setProperty("moshi.useMoshiKotlinCodeGenKapt", "false")

android {
    namespace = "com.parker.hotkey"
    compileSdk = 34

    // 서명 설정 추가
    signingConfigs {
        create("release") {
            val properties = com.android.build.gradle.internal.cxx.configure.gradleLocalProperties(rootDir)
            storeFile = file(properties.getProperty("RELEASE_STORE_FILE"))
            storePassword = properties.getProperty("RELEASE_STORE_PASSWORD")
            keyAlias = properties.getProperty("RELEASE_KEY_ALIAS")
            keyPassword = properties.getProperty("RELEASE_KEY_PASSWORD")
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = false  // 오류가 있어도 빌드를 중단하지 않음
        checkReleaseBuilds = true
        checkDependencies = true
    }

    // 테스트 코드 빌드 제외 설정 추가
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }

        // 안드로이드 계측 테스트 설정
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        animationsDisabled = true
    }

    defaultConfig {
        applicationId = "com.parker.hotkey"
        minSdk = 29
        targetSdk = 34
        versionCode = 2 // 배포마다 1씩 증가
        versionName = "1.0.0.657" // MAJOR: 호환되지 않는 API 변경 시 MINOR: 기능 추가 시 (하위 호환성 유지)  PATCH: 버그 수정 시

        // 멀티덱스 활성화
        multiDexEnabled = true

        // ABI 필터링 설정 - ARM 기반 디바이스만 지원
        ndk {
            abiFilters.add("arm64-v8a")  // 64비트 ARM
            abiFilters.add("armeabi-v7a") // 32비트 ARM
        }

        // 테스트 인스트루먼테이션 러너 활성화
        testInstrumentationRunner = "com.parker.hotkey.runner.CustomTestRunner"

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

        // Room 스키마 내보내기 설정을 제거 (KSP에서 처리)
        javaCompileOptions {
            annotationProcessorOptions {
                arguments.clear() // 기존 arguments 초기화
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
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

    // 테스트 소스 세트 설정
    sourceSets {
        // 테스트 소스 디렉토리 포함
        getByName("test") {
            java.srcDirs("src/test/java")
            resources.srcDirs("src/test/resources")
        }
        getByName("androidTest") {
            java.srcDirs("src/androidTest/java")
            resources.srcDirs("src/androidTest/resources")
        }
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }

    kapt {
        correctErrorTypes = true
        useBuildCache = true
        
        // KSP와 Kapt 간 순환 의존성을 방지하기 위한 설정
        keepJavacAnnotationProcessors = true
        
        // 경고를 제거하기 위해 arguments 블록 비우기
        arguments {
            // 빈 블록으로 유지
        }
    }

    // 빌드에서 테스트 관련 파일 제외
    packagingOptions {
        resources {
            excludes += listOf(
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md"
            )
        }
    }
}

dependencies {
    // androidx 라이브러리 - 최신 버전으로 업데이트
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.core:core:1.12.0")
    implementation("androidx.activity:activity:1.8.2")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.preference:preference-ktx:1.2.1")
    
    // Kotlin Serialization 의존성 추가
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    
    // MultiDex 지원 추가
    implementation("androidx.multidex:multidex:2.0.1")

    // 뷰 바인딩 프로퍼티 델리게이트 - 메모리 누수 방지
    implementation("com.github.kirich1409:viewbindingpropertydelegate:1.5.6")
    
    // 이미지 확대/축소를 위한 PhotoView 라이브러리
    implementation("com.github.chrisbanes:PhotoView:2.3.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.hilt:hilt-work:1.1.0")
    kapt("androidx.hilt:hilt-compiler:1.1.0")

    // EventBus
    implementation("org.greenrobot:eventbus:3.3.1")

    // Moshi - JSON 처리 (KSP로 완전 마이그레이션)
    implementation("com.squareup.moshi:moshi:1.15.1")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")
    implementation("com.squareup.retrofit2:converter-moshi:2.9.0")

    // LeakCanary - 메모리 누수 감지 (디버그 빌드에만 포함)
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.12")

    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // 테스트 의존성 비활성화
    // testImplementation(libs.junit)
    // androidTestImplementation(libs.androidx.junit)
    // androidTestImplementation(libs.androidx.espresso.core)

    // 카카오 SDK
    implementation("com.kakao.sdk:v2-user:2.20.6")
    implementation("com.kakao.sdk:v2-common:2.20.6")

    // Google Play Services Location
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // 기타 필요한 의존성
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.jakewharton.timber:timber:5.0.1")

    // 네이버 지도
    implementation("com.naver.maps:map-sdk:3.21.0")

    // Hilt - 최신 버전으로 업데이트
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-android-compiler:2.50")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    // Room 컴파일러를 kapt에서 ksp로 변경
    ksp("androidx.room:room-compiler:2.6.1")
    // kapt("androidx.room:room-compiler:2.6.1") - 완전히 제거

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.5")
    implementation("androidx.navigation:navigation-dynamic-features-fragment:2.7.5")

    // 테스트 의존성
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.22")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.22")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test:runner:1.5.2")
    testImplementation("androidx.test:rules:1.5.0")
    testImplementation("com.google.android.gms:play-services-location:21.3.0")
    testImplementation("androidx.test:core-ktx:1.5.0")
    testImplementation("org.robolectric:robolectric:4.11.1")

    // MockK 의존성 추가
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("io.mockk:mockk-android:1.13.8")
    testImplementation("io.mockk:mockk-agent:1.13.8")

    // 아키텍처 컴포넌트 테스트 의존성
    testImplementation("androidx.arch.core:core-testing:2.2.0")

    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test:core-ktx:1.5.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    
    // 추가: 안드로이드 테스트 서비스
    androidTestImplementation("androidx.test.services:test-services:1.4.2")
    androidTestImplementation("androidx.test:orchestrator:1.4.2")
    androidTestUtil("androidx.test:orchestrator:1.4.2")

    // Material Design
    implementation("com.google.android.material:material:1.11.0")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // AWS Amplify - API Gateway용
    implementation("com.amplifyframework:aws-api:2.14.5")
    implementation("com.amplifyframework:aws-core:2.14.5")
    
    // Retrofit2 & OkHttp - API 통신용
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // Glide - 이미지 로딩 라이브러리
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.8.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
}