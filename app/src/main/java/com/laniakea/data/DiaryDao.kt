package com.laniakea.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface DiaryDao {
    @Insert
    suspend fun insertEntry(entry: DiaryEntry): Long

    @Insert
    suspend fun insertVector(vector: SentenceVector)

    @Transaction
    suspend fun insertEntryWithVector(entry: DiaryEntry, vector: FloatArray) {
        val entryId = insertEntry(entry)
        insertVector(SentenceVector(entryId = entryId, vector = floatArrayToByteArray(vector)))
    }

    @Query("SELECT * FROM entries ORDER BY dateTime ASC")
    suspend fun getAllEntries(): List<DiaryEntry>

    @Query("SELECT vector FROM vectors WHERE entryId = :entryId")
    suspend fun getVectorForEntry(entryId: Long): List<ByteArray>

    @Query("DELETE FROM entries")
    suspend fun clearAllEntries()

    @Query("DELETE FROM vectors")
    suspend fun clearAllVectors()

    @Transaction
    suspend fun clearDatabase() {
        clearAllVectors()
        clearAllEntries()
    }

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