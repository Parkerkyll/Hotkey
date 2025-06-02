package com.parker.hotkey.di

import android.content.Context
import com.parker.hotkey.data.cache.DiskCacheAdapter
import com.parker.hotkey.data.cache.MarkerCacheAdapter
import com.parker.hotkey.data.cache.MarkerCacheAdapterFactory
import com.parker.hotkey.data.cache.MarkerCacheWrapper
import com.parker.hotkey.data.cache.MarkerDiskCache
import com.parker.hotkey.data.cache.MarkerDiskCacheImpl
import com.parker.hotkey.data.cache.MemoryCacheAdapter
import com.parker.hotkey.data.repository.MarkerRepositoryCacheAdapter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Provider
import javax.inject.Singleton
import javax.inject.Qualifier
import dagger.Lazy
import javax.inject.Named

/**
 * 캐시 관련 의존성 주입을 위한 Dagger 모듈
 */
@Module
@InstallIn(SingletonComponent::class)
object CacheModule {
    
    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = false
        }
    }
    
    @Provides
    @Singleton
    fun provideMarkerDiskCache(
        @ApplicationContext context: Context,
        json: Json
    ): MarkerDiskCache {
        return MarkerDiskCacheImpl(context, keyManager = com.parker.hotkey.domain.manager.MarkerCacheKeyManager(), json)
    }
    
    @Provides
    @Singleton
    fun provideMarkerCacheWrapper(
        memoryCache: com.parker.hotkey.domain.manager.MarkerMemoryCache
    ): MarkerCacheWrapper {
        return MarkerCacheWrapper(memoryCache)
    }
    
    @Provides
    @Singleton
    fun provideMemoryCacheAdapter(
        cacheWrapper: MarkerCacheWrapper
    ): MemoryCacheAdapter {
        return MemoryCacheAdapter(cacheWrapper)
    }
    
    @Provides
    @Singleton
    fun provideDiskCacheAdapter(
        diskCache: MarkerDiskCache
    ): DiskCacheAdapter {
        return DiskCacheAdapter(diskCache)
    }
    
    @Provides
    @Singleton
    fun provideMarkerCacheAdapterFactory(
        memoryCacheAdapter: MemoryCacheAdapter,
        diskCacheAdapter: DiskCacheAdapter
    ): MarkerCacheAdapterFactory {
        return MarkerCacheAdapterFactory(memoryCacheAdapter, diskCacheAdapter)
    }
    
    @Provides
    @Singleton
    @DefaultCacheAdapter
    fun provideDefaultMarkerCacheAdapter(
        factory: MarkerCacheAdapterFactory
    ): MarkerCacheAdapter {
        return factory.createAdapter(MarkerCacheAdapterFactory.CacheStrategy.MULTI_LEVEL)
    }
    
    @Provides
    @Singleton
    fun provideMarkerRepositoryCacheAdapter(
        @DefaultCacheAdapter cacheAdapter: MarkerCacheAdapter
    ): MarkerRepositoryCacheAdapter {
        return MarkerRepositoryCacheAdapter(cacheAdapter)
    }
    
    /**
     * 레포지토리에서 사용할 캐시 어댑터 
     * 옵셔널 의존성을 위해 Nullable로 제공
     */
    @Provides
    @Singleton
    @Named("optionalCacheAdapter")
    fun provideOptionalMarkerRepositoryCacheAdapter(
        adapter: MarkerRepositoryCacheAdapter
    ): MarkerRepositoryCacheAdapter? {
        return adapter
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultCacheAdapter 