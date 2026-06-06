package com.laniakea.manager

import com.laniakea.data.DiaryDatabase
import com.laniakea.data.DiaryEntry
import com.laniakea.data.ObjectBoxManager
import com.laniakea.data.WeeklyDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Calendar

class WeeklyDigestManager(
    private val db: DiaryDatabase,
    private val securityManager: SecurityManager,
    private val semanticManager: SemanticManager,
    private val isEngineActive: () -> Boolean
) {

    suspend fun generateDigest(): WeeklyDigest? {
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

            // 3. Dominant Themes (only if engine is active)
            val dominantThemes = if (isEngineActive()) {
                calculateDominantThemes(thisWeekEntriesRaw.map { it.id })
            } else {
                emptyList()
            }

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
                questionRatioPrior = priorWeekMetrics?.questionRatio
            )
        }
    }

    private fun calculateActiveDays(entries: List<DiaryEntry>): Int {
        val cal = Calendar.getInstance()
        return entries.map {
            cal.timeInMillis = it.dateTime
            "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
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
            val content = entry.content
            if (content.isBlank() || content == "[Encrypted]") continue
            
            val words = content.lowercase().split(Regex("[\\s,.!?;:()\"]+")).filter { it.isNotBlank() }
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

    private suspend fun calculateDominantThemes(entryIds: List<Long>): List<String> {
        val themeVectors = semanticManager.getCachedThemes() ?: return emptyList()
        val entryIdSet = entryIds.toSet()
        
        // Fetch all vectors for this week's entries
        val vectorBox = ObjectBoxManager.vectorBox
        val relevantVectors = mutableListOf<FloatArray>()
        for (id in entryIdSet) {
            val vObj = vectorBox.query(com.laniakea.data.ObjectBoxSentenceVector_.entryId.equal(id)).build().findFirst()
            if (vObj != null) {
                vObj.vector?.let { relevantVectors.add(it) }
            }
        }
        
        if (relevantVectors.isEmpty()) return emptyList()

        val themeCounts = mutableMapOf<String, Int>()

        // Forward map each entry vector to its single closest theme
        for (vector in relevantVectors) {
            var bestTheme = ""
            var minDistance = Double.MAX_VALUE

            for ((themeName, themeVector) in themeVectors) {
                val distance = semanticManager.calculateL2Distance(vector, themeVector)
                if (distance < minDistance) {
                    minDistance = distance
                    bestTheme = themeName
                }
            }

            // Only count if it's genuinely close to the theme
            if (minDistance < SemanticManager.MAX_DISTANCE_THEME) {
                themeCounts[bestTheme] = themeCounts.getOrDefault(bestTheme, 0) + 1
            }
        }

        // Return up to 3 themes sorted by the number of entries matching them
        return themeCounts.entries.sortedByDescending { it.value }.take(3).map { it.key }
    }
}
