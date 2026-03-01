package com.laniakea.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 0,
    val autoLoadEngine: Boolean = false,
    val userName: String = "",
    val theme: String = "PURPLE" // Default theme
)

@Entity(tableName = "entries")
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateTime: Long,
    val content: String,
    val mood: String,
    val numericMood: Double = 0.0,
    val latentVibe: Double = 0.0,
    val isVectorized: Boolean = false
)

@Entity(
    tableName = "vectors",
    foreignKeys = [ForeignKey(
        entity = DiaryEntry::class,
        parentColumns = ["id"],
        childColumns = ["entryId"],
        onDelete = ForeignKey.CASCADE
    )]
)

data class SentenceVector(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val vector: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SentenceVector

        if (id != other.id) return false
        if (entryId != other.entryId) return false
        if (!vector.contentEquals(other.vector)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + entryId.hashCode()
        result = 31 * result + vector.contentHashCode()
        return result
    }
}