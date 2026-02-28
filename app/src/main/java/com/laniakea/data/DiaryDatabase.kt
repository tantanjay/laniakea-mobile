package com.laniakea.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [DiaryEntry::class, SentenceVector::class, AppSettings::class], version = 1)
abstract class DiaryDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao

    companion object {
        @Volatile
        private var INSTANCE: DiaryDatabase? = null

        fun getDatabase(context: Context): DiaryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DiaryDatabase::class.java,
                    "laniakea_db"
                ).addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Pre-populate database with a random name
                        CoroutineScope(Dispatchers.IO).launch {
                            val names = listOf(
                                "Stardust", "Nebula", "Cosmos", "Voyager", "Nova", 
                                "Quasar", "Zenith", "Aether", "Lumen", "Solstice",
                                "Eclipse", "Orion", "Lyra", "Altair", "Sirius",
                                "Polaris", "Vega", "Rigel", "Antares", "Spica",
                                "Arcturus", "Capella", "Castor", "Pollux", "Deneb",
                                "Regulus", "Fomalhaut", "Aldebaran", "Betelgeuse", "Procyon"
                            )
                            val randomName = names.random()
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