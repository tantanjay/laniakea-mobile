package com.laniakea.manager

import com.laniakea.data.ObjectBoxManager
import com.laniakea.data.ObjectBoxVibeAxis
import com.laniakea.engine.SentenceEmbedder
import com.laniakea.engine.VibeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

class VibeManager(
    private val embedder: SentenceEmbedder,
    private val isEngineActive: () -> Boolean
) {
    private var cachedAxes: List<ObjectBoxVibeAxis>? = null
    private val initMutex = Mutex()

    private val predefinedAxes = mapOf(
        "Positivity vs Negativity" to Pair(
            "Positivity" to listOf(
                "I feel happy, calm, and satisfied with life.",
                "Everything is going well, and I am optimistic about the future.",
                "I am full of energy, joy, and positive vibes."
            ),
            "Negativity" to listOf(
                "I feel stressed, tired, and discouraged.",
                "Everything is going wrong, and I feel overwhelmed.",
                "I am feeling sad, anxious, and drained of energy."
            )
        ),
        "Active vs Passive" to Pair(
            "Active" to listOf(
                "I am being highly productive and getting things done.",
                "Feeling energized, motivated, and taking action.",
                "Moving fast, working hard, and making progress."
            ),
            "Passive" to listOf(
                "I am resting, relaxing, and taking it easy.",
                "Doing nothing, recovering, and being still.",
                "Feeling lethargic, unmotivated, and passive."
            )
        )
    )

    companion object {
        const val AXIS_TEMPLATE_VERSION = 1
    }

    suspend fun initializeAxes() {
        if (!isEngineActive()) return

        if (cachedAxes != null) return

        initMutex.withLock {
            if (cachedAxes != null) return@withLock

            withContext(Dispatchers.IO) {
                val axisBox = ObjectBoxManager.vibeAxisBox
                val storedAxes = axisBox.query().build().find()

                if (storedAxes.isNotEmpty() && storedAxes.first().version == AXIS_TEMPLATE_VERSION) {
                    if (storedAxes.size == predefinedAxes.size) {
                        cachedAxes = storedAxes
                        return@withContext
                    }
                }

                // If missing or version mismatch, recreate them
                axisBox.removeAll()
                val newAxes = mutableListOf<ObjectBoxVibeAxis>()

                for ((axisName, pair) in predefinedAxes) {
                    val rightName = pair.first.first
                    val rightPrompts = pair.first.second
                    val leftName = pair.second.first
                    val leftPrompts = pair.second.second

                    val rightEmbeddings = rightPrompts.mapNotNull { embedder.embed(it) }
                    val leftEmbeddings = leftPrompts.mapNotNull { embedder.embed(it) }

                    if (rightEmbeddings.isNotEmpty() && leftEmbeddings.isNotEmpty()) {
                        val rightCentroid = averageVectors(rightEmbeddings)
                        val leftCentroid = averageVectors(leftEmbeddings)

                        val (axisVec, centerVec) = VibeEngine.calculateAxisAndCenter(rightCentroid, leftCentroid)

                        val axisObj = ObjectBoxVibeAxis(
                            axisName = axisName,
                            rightName = rightName,
                            leftName = leftName,
                            rightCentroid = rightCentroid,
                            leftCentroid = leftCentroid,
                            axisVector = axisVec,
                            centerVector = centerVec,
                            version = AXIS_TEMPLATE_VERSION
                        )
                        newAxes.add(axisObj)
                    }
                }

                if (newAxes.isNotEmpty()) {
                    axisBox.put(newAxes)
                    cachedAxes = newAxes
                }
            }
        }
    }

    fun isAxesInitialized(): Boolean {
        return cachedAxes != null
    }

    suspend fun calculateVibesJson(vector: FloatArray?): String? {
        if (vector == null || !isEngineActive()) return null

        if (cachedAxes == null) {
            initializeAxes()
        }

        val axes = cachedAxes ?: return null
        val jsonObj = JSONObject()

        for (axis in axes) {
            val axisVec = axis.axisVector
            val centerVec = axis.centerVector
            if (axisVec != null && centerVec != null) {
                val score = VibeEngine.calculateVibeScore(vector, axisVec, centerVec)
                jsonObj.put(axis.axisName, score)
            }
        }

        return jsonObj.toString()
    }

    private fun averageVectors(vectors: List<FloatArray>): FloatArray {
        if (vectors.isEmpty()) return FloatArray(0)
        val size = vectors.first().size
        val result = FloatArray(size)
        for (vec in vectors) {
            for (i in 0 until size) {
                result[i] += vec[i]
            }
        }
        for (i in 0 until size) {
            result[i] /= vectors.size
        }
        // Normalization is assumed to be handled previously or mathematically sound in calculateAxisAndCenter
        return result
    }
}
