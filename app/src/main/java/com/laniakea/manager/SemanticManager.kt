package com.laniakea.manager

import com.laniakea.data.DiaryDatabase
import com.laniakea.data.DiaryEntry
import com.laniakea.data.ObjectBoxManager
import com.laniakea.data.ObjectBoxSentenceVector_
import com.laniakea.engine.SentenceEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SemanticManager(
    private val db: DiaryDatabase,
    private val embedder: SentenceEmbedder,
    private val securityManager: SecurityManager,
    private val isEngineActive: () -> Boolean
) {

    private var cachedThemeCentroids: Map<String, FloatArray>? = null
    private val initMutex = Mutex()

    private val richThemes = mapOf(
        "Relationships & Connection" to listOf(
            "Relationships with family, friends, romance, and coworkers.",
            "Feeling connected, social interactions, belonging, and community.",
            "Conflict with people, arguments, disagreements, and loneliness."
        ),
        "Career & Purpose" to listOf(
            "Career, professional life, work projects, and job responsibilities.",
            "Productivity, achieving success, promotions, and planning ahead.",
            "Finding meaning and purpose through work and profession."
        ),
        "Goals & Ambition" to listOf(
            "Goal setting, ambition, milestones, and personal targets.",
            "Determination, drive, discipline, and pushing boundaries.",
            "Building the future, succeeding, and working hard toward objectives."
        ),
        "Inner Reflection" to listOf(
            "Self-observation, contemplation, mindfulness, and solitude.",
            "Understanding my own mind, introspection, and self-awareness.",
            "Quiet reflection, journaling about thoughts, and inner dialogue."
        ),
        "Emotional Wellbeing" to listOf(
            "Feelings, moods, mental health, and emotional processing.",
            "Emotional sensitivity, vulnerability, and being open with feelings.",
            "Emotional highs and lows, inner peace, and mood regulation."
        ),
        "Physical Wellbeing" to listOf(
            "Body, health, exercise, fitness, diet, and sleep quality.",
            "Fatigue, lacking energy, physical exhaustion, and body aches.",
            "Physical sensations, resting, recovering, and medical health."
        ),
        "Stress & Anxiety" to listOf(
            "Feeling overwhelmed, anxious, worried, and stressed out.",
            "Deadline pressure, being overloaded, and mental tension.",
            "Panic, nervousness, restlessness, and racing thoughts."
        ),
        "Learning & Curiosity" to listOf(
            "Education, studying, reading books, and discovering new ideas.",
            "Curiosity, acquiring new skills, and intellectual growth.",
            "Research, fascination with how things work, and knowledge seeking."
        ),
        "Creativity & Expression" to listOf(
            "Art, music, writing, drawing, coding, and creative work.",
            "Creative projects, self-expression, and making things.",
            "Imagination, artistic inspiration, and creative flow."
        ),
        "Uncertainty & Waiting" to listOf(
            "Not knowing the future, feeling stuck, and ambiguity.",
            "Doubt, indecision, waiting for results, and being unsure.",
            "Feeling lost, confused about direction, and lacking clarity."
        ),
        "Gratitude & Joy" to listOf(
            "Thankfulness, appreciation, and moments of happiness.",
            "Counting blessings, joy, and positive experiences.",
            "Feeling grateful, contentment, and savoring good moments."
        ),
        "Challenges & Resilience" to listOf(
            "Facing difficult times, overcoming obstacles, and resilience.",
            "Struggling through hardship, endurance, and perseverance.",
            "Staying strong during a crisis, bouncing back, and coping."
        ),
        "Leisure & Recreation" to listOf(
            "Playing games, hobbies, entertainment, and unwinding.",
            "Watching movies, playing video games, and relaxing activities.",
            "Having fun, enjoying free time, and recreational pastimes."
        ),
        "Travel & Exploration" to listOf(
            "Vacations, flights, traveling, sightseeing, and tourism.",
            "Exploring new places, trips, and souvenir hunting.",
            "Being away from home, commuting, and navigating new areas."
        ),
        "Food & Dining" to listOf(
            "Eating meals, restaurants, cooking, and food cravings.",
            "Dining out, enjoying food, cafes, and discovering new dishes.",
            "Snacks, drinks, meals, and culinary experiences."
        ),
        "Daily Routine & Chores" to listOf(
            "Everyday tasks, running errands, chores, and household work.",
            "Daily habits, regular routines, and mundane activities.",
            "Grocery shopping, cleaning, and doing standard day-to-day things."
        )
    )
    
    companion object {
        const val THEME_TEMPLATE_VERSION = 4
        
        /**
         * Distance thresholds for normalized 768-dim vectors (L2 distance).
         * 0.0 = identical, ~1.0 = weakly related, ~1.41 = unrelated, 2.0 = opposite.
         *
         * SEARCH/SIMILAR: 0.85 ≈ cosine similarity 0.64 — filters clearly unrelated results.
         * THEME_CLUSTER:  0.95 ≈ tighter threshold to ensure semantic relevance.
         */
        private const val MAX_DISTANCE_SEARCH = 0.85
        const val MAX_DISTANCE_THEME = 0.95
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
            
            // Fetch extra to account for potential exact match filtering
            val similarIds = ObjectBoxManager.search(vector, limit + 10)
                .filter { it.score < MAX_DISTANCE_SEARCH }
                .map { it.vector.entryId }
                .filter { it != entryId }
            
            if (similarIds.isEmpty()) return@withContext emptyList()
            
            val entries = db.diaryDao().getEntriesByIds(similarIds + entryId)
            val entryMap = entries.associateBy { it.id }
            
            val targetEntryRaw = entryMap[entryId] ?: return@withContext emptyList()
            val targetEntry = securityManager.decryptEntry(targetEntryRaw)
            val targetContent = targetEntry.content.trim().lowercase()

            similarIds.mapNotNull { entryMap[it] }
                .map { securityManager.decryptEntry(it) }
                .filter { it.content.trim().lowercase() != targetContent }
                .take(limit)
        }
    }

    suspend fun initializeThemes() {
        if (!isEngineActive()) return
        
        // Only initialize once
        if (cachedThemeCentroids != null) return

        initMutex.withLock {
            if (cachedThemeCentroids != null) return@withLock
            
            withContext(Dispatchers.IO) {
                val themeBox = ObjectBoxManager.themeBox
                val storedThemes = themeBox.query().build().find()
                
                // Attempt to load from ObjectBox
                if (storedThemes.isNotEmpty() && storedThemes.first().version == THEME_TEMPLATE_VERSION) {
                    val centroids = mutableMapOf<String, FloatArray>()
                    for (t in storedThemes) {
                        t.vector?.let { centroids[t.themeName] = it }
                    }
                    if (centroids.size == richThemes.size) {
                        cachedThemeCentroids = centroids
                        return@withContext
                    }
                }
                
                // If not available or version mismatch, calculate from scratch
                themeBox.removeAll()
                val centroids = mutableMapOf<String, FloatArray>()
                for ((title, descriptions) in richThemes) {
                    val embeddedDesc = descriptions.mapNotNull { embedder.embedRaw(it) }
                    if (embeddedDesc.isNotEmpty()) {
                        val avg = averageVectors(embeddedDesc)
                        centroids[title] = avg
                        themeBox.put(
                            com.laniakea.data.ObjectBoxThemeCentroid(
                                themeName = title, 
                                vector = avg, 
                                version = THEME_TEMPLATE_VERSION
                            )
                        )
                    }
                }
                cachedThemeCentroids = centroids
            }
        }
    }

    suspend fun classifyTheme(rawVector: FloatArray?): String? {
        if (rawVector == null || !isEngineActive()) return null
        
        if (cachedThemeCentroids == null) {
            initializeThemes()
        }
        
        val centroids = cachedThemeCentroids ?: return null
        
        var bestTheme: String? = null
        var minDistance = Double.MAX_VALUE

        for ((themeName, centroid) in centroids) {
            val distance = calculateL2Distance(rawVector, centroid)
            if (distance < minDistance) {
                minDistance = distance
                bestTheme = themeName
            }
        }

        return if (minDistance < MAX_DISTANCE_THEME) bestTheme else null
    }

    fun isThemesInitialized(): Boolean {
        return cachedThemeCentroids != null
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
        if (cachedThemeCentroids == null) {
            initializeThemes()
        }

        return withContext(Dispatchers.IO) {
            val vectorBox = ObjectBoxManager.vectorBox
            val allVectors = vectorBox.query().build().find()

            // Group by pre-calculated semanticTheme
            val groupedByTheme = allVectors
                .filter { it.semanticTheme != null && selectedThemes.contains(it.semanticTheme) }
                .groupBy { it.semanticTheme!! }

            val finalClusters = mutableMapOf<String, List<DiaryEntry>>()

            for ((theme, vectors) in groupedByTheme) {
                // Sort by entryId DESC (most recent) and take top 10 potential entries
                val potentialIds = vectors.sortedByDescending { it.entryId }.take(10).map { it.entryId }
                
                if (potentialIds.isNotEmpty()) {
                    val entries = db.diaryDao().getEntriesByIds(potentialIds)
                    val entryMap = entries.associateBy { it.id }
                    
                    val decryptedEntries = potentialIds
                        .mapNotNull { entryMap[it] }
                        .map { securityManager.decryptEntry(it) }
                        .distinctBy { it.content.lowercase().trim() }
                        .take(3) // Finally show the top 3 distinct recent entries
                    
                    finalClusters[theme] = decryptedEntries
                }
            }
            
            finalClusters
        }
    }

}
