package com.laniakea.manager

import com.laniakea.data.DiaryDatabase
import com.laniakea.data.DiaryEntry
import com.laniakea.data.ObjectBoxManager
import com.laniakea.data.ObjectBoxSentenceVector_
import com.laniakea.engine.SentenceEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SemanticManager(
    private val db: DiaryDatabase,
    private val embedder: SentenceEmbedder,
    private val securityManager: SecurityManager,
    private val isEngineActive: () -> Boolean
) {

    private var cachedThemeVectors: Map<String, FloatArray>? = null

    private val richThemes = mapOf(
        "Relationships & Connection" to listOf(
            "Relationships with family, friends, romance, and coworkers.",
            "Feeling connected, social interactions, and belonging.",
            "Conflict with people, arguments, and feeling lonely."
        ),
        "Career & Purpose" to listOf(
            "Career, professional life, work, and projects.",
            "Productivity, achieving success, and planning for the future.",
            "Finding meaning, ambition, and work responsibilities."
        ),
        "Goals & Ambition" to listOf(
            "Goal setting, ambition, pushing boundaries, and milestones.",
            "I have zero tolerance for friction when chasing my goals.",
            "Building the future, succeeding, and hard work."
        ),
        "Inner Reflection" to listOf(
            "Deep self-observation, contemplation, mindfulness, and solitude.",
            "Understanding my own mind, wandering thoughts, and introspection.",
            "Observation is more interesting than participation today."
        ),
        "Emotional Wellbeing" to listOf(
            "Feelings, moods, mental health, and emotional processing.",
            "My heart is pinned to my sleeve today.",
            "Feeling sensitive, emotional highs and lows, and inner peace."
        ),
        "Physical Wellbeing" to listOf(
            "Body, health, movement, exercise, diet, and sleep.",
            "My bones feel tired today, lacking energy, physical exhaustion.",
            "Physical sensations, resting, and recovering."
        ),
        "Stress & Anxiety" to listOf(
            "Feeling overwhelmed, anxious, worried, and stressed.",
            "A strange sense of impending deadlines and pressure.",
            "Background static in my brain today, panic, and nervousness."
        ),
        "Learning & Curiosity" to listOf(
            "Education, studying, reading, and discovering new ideas.",
            "Curiosity, acquiring skills, and personal growth.",
            "Fascination with how things work and learning."
        ),
        "Creativity & Expression" to listOf(
            "Art, music, writing, drawing, and coding.",
            "Creative projects, self-expression, and making things.",
            "Flow state, imagination, and artistic expression."
        ),
        "Uncertainty & Waiting" to listOf(
            "Not knowing the future, feeling stuck, and ambiguity.",
            "Wondering about the paths not taken and waiting for news.",
            "Honestly, i keep checking the clock, doubt, and feeling lost."
        ),
        "Gratitude & Joy" to listOf(
            "Thankfulness, appreciation, and moments of happiness.",
            "Counting blessings, joy, and positive experiences.",
            "Feeling grateful for the little things in life."
        ),
        "Challenges & Resilience" to listOf(
            "Facing difficult times, overcoming obstacles, and resilience.",
            "The walls are down, pushing through hardship, and endurance.",
            "Staying strong during a crisis and bouncing back."
        )
    )
    
    val availableThemeNames = richThemes.keys.toList()

    companion object {
        /**
         * Distance thresholds for normalized 768-dim vectors (L2 distance).
         * 0.0 = identical, ~1.0 = weakly related, ~1.41 = unrelated, 2.0 = opposite.
         *
         * SEARCH/SIMILAR: 1.2 ≈ cosine similarity 0.28 — filters clearly unrelated results.
         * THEME_CLUSTER:  1.25 ≈ cosine similarity 0.22 — looser threshold for rich theme descriptions.
         */
        private const val MAX_DISTANCE_SEARCH = 1.2
        const val MAX_DISTANCE_THEME = 1.25
    }

    suspend fun semanticSearch(query: String, limit: Int = 5): List<DiaryEntry> {
        return withContext(Dispatchers.IO) {
            // Try semantic search if the engine is active
            if (isEngineActive()) {
                val vector = embedder.embed(query)
                if (vector != null) {
                    val similarIds = ObjectBoxManager.search(vector, limit)
                        .filter { it.score < MAX_DISTANCE_SEARCH }
                        .map { it.vector.entryId }
                    
                    if (similarIds.isNotEmpty()) {
                        val entries = db.diaryDao().getEntriesByIds(similarIds)
                        val entryMap = entries.associateBy { it.id }
                        return@withContext similarIds.mapNotNull { entryMap[it] }.map { securityManager.decryptEntry(it) }
                    }
                }
            }
            
            // Fallback: plaintext search using lazy sequences to avoid decrypting all entries
            val allEntries = db.diaryDao().getAllEntries()
            val lowerQuery = query.lowercase()
            allEntries
                .asSequence()
                .map { securityManager.decryptEntry(it) }
                .filter { it.content.lowercase().contains(lowerQuery) }
                .take(limit)
                .toList()
        }
    }

    suspend fun findSimilarEntries(entryId: Long, limit: Int = 5): List<DiaryEntry> {
        return withContext(Dispatchers.IO) {
            val vectorObj = ObjectBoxManager.vectorBox.query(ObjectBoxSentenceVector_.entryId.equal(entryId)).build().findFirst()
            val vector = vectorObj?.vector ?: return@withContext emptyList()
            
            val similarIds = ObjectBoxManager.search(vector, limit + 1)
                .filter { it.score < MAX_DISTANCE_SEARCH }
                .map { it.vector.entryId }
                .filter { it != entryId }
                .take(limit)
            
            if (similarIds.isEmpty()) return@withContext emptyList()
            
            val entries = db.diaryDao().getEntriesByIds(similarIds)
            val entryMap = entries.associateBy { it.id }
            similarIds.mapNotNull { entryMap[it] }.map { securityManager.decryptEntry(it) }
        }
    }

    suspend fun initializeThemes(selectedThemes: List<String>) {
        if (!isEngineActive()) return
        
        // Only initialize once
        if (cachedThemeVectors != null) return

        val vectors = mutableMapOf<String, FloatArray>()
        for ((title, descriptions) in richThemes) {
            if (selectedThemes.contains(title)) {
                val embeddedDesc = descriptions.mapNotNull { embedder.embed(it) }
                if (embeddedDesc.isNotEmpty()) {
                    vectors[title] = averageVectors(embeddedDesc)
                }
            }
        }
        cachedThemeVectors = vectors
    }

    suspend fun reinitializeThemes(selectedThemes: List<String>) {
        cachedThemeVectors = null
        initializeThemes(selectedThemes)
    }

    fun getCachedThemes(): Map<String, FloatArray>? {
        return cachedThemeVectors
    }

    fun calculateL2Distance(a: FloatArray?, b: FloatArray): Double {
        if (a == null) return Double.MAX_VALUE
        var sum = 0.0
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return kotlin.math.sqrt(sum)
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
        var sumSq = 0f
        for (i in 0 until size) {
            result[i] /= vectors.size
            sumSq += result[i] * result[i]
        }
        val norm = kotlin.math.sqrt(sumSq.toDouble()).toFloat()
        if (norm > 0) {
            for (i in 0 until size) {
                result[i] /= norm
            }
        }
        return result
    }

    suspend fun getThemeClusters(selectedThemes: List<String>): Map<String, List<DiaryEntry>> {
        if (!isEngineActive()) return emptyMap()
        
        // Ensure themes are initialized
        if (cachedThemeVectors == null) {
            initializeThemes(selectedThemes)
        }
        
        val themeVectors = cachedThemeVectors ?: return emptyMap()

        return withContext(Dispatchers.IO) {
            val allVectors = ObjectBoxManager.vectorBox.all
            val clusterMap = mutableMapOf<String, MutableList<Pair<Long, Double>>>()
            
            // Forward Mapping: Bin each entry into strictly its closest theme
            for (vectorObj in allVectors) {
                var bestTheme = ""
                var minDistance = Double.MAX_VALUE

                for ((themeName, themeVector) in themeVectors) {
                    val distance = calculateL2Distance(vectorObj.vector, themeVector)
                    if (distance < minDistance) {
                        minDistance = distance
                        bestTheme = themeName
                    }
                }

                if (minDistance < MAX_DISTANCE_THEME) {
                    clusterMap.getOrPut(bestTheme) { mutableListOf() }.add(vectorObj.entryId to minDistance)
                }
            }

            // Resolve entries and build final map
            val finalClusters = mutableMapOf<String, List<DiaryEntry>>()
            
            for ((theme, pairs) in clusterMap) {
                // Take top 3 closest entries for this theme
                val topIds = pairs.sortedBy { it.second }.take(3).map { it.first }
                
                if (topIds.isNotEmpty()) {
                    val entries = db.diaryDao().getEntriesByIds(topIds)
                    val entryMap = entries.associateBy { it.id }
                    val decryptedEntries = topIds.mapNotNull { entryMap[it] }.map { securityManager.decryptEntry(it) }
                    
                    finalClusters[theme] = decryptedEntries
                }
            }
            
            finalClusters
        }
    }

}
