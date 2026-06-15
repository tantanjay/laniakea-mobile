package com.laniakea.manager

import com.laniakea.data.DiaryDatabase
import com.laniakea.data.DiaryEntry
import com.laniakea.data.ObjectBoxManager
import com.laniakea.data.ObjectBoxSentenceVector_
import com.laniakea.data.WeeklyDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Calendar

class WeeklyDigestManager(
    private val db: DiaryDatabase,
    private val securityManager: SecurityManager,
    private val isEngineActive: () -> Boolean
) {

    suspend fun generateDigest(selectedThemes: List<String>): WeeklyDigest? {
        return withContext(Dispatchers.IO) {
            val dao = db.diaryDao()
            
            // Anchor the week to the most recent entry, falling back to now if DB is empty
            val mostRecentEntry = dao.getRecentEntries(1).firstOrNull() ?: return@withContext null
            val anchorTimeMillis = mostRecentEntry.dateTime
            
            val endInstant = Instant.ofEpochMilli(anchorTimeMillis)
            val weekStart = endInstant.minus(7, ChronoUnit.DAYS).toEpochMilli()
            val priorWeekStart = endInstant.minus(14, ChronoUnit.DAYS).toEpochMilli()

            val thisWeekEntriesRaw = dao.getEntriesInRangeSnapshot(weekStart, anchorTimeMillis)
            val priorWeekEntriesRaw = dao.getEntriesInRangeSnapshot(priorWeekStart, weekStart - 1)

            if (thisWeekEntriesRaw.isEmpty()) {
                return@withContext null // No digest if no entries this week
            }

            val thisWeekEntries = thisWeekEntriesRaw.map { securityManager.decryptEntry(it) }
            val priorWeekEntries = priorWeekEntriesRaw.map { securityManager.decryptEntry(it) }

            // 1. Entry Count and Active Days
            val entryCount = thisWeekEntries.size
            val activeDays = calculateActiveDays(thisWeekEntries)

            // 2. Structural Metrics
            val thisWeekMetrics = calculateMetrics(thisWeekEntries)
            val priorWeekMetrics = if (priorWeekEntries.isNotEmpty()) calculateMetrics(priorWeekEntries) else null

            // 3. Dominant Themes and Vibes (only if engine is active)
            val dominantThemes: List<String>
            val thisWeekVibe: Float?
            val priorWeekVibe: Float?

            if (isEngineActive()) {
                dominantThemes = calculateDominantThemes(thisWeekEntriesRaw.map { it.id }, selectedThemes)
                thisWeekVibe = calculateAverageVibe(thisWeekEntriesRaw.map { it.id })
                priorWeekVibe = calculateAverageVibe(priorWeekEntriesRaw.map { it.id })
            } else {
                dominantThemes = emptyList()
                thisWeekVibe = null
                priorWeekVibe = null
            }

            val thisWeekManualMood = calculateAverageManualMood(thisWeekEntriesRaw)
            val priorWeekManualMood = calculateAverageManualMood(priorWeekEntriesRaw)

            WeeklyDigest(
                weekStart = weekStart,
                weekEnd = anchorTimeMillis,
                entryCount = entryCount,
                activeDays = activeDays,
                dominantThemes = dominantThemes,
                avgEntryLength = thisWeekMetrics.avgLength,
                avgEntryLengthPrior = priorWeekMetrics?.avgLength,
                vocabularyDiversity = thisWeekMetrics.vocabDiversity,
                vocabularyDiversityPrior = priorWeekMetrics?.vocabDiversity,
                questionRatio = thisWeekMetrics.questionRatio,
                questionRatioPrior = priorWeekMetrics?.questionRatio,
                avgVibeScore = thisWeekVibe,
                avgVibeScorePrior = priorWeekVibe,
                avgManualMood = thisWeekManualMood,
                avgManualMoodPrior = priorWeekManualMood
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
