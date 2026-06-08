package com.laniakea.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id

@Entity
data class ObjectBoxSentenceVector(
    @Id var id: Long = 0,
    var entryId: Long = 0,
    @HnswIndex(dimensions = 768)
    var vector: FloatArray? = null,
    var semanticTheme: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ObjectBoxSentenceVector

        if (id != other.id) return false
        if (entryId != other.entryId) return false
        if (!vector.contentEquals(other.vector)) return false
        if (semanticTheme != other.semanticTheme) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + entryId.hashCode()
        result = 31 * result + (vector?.contentHashCode() ?: 0)
        result = 31 * result + (semanticTheme?.hashCode() ?: 0)
        return result
    }
}
