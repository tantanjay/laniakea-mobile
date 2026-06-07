package com.laniakea.engine

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.tanh

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

    private var joyAnchor: FloatArray? = null
    private var distressAnchor: FloatArray? = null

    private var axis: FloatArray? = null
    private var center: FloatArray? = null

    fun setAnchors(
        joy: FloatArray,
        distress: FloatArray,
        rotate: (FloatArray) -> FloatArray,
        normalize: (FloatArray) -> FloatArray
    ) {
        joyAnchor = rotate(normalize(joy))
        distressAnchor = rotate(normalize(distress))

        buildAxis()
    }

    private fun buildAxis() {

        val joy = joyAnchor ?: return
        val sad = distressAnchor ?: return

        val size = joy.size
        axis = FloatArray(size)
        center = FloatArray(size)

        for (i in 0 until size) {
            axis!![i] = joy[i] - sad[i]
            center!![i] = (joy[i] + sad[i]) * 0.5f
        }

        // normalize axis
        var norm = 0f
        for (v in axis!!) norm += v * v
        norm = kotlin.math.sqrt(norm)

        for (i in axis!!.indices) {
            axis!![i] /= (norm + 1e-9f)
        }
    }

    fun calculateVibeScore(embedding: FloatArray): Float {

        val axisVec = axis ?: return 0f
        val centerVec = center ?: return 0f

        var score = 0f

        for (i in embedding.indices) {
            score += (embedding[i] - centerVec[i]) * axisVec[i]
        }

        return scaleVibeSmooth(score)
    }

    fun scaleVibeSmooth(raw: Float, maxExpected: Float = 0.1f): Float {
        // normalized relative to expected max
        val normalized = raw / maxExpected
        // tanh naturally clamps between -1 and 1, but large values saturate smoothly
        val scaled = tanh(normalized.toDouble())
        // optional multiplier for desired range (e.g., -2..2)
        return (scaled * 2.0).toFloat()
    }
}