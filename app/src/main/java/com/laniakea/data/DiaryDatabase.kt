package com.laniakea.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.room.migration.Migration

@Database(
    entities = [DiaryEntry::class, AppSettings::class],
    version = 5,
    exportSchema = true
)
abstract class DiaryDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao

    companion object {
        @Volatile
        private var INSTANCE: DiaryDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN hasCompletedOnboarding INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN entryType TEXT NOT NULL DEFAULT 'WRITTEN'")
                db.execSQL("ALTER TABLE entries ADD COLUMN energyLevel REAL")
                db.execSQL("ALTER TABLE entries ADD COLUMN mainTheme TEXT")
                db.execSQL("ALTER TABLE entries ADD COLUMN mentalPace REAL")
                db.execSQL("ALTER TABLE entries ADD COLUMN connectionLevel REAL")
                db.execSQL("ALTER TABLE entries ADD COLUMN thinkingStyle TEXT")
                db.execSQL("ALTER TABLE entries ADD COLUMN timeFocus TEXT")
                db.execSQL("ALTER TABLE entries ADD COLUMN intensityLevel REAL")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN isNotificationEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN notificationHours TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): DiaryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DiaryDatabase::class.java,
                    "laniakea_db"
                ).fallbackToDestructiveMigration(true)
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Pre-populate database with a random name
                        CoroutineScope(Dispatchers.IO).launch {
                            val randomAlias = Aliases.ALL.random()
                            val randomName = randomAlias.name
                            INSTANCE?.diaryDao()?.saveSettings(AppSettings(id = 0, userName = randomName))
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }
    }
}