package com.laniakea.engine

import com.laniakea.data.DiaryDao
import com.laniakea.data.ObjectBoxManager
import com.laniakea.data.ObjectBoxSentenceVector_
import kotlin.math.sqrt

/**
 * Detects structural anomalies in user journal entries by calculating the semantic distance 
 * between a new entry's vector and the baseline centroid of the last N entries.
 */
class AnomalyDetector(
    private val diaryDao: DiaryDao
) {
    /**
     * Threshold for L2 distance. 
     * Typical normalized vectors have max distance of ~2.0 (opposite directions).
     * 1.41 means orthogonal (unrelated). Distance > 1.4 suggests a unique day/theme.
     */
    private val anomalyThreshold = 1.4f

    /**
     * Analyzes a new vector against the user's recent baseline.
     * @param newVector The embedding of the new entry
     * @param baselineLimit The number of previous entries to use for the baseline
     * @return Pair of (distanceScore, isAnomaly)
     */
    suspend fun detectAnomaly(newVector: FloatArray, baselineLimit: Int = 30): Pair<Float, Boolean> {
        val recentEntries = diaryDao.getRecentEntries(baselineLimit)
        if (recentEntries.isEmpty()) {
            return Pair(0f, false) // Not enough data for a baseline
        }

        val recentEntryIds = recentEntries.map { it.id }.toLongArray()
        
        // Fetch the corresponding vectors from ObjectBox
        val box = ObjectBoxManager.vectorBox
        val baselineVectors = box.query()
            .`in`(ObjectBoxSentenceVector_.entryId, recentEntryIds)
            .build()
            .find()
            .mapNotNull { it.vector }

        if (baselineVectors.isEmpty()) {
            return Pair(0f, false)
        }

        // Calculate the centroid (average) of the baseline vectors
        val dimensions = newVector.size
        val centroid = FloatArray(dimensions)
        
        for (vector in baselineVectors) {
            for (i in 0 until dimensions) {
                centroid[i] += vector[i]
            }
        }
        
        val sizeFloat = baselineVectors.size.toFloat()
        for (i in 0 until dimensions) {
            centroid[i] /= sizeFloat
        }

        // Calculate L2 distance between newVector and centroid
        var sumSquaredDiff = 0f
        for (i in 0 until dimensions) {
            val diff = newVector[i] - centroid[i]
            sumSquaredDiff += diff * diff
        }
        val distance = sqrt(sumSquaredDiff)

        val isAnomaly = distance > anomalyThreshold

        return Pair(distance, isAnomaly)
    }
}
