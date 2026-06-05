package com.laniakea.manager

import com.laniakea.data.DiaryDatabase
import com.laniakea.data.DiaryEntry
import com.laniakea.data.TaglineTemplates
import com.laniakea.security.SecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

data class WritingMetrics(
    val entryLengths: List<Pair<Long, Float>>,
    val vocabularyDiversity: List<Pair<Long, Float>>,
    val questionFrequency: List<Pair<Long, Float>>,
    val firstPersonUsage: List<Pair<Long, Float>>,
    val futureVsPast: List<Pair<Long, Float>>
)

class AnalyticsManager(
    private val db: DiaryDatabase,
    private val securityManager: SecurityManager
) {

    suspend fun analyzeWritingTrends(limit: Int = 30): WritingMetrics {
        return withContext(Dispatchers.IO) {
            val rawEntries = db.diaryDao().getRecentEntries(limit)
            // Reverse to chronological order (oldest first) for sparkline
            val entries = rawEntries.reversed().map { decryptEntry(it) }

            val firstPersonWords = setOf("i", "me", "my", "myself", "mine", "i'm", "i've", "i'll", "i'd")
            val futureWords = setOf("will", "going", "plan", "tomorrow", "soon", "next", "hope", "want", "intend", "goal", "ahead", "forward", "future")
            val pastWords = setOf("was", "had", "yesterday", "ago", "used", "before", "past", "remember", "remembered", "forgot", "back", "earlier", "then")

            val entryLengths = mutableListOf<Pair<Long, Float>>()
            val vocabularyDiversity = mutableListOf<Pair<Long, Float>>()
            val questionFrequency = mutableListOf<Pair<Long, Float>>()
            val firstPersonUsage = mutableListOf<Pair<Long, Float>>()
            val futureVsPast = mutableListOf<Pair<Long, Float>>()

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
            }

            WritingMetrics(
                entryLengths = entryLengths,
                vocabularyDiversity = vocabularyDiversity,
                questionFrequency = questionFrequency,
                firstPersonUsage = firstPersonUsage,
                futureVsPast = futureVsPast
            )
        }
    }

    fun aggregateByDay(values: List<Pair<Long, Float>>): List<Float> {
        val cal = Calendar.getInstance()
        return values.groupBy { (timestamp, _) ->
            cal.timeInMillis = timestamp
            "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
        }.map { (_, dayValues) ->
            dayValues.map { it.second }.average().toFloat()
        }
    }

    fun calculateMomentum(
        values: List<Float>,
        span: Int,
        outlierMultiplier: Float,
        statusMap: Map<Float, String>
    ): Triple<Float, String, List<Float>> {

        if (values.size < 2) return Triple(0f, "STABLE", emptyList())

        // 1️⃣ Compute deltas
        val deltas = mutableListOf(0f)
        for (i in 1 until values.size) deltas.add(values[i] - values[i - 1])

        // 2️⃣ Median and MAD for outlier detection
        val sortedDeltas = deltas.sorted()
        val median = sortedDeltas[sortedDeltas.size / 2]
        val mad = sortedDeltas.map { kotlin.math.abs(it - median) }
            .sorted()[sortedDeltas.size / 2]
            .coerceAtLeast(0.001f)

        // 3️⃣ Outlier suppression
        val filteredDeltas = deltas.map { delta ->
            val deviation = delta - median
            if (kotlin.math.abs(deviation) > outlierMultiplier * mad) {
                median + deviation.coerceIn(-1.5f * mad, 1.5f * mad)
            } else delta
        }

        // 4️⃣ Volatility normalization
        val sensitivityFloor = 1.0f // Unified for manual and AI (-2..2 scale)
        val rawVolatility = (filteredDeltas.maxOrNull() ?: 0f) - (filteredDeltas.minOrNull() ?: 0f)
        val volatility = rawVolatility.coerceAtLeast(sensitivityFloor)
        val normalizedDeltas = filteredDeltas.map { it / volatility }

        // 5️⃣ EMA smoothing
        val alpha = 2f / (span + 1f)
        val trend = mutableListOf<Float>()
        var ema = normalizedDeltas[0]
        trend.add(ema)
        for (i in 1 until normalizedDeltas.size) {
            ema = normalizedDeltas[i] * alpha + ema * (1f - alpha)
            trend.add(ema)
        }

        // 6️⃣ Map EMA to -100..100
        val score = (ema * 100f).coerceIn(-100f, 100f)

        // 7️⃣ Status mapping (pre-sorted for efficiency)
        val sortedStatusMap = statusMap.entries.sortedBy { it.key }
        val status = sortedStatusMap.firstOrNull { score < it.key }?.value ?: "UNKNOWN"

        return Triple(score, status, trend)
    }

    fun generateTagline(year: String): String {
        return TaglineTemplates.ALL.random()(year)
    }

    private fun decryptEntry(entry: DiaryEntry): DiaryEntry {
        return entry.copy(
            content = try { securityManager.decrypt(entry.content) } catch (e: Exception) { "[Encrypted]" },
            mood = try { securityManager.decrypt(entry.mood) } catch (e: Exception) { "" },
            category = try { securityManager.decrypt(entry.category) } catch (e: Exception) { "" },
            weather = try { securityManager.decrypt(entry.weather) } catch (e: Exception) { "" },
            activities = try { securityManager.decrypt(entry.activities) } catch (e: Exception) { "" }
        )
    }
}
