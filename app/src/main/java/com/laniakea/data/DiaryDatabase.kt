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
    version = 3,
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

        fun getDatabase(context: Context): DiaryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DiaryDatabase::class.java,
                    "laniakea_db"
                ).fallbackToDestructiveMigration(true)
                .addMigrations(MIGRATION_2_3)
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