package com.laniakea.manager

import com.laniakea.data.DiaryDatabase
import com.laniakea.data.DiaryEntry
import com.laniakea.data.ObjectBoxManager
import com.laniakea.data.ObjectBoxSentenceVector_
import com.laniakea.data.PeriodDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class PeriodDigestManager(
    private val db: DiaryDatabase,
    private val securityManager: SecurityManager,
    private val isEngineActive: () -> Boolean
) {

    suspend fun generateDigest(startDate: Long, endDate: Long, selectedThemes: List<String>): PeriodDigest? {
        return withContext(Dispatchers.IO) {
            val dao = db.diaryDao()
            
            val duration = endDate - startDate
            val priorPeriodStart = startDate - duration
            val priorPeriodEnd = startDate - 1

            val thisPeriodEntriesRaw = dao.getEntriesInRangeSnapshot(startDate, endDate)
            val priorPeriodEntriesRaw = dao.getEntriesInRangeSnapshot(priorPeriodStart, priorPeriodEnd)

            if (thisPeriodEntriesRaw.isEmpty()) {
                return@withContext null // No digest if no entries in period
            }

            val thisPeriodEntries = thisPeriodEntriesRaw.map { securityManager.decryptEntry(it) }
            val priorPeriodEntries = priorPeriodEntriesRaw.map { securityManager.decryptEntry(it) }

            // 1. Entry Count and Active Days
            val entryCount = thisPeriodEntries.size
            val activeDays = calculateActiveDays(thisPeriodEntries)

            // 2. Structural Metrics
            val thisPeriodMetrics = calculateMetrics(thisPeriodEntries)
            val priorPeriodMetrics = if (priorPeriodEntries.isNotEmpty()) calculateMetrics(priorPeriodEntries) else null

            // 3. Dominant Themes and Vibes (only if engine is active)
            val dominantThemes: List<String>
            val thisPeriodVibe: Float?
            val priorPeriodVibe: Float?

            if (isEngineActive()) {
                dominantThemes = calculateDominantThemes(thisPeriodEntriesRaw.map { it.id }, selectedThemes)
                thisPeriodVibe = calculateAverageVibe(thisPeriodEntriesRaw.map { it.id })
                priorPeriodVibe = calculateAverageVibe(priorPeriodEntriesRaw.map { it.id })
            } else {
                dominantThemes = emptyList()
                thisPeriodVibe = null
                priorPeriodVibe = null
            }

            val thisPeriodManualMood = calculateAverageManualMood(thisPeriodEntriesRaw)
            val priorPeriodManualMood = calculateAverageManualMood(priorPeriodEntriesRaw)

            PeriodDigest(
                periodStart = startDate,
                periodEnd = endDate,
                entryCount = entryCount,
                activeDays = activeDays,
                dominantThemes = dominantThemes,
                avgEntryLength = thisPeriodMetrics.avgLength,
                avgEntryLengthPrior = priorPeriodMetrics?.avgLength,
                vocabularyDiversity = thisPeriodMetrics.vocabDiversity,
                vocabularyDiversityPrior = priorPeriodMetrics?.vocabDiversity,
                questionRatio = thisPeriodMetrics.questionRatio,
                questionRatioPrior = priorPeriodMetrics?.questionRatio,
                avgVibeScore = thisPeriodVibe,
                avgVibeScorePrior = priorPeriodVibe,
                avgManualMood = thisPeriodManualMood,
                avgManualMoodPrior = priorPeriodManualMood
            )
        }
    }

    private fun calculateActiveDays(entries: List<DiaryEntry>): Int {
        val calendar = Calendar.getInstance()
        return entries.map { 
            calendar.timeInMillis = it.dateTime
            val year = calendar.get(Calendar.YEAR)
            val day = calendar.get(Calendar.DAY_OF_YEAR)
            "$year-$day"
        }.distinct().size
    }

    private data class StructuralMetrics(
        val avgLength: Float,
        val vocabDiversity: Float,
        val questionRatio: Float
    )

    private fun calculateMetrics(entries: List<DiaryEntry>): StructuralMetrics {
        var totalWords = 0
        var totalUniqueWords = 0
        var totalSentences = 0
        var totalQuestions = 0
        var entriesWithContent = 0

        for (entry in entries) {
            val content = entry.content.trim()
            if (content.isEmpty()) continue

            val words = content.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty()) continue

            totalWords += words.size
            totalUniqueWords += words.distinct().size
            entriesWithContent++

            val sentences = content.split(Regex("[.!?]+")).filter { it.isNotBlank() }
            totalSentences += sentences.size
            totalQuestions += content.count { it == '?' }
        }

        if (entriesWithContent == 0) return StructuralMetrics(0f, 0f, 0f)

        val avgLength = totalWords.toFloat() / entriesWithContent.toFloat()
        val vocabDiversity = totalUniqueWords.toFloat() / totalWords.toFloat()
        val questionRatio = if (totalSentences > 0) totalQuestions.toFloat() / totalSentences.toFloat() else 0f

        return StructuralMetrics(avgLength, vocabDiversity, questionRatio)
    }

    private fun calculateDominantThemes(entryIds: List<Long>, selectedThemes: List<String>): List<String> {
        val entryIdSet = entryIds.toSet()
        val vectorBox = ObjectBoxManager.vectorBox
        
        val themeCounts = mutableMapOf<String, Int>()

        for (id in entryIdSet) {
            val vObj = vectorBox.query(ObjectBoxSentenceVector_.entryId.equal(id)).build().findFirst()
            val theme = vObj?.semanticTheme
            if (theme != null && selectedThemes.contains(theme)) {
                themeCounts[theme] = themeCounts.getOrDefault(theme, 0) + 1
            }
        }

        // Return up to 3 themes sorted by the number of entries matching them
        return themeCounts.entries.sortedByDescending { it.value }.take(3).map { it.key }
    }

    private fun calculateAverageVibe(entryIds: List<Long>): Float? {
        if (entryIds.isEmpty()) return null
        var totalVibe = 0f
        var count = 0

        val vectorBox = ObjectBoxManager.vectorBox
        for (id in entryIds) {
            val vObj = vectorBox.query(ObjectBoxSentenceVector_.entryId.equal(id)).build().findFirst()
            val scoreJson = vObj?.vibeScoresJson
            if (scoreJson != null) {
                try {
                    val json = org.json.JSONObject(scoreJson)
                    val axisObj = json.optJSONObject("Positivity vs Negativity")
                    totalVibe += axisObj?.optDouble("score", 0.0)?.toFloat()
                        ?: json.optDouble("Positivity vs Negativity", 0.0).toFloat()
                    count++
                } catch (_: Exception) {}
            }
        }

        return if (count > 0) totalVibe / count else null
    }

    private fun calculateAverageManualMood(entries: List<DiaryEntry>): Float? {
        if (entries.isEmpty()) return null
        var totalMood = 0f
        var count = 0
        for (entry in entries) {
            totalMood += entry.numericMood.toFloat()
            count++
        }
        return if (count > 0) totalMood / count else null
    }
}
