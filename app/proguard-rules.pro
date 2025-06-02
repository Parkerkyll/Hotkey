# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable,StackMapTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Hilt Worker
-keepclassmembers,allowobfuscation class * extends androidx.work.CoroutineWorker {
    @dagger.assisted.AssistedInject <init>(...);
}

# Keep class names of Hilt injected Workers
-keepnames @dagger.hilt.android.HiltAndroidApp class * extends android.app.Application
-keepnames @dagger.hilt.android.qualifiers.HiltWorker class * extends androidx.work.CoroutineWorker

# Naver Map SDK 최적화 규칙 (용량 절감을 위해 업데이트)
# 기본 네이버 맵 기능만 보존하고 불필요한 기능 제거
-keep class com.naver.maps.map.NaverMapOptions { *; }
-keep class com.naver.maps.map.NaverMapSdk { *; }
-keep class com.naver.maps.map.NaverMap { *; }
-keep class com.naver.maps.map.MapView { *; }
-keep class com.naver.maps.map.OnMapReadyCallback { *; }
-keep class com.naver.maps.map.CameraUpdate { *; }
-keep class com.naver.maps.map.CameraPosition { *; }
-keep class com.naver.maps.map.LocationTrackingMode { *; }
-keep class com.naver.maps.map.UiSettings { *; }

# 마커 및 오버레이 관련 필수 클래스 보존
-keep class com.naver.maps.map.overlay.Marker { *; }
-keep class com.naver.maps.map.overlay.Overlay { *; }
-keep class com.naver.maps.map.overlay.CircleOverlay { *; }
-keep class com.naver.maps.map.overlay.InfoWindow { *; }
-keep class com.naver.maps.map.overlay.OverlayImage { *; }

# 위치 관련 필수 클래스 보존
-keep class com.naver.maps.map.util.FusedLocationSource { *; }
-keep class com.naver.maps.geometry.LatLng { *; }

# 사용하지 않는 네이버 맵 기능 제거 (용량 절감)
-assumenosideeffects class com.naver.maps.map.indoor.** { *; }
-assumenosideeffects class com.naver.maps.map.overlay.three.** { *; }
-assumenosideeffects class com.naver.maps.map.overlay.path.PathOverlay { *; }
-assumenosideeffects class com.naver.maps.map.overlay.path.MultipartPathOverlay { *; }

# EventBus 관련 규칙
-keepattributes *Annotation*
-keepclassmembers class org.greenrobot.eventbus.** { *; }
-keep enum org.greenrobot.eventbus.ThreadMode { *; }

# 이벤트버스 구독자 메서드 보존
-keepclassmembers class * {
    @org.greenrobot.eventbus.Subscribe <methods>;
}

# Google API Client 관련 클래스 보존
-dontwarn com.google.api.client.**
-keep class com.google.api.client.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn org.joda.time.**
-keep class org.joda.time.** { *; }

# 스택 맵 테이블 관련 처리를 위한 규칙
-keepattributes StackMapTable

# R8 관련 추가 규칙
-dontobfuscate
-dontoptimize
-dontshrink

# 기본 안드로이드 관련 클래스 보존
-keep class androidx.databinding.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class * implements androidx.lifecycle.LifecycleObserver { *; }