package com.laniakea.engine

object VibeEngine {
    var joyAnchor: FloatArray? = null
    var distressAnchor: FloatArray? = null

    // This is the function you were looking for earlier
    fun calculateVibeScore(embedding: FloatArray): Float {
        val joy = joyAnchor ?: return 0f
        val sad = distressAnchor ?: return 0f

        // 1. Calculate the 'Vibe Axis' (Joy - Distress)
        val axis = FloatArray(embedding.size) { i -> joy[i] - sad[i] }

        // 2. Normalize the axis
        val magnitude = kotlin.math.sqrt(axis.fold(0f) { acc, f -> acc + f * f }.toDouble()).toFloat()
        if (magnitude == 0f) return 0f
        val unitAxis = axis.map { it / magnitude }

        // 3. Dot Product (Project current embedding onto the axis)
        var dotProduct = 0f
        for (i in embedding.indices) {
            dotProduct += embedding[i] * unitAxis[i]
        }
        return dotProduct
    }
}