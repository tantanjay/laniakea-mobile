package com.laniakea.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class MoodScores(
    val numericMood: Double,
    val latentVibe: Double
)

@Dao
interface DiaryDao {
    @Insert
    suspend fun insertEntry(entry: DiaryEntry): Long

    @Insert
    suspend fun insertVector(vector: SentenceVector)

    @Update
    suspend fun updateEntry(entry: DiaryEntry)

    @Transaction
    suspend fun insertEntryWithVector(entry: DiaryEntry, vector: FloatArray) {
        val entryId = insertEntry(entry)
        insertVector(SentenceVector(entryId = entryId, vector = floatArrayToByteArray(vector)))
    }

    @Query("SELECT * FROM entries ORDER BY dateTime ASC")
    suspend fun getAllEntries(): List<DiaryEntry>

    @Query("SELECT numericMood, latentVibe FROM entries ORDER BY dateTime ASC")
    suspend fun getAllMoodScores(): List<MoodScores>

    @Query("SELECT * FROM entries WHERE isVectorized = 0")
    suspend fun getUnprocessedEntries(): List<DiaryEntry>

    @Query("SELECT COUNT(*) FROM entries")
    suspend fun getTotalEntriesCount(): Int

    @Query("SELECT COUNT(*) FROM entries WHERE isVectorized = 0")
    suspend fun getUnprocessedEntriesCount(): Int

    @Query("SELECT MIN(dateTime) FROM entries")
    suspend fun getOldestTimestamp(): Long?

    @Query("SELECT vector FROM vectors WHERE entryId = :entryId")
    suspend fun getVectorForEntry(entryId: Long): List<ByteArray>

    @Query("""
        SELECT v.vector 
        FROM vectors v 
        JOIN entries e ON v.entryId = e.id 
        WHERE e.numericMood = :moodValue
        ORDER BY e.dateTime DESC
        LIMIT :limit
    """)
    suspend fun getRecentVectorsByNumericMood(moodValue: Double, limit: Int): List<ByteArray>

    @Query("DELETE FROM entries")
    suspend fun clearAllEntries()

    @Query("DELETE FROM vectors")
    suspend fun clearAllVectors()

    @Transaction
    suspend fun clearDatabase() {
        clearAllVectors()
        clearAllEntries()
    }

    @Query("SELECT * FROM app_settings WHERE id = 0")
    suspend fun getSettings(): AppSettings?

    @Query("SELECT * FROM app_settings WHERE id = 0")
    fun getSettingsFlow(): Flow<AppSettings?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AppSettings)

    fun floatArrayToByteArray(floats: FloatArray): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(floats.size * 4)
        floats.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    fun byteArrayToFloatArray(bytes: ByteArray): FloatArray {
        val buffer = java.nio.ByteBuffer.wrap(bytes)
        val floats = FloatArray(bytes.size / 4)
        for (i in floats.indices) floats[i] = buffer.getFloat()
        return floats
    }
}