package com.parker.hotkey.di

import android.content.Context
import com.parker.hotkey.data.local.HotKeyDatabase
import com.parker.hotkey.data.local.dao.GeohashLastSyncDao
import com.parker.hotkey.data.local.dao.MarkerDao
import com.parker.hotkey.data.local.dao.MemoDao
import com.parker.hotkey.data.local.dao.VisitedGeohashDao
import com.parker.hotkey.data.manager.LocationManagerImpl
import com.parker.hotkey.data.manager.TokenManagerImpl
import com.parker.hotkey.data.mapper.MarkerEntityMapper
import com.parker.hotkey.data.mapper.MemoEntityMapper
import com.parker.hotkey.data.repository.AuthRepositoryImpl
import com.parker.hotkey.data.repository.FirebaseNoticeRepository
import com.parker.hotkey.data.repository.MarkerRepositoryImpl
import com.parker.hotkey.data.repository.MemoRepositoryImpl
import com.parker.hotkey.data.repository.VisitedGeohashRepositoryImpl
import com.parker.hotkey.domain.manager.GeohashManager
import com.parker.hotkey.domain.manager.GeohashManagerImpl
import com.parker.hotkey.domain.repository.AuthRepository
import com.parker.hotkey.domain.repository.LocationManager
import com.parker.hotkey.domain.repository.MarkerRepository
import com.parker.hotkey.domain.repository.MemoRepository
import com.parker.hotkey.domain.repository.NoticeRepository
import com.parker.hotkey.domain.repository.SyncRepository
import com.parker.hotkey.domain.repository.VisitedGeohashRepository
import com.parker.hotkey.domain.manager.TokenManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import dagger.Lazy

/**
 * 데이터 계층 관련 의존성을 제공하는 통합 모듈
 * - 데이터베이스 관련(DatabaseModule)
 * - 레포지토리 관련(RepositoryModule)
 * - 네트워크 관련(NetworkModule)
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    
    // ---- DatabaseModule 기능 ----
    
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HotKeyDatabase {
        return HotKeyDatabase.getInstance(context)
    }
    
    @Provides
    @Singleton
    fun provideMarkerDao(database: HotKeyDatabase): MarkerDao {
        return database.markerDao()
    }
    
    @Provides
    @Singleton
    fun provideMemoDao(database: HotKeyDatabase): MemoDao {
        return database.memoDao()
    }
    
    @Provides
    @Singleton
    fun provideGeohashLastSyncDao(database: HotKeyDatabase): GeohashLastSyncDao {
        return database.geohashLastSyncDao()
    }
    
    @Provides
    @Singleton
    fun provideVisitedGeohashDao(database: HotKeyDatabase): VisitedGeohashDao {
        return database.visitedGeohashDao()
    }
    
    // ---- Mapper 제공 ----
    
    @Provides
    @Singleton
    fun provideMarkerEntityMapper(): MarkerEntityMapper {
        return MarkerEntityMapper()
    }
    
    @Provides
    @Singleton
    fun provideMemoEntityMapper(): MemoEntityMapper {
        return MemoEntityMapper()
    }
    
    // ---- RepositoryModule 기능 ----
    
    @Provides
    @Singleton
    fun provideMarkerRepository(
        markerDao: MarkerDao,
        markerMapper: MarkerEntityMapper,
        @Named("optionalCacheAdapter") cacheAdapter: com.parker.hotkey.data.repository.MarkerRepositoryCacheAdapter?,
        syncRepository: SyncRepository,
        markerStateAdapter: Lazy<com.parker.hotkey.domain.manager.MarkerStateAdapter>
    ): MarkerRepository {
        return MarkerRepositoryImpl(markerDao, markerMapper, cacheAdapter, syncRepository, markerStateAdapter)
    }
    
    @Provides
    @Singleton
    fun provideMemoRepository(
        memoDao: MemoDao,
        markerDao: MarkerDao,
        syncRepository: SyncRepository,
        memoMapper: MemoEntityMapper
    ): MemoRepository {
        return MemoRepositoryImpl(memoDao, markerDao, syncRepository, memoMapper)
    }
    
    @Provides
    @Singleton
    fun provideAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository {
        return authRepositoryImpl
    }
    
    @Provides
    @Singleton
    fun provideNoticeRepository(
        firebaseNoticeRepository: FirebaseNoticeRepository
    ): NoticeRepository {
        return firebaseNoticeRepository
    }
    
    @Provides
    @Singleton
    fun provideVisitedGeohashRepository(
        visitedGeohashRepositoryImpl: VisitedGeohashRepositoryImpl
    ): VisitedGeohashRepository {
        return visitedGeohashRepositoryImpl
    }
}

/**
 * 데이터 계층의 Manager 관련 바인딩을 제공하는 모듈
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {
    @Binds
    @Singleton
    abstract fun bindLocationManager(
        locationManagerImpl: LocationManagerImpl
    ): LocationManager

    @Binds
    @Singleton
    abstract fun bindTokenManager(
        tokenManagerImpl: TokenManagerImpl
    ): TokenManager
    
    @Binds
    @Singleton
    abstract fun bindGeohashManager(
        geohashManagerImpl: GeohashManagerImpl
    ): GeohashManager
} 