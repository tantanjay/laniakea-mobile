package com.laniakea.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class ObjectBoxThemeCentroid(
    @Id var id: Long = 0,
    var themeName: String = "",
    var vector: FloatArray? = null,
    var version: Int = 1
)
