    package com.laniakea.manager

import com.laniakea.data.DiaryDatabase
import com.laniakea.data.TaglineTemplates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

    data class WritingMetrics(
    val entryLengths: List<Pair<Long, Float>>,
    val vocabularyDiversity: List<Pair<Long, Float>>,
    val questionFrequency: List<Pair<Long, Float>>,
    val firstPersonUsage: List<Pair<Long, Float>>,
    val futureVsPast: List<Pair<Long, Float>>,
    val syntacticPacing: List<Pair<Long, Float>>,
    val agencyScore: List<Pair<Long, Float>>,
    val epistemicModality: List<Pair<Long, Float>>,
    val processingMarkers: List<Pair<Long, Float>>,
    val temporalHorizon: List<Pair<Long, Float>>
)

class AnalyticsManager(
    private val db: DiaryDatabase,
    private val securityManager: SecurityManager
) {

    suspend fun analyzeWritingTrends(limit: Int = 30): WritingMetrics {
        return withContext(Dispatchers.IO) {
            val rawEntries = db.diaryDao().getRecentEntries(limit)
            // Reverse to chronological order (oldest first) for sparkline
            val entries = rawEntries.reversed().map { securityManager.decryptEntry(it) }

            val firstPersonWords = setOf("i", "me", "my", "myself", "mine", "i'm", "i've", "i'll", "i'd")
            val futureWords = setOf(
                "will", "going", "plan", "tomorrow", "soon", "next", "hope", "want", "intend",
                "goal", "ahead", "forward", "future", "aspire", "dream", "envision", "eventually",
                "someday", "upcoming", "later", "shall", "expect", "anticipate", "wish", "ambition"
            )
            val pastWords = setOf(
                "was", "had", "yesterday", "ago", "used", "before", "past", "remember", "remembered",
                "forgot", "back", "earlier", "then", "recall", "once", "former", "previously",
                "nostalgia", "regret", "missed", "childhood", "history", "memoir", "longtime", "already"
            )

            val entryLengths = mutableListOf<Pair<Long, Float>>()
            val vocabularyDiversity = mutableListOf<Pair<Long, Float>>()
            val questionFrequency = mutableListOf<Pair<Long, Float>>()
            val firstPersonUsage = mutableListOf<Pair<Long, Float>>()
            val futureVsPast = mutableListOf<Pair<Long, Float>>()
            val syntacticPacing = mutableListOf<Pair<Long, Float>>()
            val agencyScore = mutableListOf<Pair<Long, Float>>()
            val epistemicModality = mutableListOf<Pair<Long, Float>>()
            val processingMarkers = mutableListOf<Pair<Long, Float>>()
            val temporalHorizon = mutableListOf<Pair<Long, Float>>()

            for (entry in entries) {
                val content = entry.content
                if (content == "[Encrypted]" || content.isBlank()) continue

                val words = content.lowercase().split(Regex("[\\s,.!?;:()\"]+")).filter { it.isNotBlank() }
                val timestamp = entry.dateTime

                if (words.isEmpty()) continue

                // 1. Entry Length
                entryLengths.add(timestamp to words.size.toFloat())

                // 2. Vocabulary Diversity
                val uniqueRatio = words.distinct().size.toFloat() / words.size.toFloat()
                vocabularyDiversity.add(timestamp to uniqueRatio)

                // 3. Question Frequency
                val sentences = content.split(Regex("[.!?]+")).filter { it.isNotBlank() }
                val questions = content.count { it == '?' }
                val questionRatio = if (sentences.isNotEmpty()) questions.toFloat() / sentences.size.toFloat() else 0f
                questionFrequency.add(timestamp to questionRatio)

                // 4. First-Person Usage
                val fpCount = words.count { it in firstPersonWords }
                val fpRatio = fpCount.toFloat() / words.size.toFloat()
                firstPersonUsage.add(timestamp to fpRatio)

                // 5. Future vs Past Orientation (-1 = past-focused, +1 = future-focused)
                val futureCount = words.count { it in futureWords }
                val pastCount = words.count { it in pastWords }
                val total = (futureCount + pastCount).toFloat()
                val orientation = if (total > 0) (futureCount - pastCount) / total else 0f
                futureVsPast.add(timestamp to orientation)
                
                // 6. Cognitive Metrics
                syntacticPacing.add(timestamp to entry.syntacticPacing)
                agencyScore.add(timestamp to entry.agencyScore)
                epistemicModality.add(timestamp to entry.epistemicModality)
                processingMarkers.add(timestamp to entry.processingMarkers.toFloat())
                temporalHorizon.add(timestamp to entry.temporalHorizon)
            }

            WritingMetrics(
                entryLengths = entryLengths,
                vocabularyDiversity = vocabularyDiversity,
                questionFrequency = questionFrequency,
                firstPersonUsage = firstPersonUsage,
                futureVsPast = futureVsPast,
                syntacticPacing = syntacticPacing,
                agencyScore = agencyScore,
                epistemicModality = epistemicModality,
                processingMarkers = processingMarkers,
                temporalHorizon = temporalHorizon
            )
        }
    }

    fun generateTagline(year: String): String {
        return TaglineTemplates.ALL.random()(year)
    }

}
