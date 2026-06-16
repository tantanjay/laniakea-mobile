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
        "Agency vs Helplessness" to Pair(
            "Agency" to listOf(
                "I took control of the situation and made it happen.",
                "I am responsible for my own choices and I am actively fixing this.",
                "I stepped up, decided what to do, and executed my plan."
            ),
            "Helplessness" to listOf(
                "There is nothing I can do, this just keeps happening to me.",
                "I have no control over this situation and I am stuck.",
                "Everything is out of my hands and I am overwhelmed by circumstances."
            )
        ),
        "Certainty vs Rumination" to Pair(
            "Certainty" to listOf(
                "I know exactly what I need to do and I am sure of my decision.",
                "The situation is clear, the facts are absolute, and I am confident.",
                "I have decided on my path and there is no doubt in my mind."
            ),
            "Rumination" to listOf(
                "I keep overthinking everything and wondering what if I made a mistake.",
                "Maybe I should have done something else, I'm not really sure.",
                "I feel confused, uncertain, and keep getting stuck in loops."
            )
        ),
        "Concrete vs Abstract" to Pair(
            "Concrete" to listOf(
                "I woke up, drank coffee, walked the dog, and sent some emails.",
                "I went to the store, bought groceries, and cooked dinner.",
                "The sun was shining brightly as I ran five miles this morning."
            ),
            "Abstract" to listOf(
                "The nature of existence and meaning is complicated and deeply philosophical.",
                "We must constantly evaluate our internal paradigms and universal truths.",
                "Time is a relative construct that intertwines with our perceived reality."
            )
        )
    )

    companion object {
        const val AXIS_TEMPLATE_VERSION = 2
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
            val rightCentroid = axis.rightCentroid
            val leftCentroid = axis.leftCentroid
            
            if (axisVec != null && centerVec != null && rightCentroid != null && leftCentroid != null) {
                val score = VibeEngine.calculateVibeScore(vector, axisVec, centerVec)
                val rightDist = VibeEngine.calculateDistance(vector, rightCentroid)
                val leftDist = VibeEngine.calculateDistance(vector, leftCentroid)
                
                val axisObj = JSONObject()
                axisObj.put("right", rightDist.toDouble())
                axisObj.put("left", leftDist.toDouble())
                axisObj.put("score", score.toDouble())
                
                jsonObj.put(axis.axisName, axisObj)
            } else if (axisVec != null && centerVec != null) {
                val score = VibeEngine.calculateVibeScore(vector, axisVec, centerVec)
                jsonObj.put(axis.axisName, score.toDouble())
            }
        }

        return jsonObj.toString()
    }

    fun getAxisScore(axisName: String, vector: FloatArray): Float? {
        val axes = cachedAxes ?: return null
        val axis = axes.find { it.axisName == axisName } ?: return null
        
        val axisVec = axis.axisVector ?: return null
        val centerVec = axis.centerVector ?: return null
        
        return VibeEngine.calculateVibeScore(vector, axisVec, centerVec)
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
