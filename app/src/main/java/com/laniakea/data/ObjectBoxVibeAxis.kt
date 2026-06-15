package com.laniakea.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class ObjectBoxVibeAxis(
    @Id var id: Long = 0,
    var axisName: String = "", 
    var rightName: String = "", 
    var leftName: String = "",
    var rightCentroid: FloatArray? = null,
    var leftCentroid: FloatArray? = null,
    var axisVector: FloatArray? = null, 
    var centerVector: FloatArray? = null, 
    var version: Int = 1
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ObjectBoxVibeAxis

        if (id != other.id) return false
        if (axisName != other.axisName) return false
        if (rightName != other.rightName) return false
        if (leftName != other.leftName) return false
        if (version != other.version) return false
        if (rightCentroid != null) {
            if (other.rightCentroid == null) return false
            if (!rightCentroid.contentEquals(other.rightCentroid)) return false
        } else if (other.rightCentroid != null) return false
        if (leftCentroid != null) {
            if (other.leftCentroid == null) return false
            if (!leftCentroid.contentEquals(other.leftCentroid)) return false
        } else if (other.leftCentroid != null) return false
        if (axisVector != null) {
            if (other.axisVector == null) return false
            if (!axisVector.contentEquals(other.axisVector)) return false
        } else if (other.axisVector != null) return false
        if (centerVector != null) {
            if (other.centerVector == null) return false
            if (!centerVector.contentEquals(other.centerVector)) return false
        } else if (other.centerVector != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + axisName.hashCode()
        result = 31 * result + rightName.hashCode()
        result = 31 * result + leftName.hashCode()
        result = 31 * result + (rightCentroid?.contentHashCode() ?: 0)
        result = 31 * result + (leftCentroid?.contentHashCode() ?: 0)
        result = 31 * result + (axisVector?.contentHashCode() ?: 0)
        result = 31 * result + (centerVector?.contentHashCode() ?: 0)
        result = 31 * result + version
        return result
    }
}
