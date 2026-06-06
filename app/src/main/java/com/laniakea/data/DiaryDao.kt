package com.laniakea.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class MoodScores(
    val dateTime: Long,
    val numericMood: Double,
    val latentVibe: Double
)

@Dao
interface DiaryDao {
    @Insert
    suspend fun insertEntry(entry: DiaryEntry): Long

    @Update
    suspend fun updateEntry(entry: DiaryEntry)

    @Query("SELECT * FROM entries ORDER BY dateTime ASC")
    suspend fun getAllEntries(): List<DiaryEntry>

    @Query("SELECT * FROM entries WHERE id IN (:ids)")
    suspend fun getEntriesByIds(ids: List<Long>): List<DiaryEntry>

    @Query("SELECT * FROM entries ORDER BY dateTime DESC LIMIT :limit")
    suspend fun getRecentEntries(limit: Int): List<DiaryEntry>

    @Query("SELECT * FROM entries ORDER BY dateTime DESC")
    fun getAllEntriesFlow(): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM entries WHERE dateTime >= :startDate AND dateTime <= :endDate ORDER BY dateTime DESC")
    fun getEntriesInRange(startDate: Long, endDate: Long): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM entries WHERE dateTime >= :startDate AND dateTime <= :endDate ORDER BY dateTime DESC")
    suspend fun getEntriesInRangeSnapshot(startDate: Long, endDate: Long): List<DiaryEntry>

    @Query("SELECT dateTime, numericMood, latentVibe FROM entries WHERE isVectorized = 1 ORDER BY dateTime ASC")
    suspend fun getAllMoodScores(): List<MoodScores>

    @Query("SELECT * FROM entries WHERE isVectorized = 0")
    suspend fun getUnprocessedEntries(): List<DiaryEntry>

    @Query("SELECT COUNT(*) FROM entries")
    suspend fun getTotalEntriesCount(): Int

    @Query("SELECT COUNT(*) FROM entries WHERE isVectorized = 0")
    suspend fun getUnprocessedEntriesCount(): Int

    @Query("SELECT MIN(dateTime) FROM entries")
    suspend fun getOldestTimestamp(): Long?

    @Query("""
        SELECT id 
        FROM entries 
        WHERE numericMood = :moodValue
        ORDER BY dateTime DESC
        LIMIT :limit
    """)
    suspend fun getRecentEntryIdsByNumericMood(moodValue: Double, limit: Int): List<Long>

    @Query("DELETE FROM entries")
    suspend fun clearAllEntries()

    @Transaction
    suspend fun clearDatabase() {
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
