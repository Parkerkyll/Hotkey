package com.parker.hotkey.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MapLocationSource

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FragmentCoroutineScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultNaverMap 