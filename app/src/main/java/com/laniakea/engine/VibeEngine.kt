package com.laniakea.engine

/**
 * The VibeEngine processes high-dimensional embeddings to derive a human-readable "vibe score".
 * 
 * TECHNICAL NOTE ON PRIVACY SHIELD & VECTOR SHUFFLING:
 * A common concern is whether the 'Vector Shuffling' in the Privacy Shield affects the accuracy 
 * of the vibe score. 
 * 
 * It DOES NOT affect accuracy for two reasons:
 * 1. SYMMETRY: The shuffling uses a fixed permutation derived from the user's unique encrypted seed.
 *    Both the journal entries and the anchors (Joy/Distress) are shuffled using the EXACT SAME map.
 * 2. MATHEMATICAL INVARIANCE: The vibe score is calculated using a Dot Product. The Dot Product 
 *    is 'permutation invariant'—as long as both vectors are reordered using the same map, the 
 *    scalar result (the score) remains identical.
 * 
 * This allows Laniakea to maintain a 'scrambled' vector space that is useless to outsiders 
 * but perfectly consistent for internal analysis.
 */
object VibeEngine {
    var joyAnchor: FloatArray? = null
    var distressAnchor: FloatArray? = null

    /**
     * Projects the given embedding onto the axis between joy and distress.
     * @return a score where positive is 'joyful' and negative is 'distressed'.
     */
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