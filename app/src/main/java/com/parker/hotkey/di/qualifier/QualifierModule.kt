package com.parker.hotkey.di.qualifier

import javax.inject.Qualifier
import javax.inject.Scope

/**
 * 앱 레벨의 코루틴 스코프를 식별하는 한정자
 * 애플리케이션 수명 주기 동안 유지되는 코루틴 스코프에 사용됩니다.
 */
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class ApplicationScope

/**
 * Fragment 범위의 코루틴 스코프를 식별하는 한정자
 * Fragment 수명 주기를 따르는 코루틴 스코프에 사용됩니다.
 */
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class FragmentCoroutineScope

/**
 * 지도 위치 소스 인스턴스를 식별하는 한정자
 * 네이버 지도에서 사용되는 위치 소스에 사용됩니다.
 */
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class MapLocationSource

/**
 * 기본 네이버 맵 인스턴스를 식별하는 한정자
 * 네이버 지도의 기본 인스턴스에 사용됩니다.
 */
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class DefaultNaverMap

/**
 * 임시 마커 기능 사용 여부를 식별하는 한정자
 * 임시 마커 기능을 사용할지 여부를 나타내는 플래그에 사용됩니다.
 */
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class UseTemporaryMarkerFeature

/**
 * IO 코루틴 스코프를 식별하는 한정자
 * IO 작업을 위한 코루틴 스코프에 사용됩니다.
 */
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class IoDispatcher

/**
 * 기본 코루틴 스코프를 식별하는 한정자
 * 일반 계산 작업을 위한 코루틴 스코프에 사용됩니다.
 */
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class DefaultDispatcher

/**
 * 메인 스레드 코루틴 스코프를 식별하는 한정자
 * UI 작업을 위한 코루틴 스코프에 사용됩니다.
 */
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class MainDispatcher 