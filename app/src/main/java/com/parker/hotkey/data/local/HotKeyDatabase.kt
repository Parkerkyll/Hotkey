package com.parker.hotkey.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.parker.hotkey.data.local.converter.LastSyncConverter
import com.parker.hotkey.data.local.dao.GeohashLastSyncDao
import com.parker.hotkey.data.local.dao.MarkerDao
import com.parker.hotkey.data.local.dao.MemoDao
import com.parker.hotkey.data.local.dao.VisitedGeohashDao
import com.parker.hotkey.data.local.entity.GeohashLastSyncEntity
import com.parker.hotkey.data.local.entity.MarkerEntity
import com.parker.hotkey.data.local.entity.MemoEntity
import com.parker.hotkey.data.local.entity.VisitedGeohashEntity
import com.parker.hotkey.domain.model.LastSync
import timber.log.Timber

@Database(
    entities = [
        MarkerEntity::class, 
        MemoEntity::class, 
        GeohashLastSyncEntity::class,
        VisitedGeohashEntity::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(LastSyncConverter::class)
abstract class HotKeyDatabase : RoomDatabase() {
    abstract fun markerDao(): MarkerDao
    abstract fun memoDao(): MemoDao
    abstract fun geohashLastSyncDao(): GeohashLastSyncDao
    abstract fun visitedGeohashDao(): VisitedGeohashDao

    companion object {
        private const val DATABASE_NAME = "hotkey.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. 임시 테이블 생성
                db.execSQL("""
                    CREATE TABLE markers_temp (
                        id TEXT NOT NULL PRIMARY KEY,
                        geohash TEXT NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        createdAt INTEGER NOT NULL,
                        modifiedAt INTEGER NOT NULL,
                        lastSync TEXT NOT NULL,
                        version INTEGER NOT NULL,
                        isDeleted INTEGER NOT NULL
                    )
                """)
                
                db.execSQL("""
                    CREATE TABLE memos_temp (
                        id TEXT NOT NULL PRIMARY KEY,
                        markerId TEXT NOT NULL,
                        content TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        modifiedAt INTEGER NOT NULL,
                        lastSync TEXT NOT NULL,
                        version INTEGER NOT NULL,
                        isDeleted INTEGER NOT NULL,
                        FOREIGN KEY(markerId) REFERENCES markers(id) ON DELETE CASCADE
                    )
                """)

                // 2. 데이터 이전 - 마커
                db.execSQL("""
                    INSERT INTO markers_temp (
                        id, geohash, latitude, longitude, createdAt, modifiedAt, 
                        lastSync, version, isDeleted
                    )
                    SELECT 
                        id, geohash, latitude, longitude, createdAt, modifiedAt,
                        '{"timestamp":' || modifiedAt || 
                        ',"status":' || CASE 
                            WHEN syncState = 'SYNCED' THEN '"SUCCESS"'
                            WHEN syncState = 'PENDING' THEN '"NONE"'
                            ELSE '"ERROR"'
                        END ||
                        ',"serverVersion":' || CASE 
                            WHEN syncState = 'SYNCED' THEN version
                            ELSE 'null'
                        END || '}',
                        version,
                        isDeleted
                    FROM markers
                """)
                
                // 2. 데이터 이전 - 메모
                db.execSQL("""
                    INSERT INTO memos_temp (
                        id, markerId, content, createdAt, modifiedAt,
                        lastSync, version, isDeleted
                    )
                    SELECT 
                        id, markerId, content, createdAt, modifiedAt,
                        '{"timestamp":' || modifiedAt || 
                        ',"status":' || CASE 
                            WHEN syncState = 'SYNCED' THEN '"SUCCESS"'
                            WHEN syncState = 'PENDING' THEN '"NONE"'
                            ELSE '"ERROR"'
                        END ||
                        ',"serverVersion":' || CASE 
                            WHEN syncState = 'SYNCED' THEN version
                            ELSE 'null'
                        END || '}',
                        version,
                        isDeleted
                    FROM memos
                """)

                // 3. 기존 테이블 삭제
                db.execSQL("DROP TABLE markers")
                db.execSQL("DROP TABLE memos")

                // 4. 임시 테이블 이름 변경
                db.execSQL("ALTER TABLE markers_temp RENAME TO markers")
                db.execSQL("ALTER TABLE memos_temp RENAME TO memos")

                // 5. 인덱스 생성
                db.execSQL("CREATE INDEX index_markers_geohash ON markers(geohash)")
                db.execSQL("CREATE INDEX index_markers_modifiedAt ON markers(modifiedAt)")
                db.execSQL("CREATE INDEX index_memos_markerId ON memos(markerId)")
                db.execSQL("CREATE INDEX index_memos_modifiedAt ON memos(modifiedAt)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // markers 테이블에 userId 컬럼 추가
                db.execSQL(
                    "ALTER TABLE markers ADD COLUMN userId TEXT NOT NULL DEFAULT ''"
                )
                
                // memos 테이블에 userId 컬럼 추가
                db.execSQL(
                    "ALTER TABLE memos ADD COLUMN userId TEXT NOT NULL DEFAULT ''"
                )
            }
        }
        
        /**
         * 버전 3에서 4로의 마이그레이션 - 최적화된 인덱스 추가
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    Timber.d("마이그레이션 3→4 실행: 최적화된 인덱스 추가")
                    
                    // 기존 복합 인덱스가 있으면 삭제 (안전한 마이그레이션을 위해)
                    try {
                        db.execSQL("DROP INDEX IF EXISTS index_marker_geohash_userId")
                        db.execSQL("DROP INDEX IF EXISTS index_marker_userId_modifiedAt")
                    } catch (e: Exception) {
                        Timber.w(e, "기존 인덱스 삭제 중 오류 (무시해도 됨)")
                    }
                    
                    // 새 복합 인덱스 생성
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_marker_geohash_userId ON markers(geohash, userId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_marker_userId_modifiedAt ON markers(userId, modifiedAt)")
                    
                    Timber.d("마이그레이션 3→4 완료: 최적화된 인덱스 추가됨")
                } catch (e: Exception) {
                    Timber.e(e, "마이그레이션 3→4 중 오류 발생")
                    // 마이그레이션 실패해도 앱 작동에 영향 없도록 예외 처리
                }
            }
        }

        /**
         * 버전 4에서 5로의 마이그레이션 - 마커 로딩 성능 최적화를 위한 인덱스 추가
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    Timber.d("마이그레이션 4→5 실행: 마커 로딩 성능 최적화 인덱스 추가")
                    
                    // 기존 인덱스가 있으면 삭제 (안전한 마이그레이션을 위해)
                    try {
                        db.execSQL("DROP INDEX IF EXISTS index_marker_geohash_modifiedAt")
                    } catch (e: Exception) {
                        Timber.w(e, "기존 인덱스 삭제 중 오류 (무시해도 됨)")
                    }
                    
                    // 새 복합 인덱스 생성
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_marker_geohash_modifiedAt ON markers(geohash, modifiedAt)")
                    
                    Timber.d("마이그레이션 4→5 완료: 마커 로딩 성능 최적화 인덱스 추가됨")
                } catch (e: Exception) {
                    Timber.e(e, "마이그레이션 4→5 중 오류 발생")
                    // 마이그레이션 실패해도 앱 작동에 영향 없도록 예외 처리
                }
            }
        }

        /**
         * 버전 5에서 6으로의 마이그레이션 - GeohashLastSync와 VisitedGeohash 테이블 추가
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    Timber.d("마이그레이션 5→6 실행: GeohashLastSync와 VisitedGeohash 테이블 추가")
                    
                    // geohash_last_sync 테이블 생성
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS geohash_last_sync (
                            geohash TEXT NOT NULL PRIMARY KEY,
                            lastSyncTimestamp INTEGER NOT NULL
                        )
                    """)
                    
                    // visited_geohash 테이블 생성
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS visited_geohash (
                            geohash TEXT NOT NULL PRIMARY KEY,
                            firstVisitTime INTEGER NOT NULL,
                            lastVisitTime INTEGER NOT NULL,
                            visitCount INTEGER NOT NULL DEFAULT 1,
                            hasLocalData INTEGER NOT NULL DEFAULT 0,
                            hasSyncedWithServer INTEGER NOT NULL DEFAULT 0
                        )
                    """)
                    
                    Timber.d("마이그레이션 5→6 완료: GeohashLastSync와 VisitedGeohash 테이블 추가됨")
                } catch (e: Exception) {
                    Timber.e(e, "마이그레이션 5→6 중 오류 발생")
                    // 마이그레이션 실패해도 앱 작동에 영향 없도록 예외 처리
                }
            }
        }

        @Volatile
        private var INSTANCE: HotKeyDatabase? = null

        fun getInstance(context: Context): HotKeyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        fun buildDatabase(context: Context): HotKeyDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                HotKeyDatabase::class.java,
                DATABASE_NAME
            )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .setQueryCallback({ sqlQuery, bindArgs ->
                Timber.tag("RoomSQL").d("SQL 쿼리: $sqlQuery")
                Timber.tag("RoomSQL").d("SQL 바인딩: $bindArgs")
            }, Runnable::run)
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