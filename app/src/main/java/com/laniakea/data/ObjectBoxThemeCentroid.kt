package com.laniakea.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class ObjectBoxThemeCentroid(
    @Id var id: Long = 0,
    var themeName: String = "",
    var vector: FloatArray? = null,
    var version: Int = 1
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ObjectBoxThemeCentroid

        if (id != other.id) return false
        if (version != other.version) return false
        if (themeName != other.themeName) return false
        if (!vector.contentEquals(other.vector)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + version
        result = 31 * result + themeName.hashCode()
        result = 31 * result + (vector?.contentHashCode() ?: 0)
        return result
    }
}
