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
)
