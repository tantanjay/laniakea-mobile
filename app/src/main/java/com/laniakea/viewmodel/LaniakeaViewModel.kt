package com.laniakea.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.laniakea.data.DiaryDatabase
import com.laniakea.data.DiaryEntry
import com.laniakea.engine.SentenceEmbedder
import com.laniakea.engine.VibeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Locale

// This class handles all logic, making the UI "dumb" and lean
class LaniakeaViewModel(application: Application) : AndroidViewModel(application) {
    private val db = DiaryDatabase.getDatabase(application)
    private val embedder = SentenceEmbedder(application)

    // UI State
    var manualMomentum by mutableStateOf(Triple(0f, "STABLE", emptyList<Float>()))
    var aiMomentum by mutableStateOf(Triple(0f, "STABLE", emptyList<Float>()))
    var isImporting by mutableStateOf(false)
    var importProgress by mutableStateOf(0 to 0)

    init {
        initializeEngine()
    }

    private fun initializeEngine() {
        viewModelScope.launch {
            embedder.ready.collect { isReady ->
                if (isReady) {
                    VibeEngine.joyAnchor = embedder.embed("I feel incredibly happy, fulfilled, and optimistic.")
                    VibeEngine.distressAnchor = embedder.embed("I feel miserable, exhausted, and hopeless.")
                    refreshData()
                }
            }
        }
    }

    fun refreshData() {
        viewModelScope.launch(Dispatchers.IO) {
            val entries = db.diaryDao().getAllEntries()
            val manualValues = entries.map { it.numericMood.toFloat() }
            val aiValues = entries.map { it.latentVibe.toFloat() }

            withContext(Dispatchers.Main) {
                manualMomentum = calculateMomentum(manualValues)
                aiMomentum = calculateMomentum(aiValues)
            }
        }
    }

    fun calculateMomentum(values: List<Float>, span: Int = 7): Triple<Float, String, List<Float>> {
        if (values.size < 2) return Triple(0f, "STABLE", emptyList())

        // It doesn't care if these values are Manual labels or AI vibes anymore
        val deltas = mutableListOf(0f)
        for (i in 1 until values.size) {
            deltas.add(values[i] - values[i - 1])
        }

        val alpha = 2f / (span + 1f)
        val trend = mutableListOf<Float>()
        var ema = deltas[0]
        trend.add(ema)

        for (i in 1 until deltas.size) {
            ema = (deltas[i] * alpha) + (ema * (1f - alpha))
            trend.add(ema)
        }

        val score = (ema * 100f).coerceIn(-100f, 100f)
        val status = listOf(
            -20f to "SHARP DECLINE",
            -5f to "DECLINING",
            5f to "STABLE",
            20f to "IMPROVING",
            101f to "STRONG UPTURN"
        ).first { score < it.first }.second

        return Triple(score, status, trend)
    }

    fun importDummyData() {
        viewModelScope.launch {
            isImporting = true
            withContext(Dispatchers.IO) {
                val dao = db.diaryDao()
                dao.clearDatabase()
                val lines = getApplication<Application>().assets.open("dummy.csv").bufferedReader().use { it.readLines() }
                val dataLines = lines.filter { it.isNotBlank() && !it.startsWith("date,") }
                val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                dataLines.forEachIndexed { index, line ->
                    val parts = line.split(",")
                    val (dateString, content, mood) = when {
                        line.contains("\"") -> {
                            val date = line.substringBefore(",")
                            val m = line.substringAfterLast(",").trim()
                            val c = line.substringAfter(",").substringBeforeLast(",").trim().removeSurrounding("\"")
                            Triple(date, c, m)
                        }
                        else -> Triple(parts[0].trim(), parts[1].trim(), parts[2].trim())
                    }

                    // 2. Convert "2025-01-01" to Long timestamp
                    val timestamp = try {
                        dateFormatter.parse(dateString)?.time ?: System.currentTimeMillis()
                    } catch (_: Exception) {
                        System.currentTimeMillis()
                    }

                    val vector = suspendCancellableCoroutine { continuation ->
                        embedder.embedAsync(content) { result -> continuation.resume(result) }
                    }

                    if (vector != null) {
                        val numericMood = when (mood.trim()) {
                            "Awesome" -> 2.0
                            "Good" -> 1.0
                            "Fine" -> 0.0
                            "Bad" -> -1.0
                            "Terrible" -> -2.0
                            else -> 0.0
                        }
                        val aiVibe = VibeEngine.calculateVibeScore(vector)
                        val entry = DiaryEntry(
                            dateTime = timestamp,
                            content = content,
                            mood = mood,
                            numericMood = numericMood,
                            latentVibe = aiVibe.toDouble()
                        )
                        dao.insertEntryWithVector(entry, vector)
                    }

                    withContext(Dispatchers.Main) {
                        importProgress = (index + 1) to dataLines.size
                    }
                }
            }
            refreshData()
            isImporting = false
        }
    }
}