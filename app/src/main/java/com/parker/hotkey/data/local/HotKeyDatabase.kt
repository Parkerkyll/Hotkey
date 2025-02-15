package com.parker.hotkey.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.parker.hotkey.data.local.dao.MarkerDao
import com.parker.hotkey.data.local.dao.MemoDao
import com.parker.hotkey.data.local.entity.MarkerEntity
import com.parker.hotkey.data.local.entity.MemoEntity
import timber.log.Timber

@Database(
    entities = [MarkerEntity::class, MemoEntity::class],
    version = 1,
    exportSchema = true
)
abstract class HotKeyDatabase : RoomDatabase() {
    abstract fun markerDao(): MarkerDao
    abstract fun memoDao(): MemoDao

    companion object {
        private const val DATABASE_NAME = "hotkey.db"

        @Volatile
        private var INSTANCE: HotKeyDatabase? = null

        fun getInstance(context: Context): HotKeyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): HotKeyDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                HotKeyDatabase::class.java,
                DATABASE_NAME
            )
            .fallbackToDestructiveMigration()
            .build()
        }

        fun destroyInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }

        fun deleteDatabase(context: Context) {
            try {
                context.deleteDatabase(DATABASE_NAME)
                // WAL 파일들도 삭제
                context.getDatabasePath("$DATABASE_NAME-shm").delete()
                context.getDatabasePath("$DATABASE_NAME-wal").delete()
                destroyInstance()
                Timber.d("데이터베이스 완전히 삭제됨")
            } catch (e: Exception) {
                Timber.e(e, "데이터베이스 삭제 중 오류 발생")
            }
        }
    }
} 