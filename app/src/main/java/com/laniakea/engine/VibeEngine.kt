package com.laniakea.engine

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

    fun calculateAxisAndCenter(pos: FloatArray, neg: FloatArray): Pair<FloatArray, FloatArray> {
        val size = pos.size
        val axis = FloatArray(size)
        val center = FloatArray(size)

        for (i in 0 until size) {
            axis[i] = pos[i] - neg[i]
            center[i] = (pos[i] + neg[i]) * 0.5f
        }

        // normalize axis
        var norm = 0f
        for (v in axis) norm += v * v
        norm = kotlin.math.sqrt(norm)

        for (i in axis.indices) {
            axis[i] /= (norm + 1e-9f)
        }

        return Pair(axis, center)
    }

    fun calculateVibeScore(embedding: FloatArray, axisVec: FloatArray, centerVec: FloatArray): Float {
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