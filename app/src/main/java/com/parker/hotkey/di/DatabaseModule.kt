package com.parker.hotkey.di

import android.content.Context
import com.parker.hotkey.data.local.HotKeyDatabase
import com.parker.hotkey.data.local.dao.MarkerDao
import com.parker.hotkey.data.local.dao.MemoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
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
} 