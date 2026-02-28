package com.laniakea.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.laniakea.data.AppSettings
import com.laniakea.data.DiaryDatabase
import com.laniakea.data.DiaryEntry
import com.laniakea.data.SentenceVector
import com.laniakea.engine.SentenceEmbedder
import com.laniakea.engine.VibeEngine
import com.laniakea.security.SecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

class LaniakeaViewModel(application: Application) : AndroidViewModel(application) {
    private val db = DiaryDatabase.getDatabase(application)
    private val embedder = SentenceEmbedder(application)
    private val securityManager = SecurityManager(application)

    // UI State
    var manualMomentum by mutableStateOf(Triple(0f, "STABLE", emptyList<Float>()))
    var aiMomentum by mutableStateOf(Triple(0f, "STABLE", emptyList<Float>()))
    var isImporting by mutableStateOf(false)
    var importProgress by mutableStateOf(0 to 0)

    var isEngineActive by mutableStateOf(false)
    var isEngineLoading by mutableStateOf(false)
    var autoLoadEnabled by mutableStateOf(false)
    var vibeYear by mutableStateOf("2025")

    // Processing State
    var totalEntries by mutableIntStateOf(0)
    var unprocessedCount by mutableIntStateOf(0)
    var isProcessing by mutableStateOf(false)

    init {
        loadSettings()
        observeEngineStatus()
        refreshData()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val settings = db.diaryDao().getSettings()
            autoLoadEnabled = settings?.autoLoadEngine ?: false
            if (autoLoadEnabled) {
                initializeEngine()
            }
        }
    }

    private fun observeEngineStatus() {
        viewModelScope.launch {
            embedder.ready.collect { isReady ->
                isEngineActive = isReady
                isEngineLoading = false
                if (isReady) {
                    // Set default anchors first
                    VibeEngine.joyAnchor = embedder.embed("I feel incredibly happy, fulfilled, and optimistic.")
                    VibeEngine.distressAnchor = embedder.embed("I feel miserable, exhausted, and hopeless.")

                    // Attempt personalization
                    withContext(Dispatchers.IO) { calibrateAnchors() }
                    refreshData()
                }
            }
        }
    }

    private suspend fun calibrateAnchors() {
        val dao = db.diaryDao()
        // Limit to most recent 20 entries per mood for performance and relevance
        val joyVectors = dao.getRecentVectorsByNumericMood(2.0, 20)    // "Awesome"
        val sadVectors = dao.getRecentVectorsByNumericMood(-2.0, 20)   // "Terrible"

        // Only calibrate if we have enough personal data for a stable mean
        if (joyVectors.size >= 5 && sadVectors.size >= 5) {
            val avgJoy = calculateAverageVector(joyVectors.map { dao.byteArrayToFloatArray(it) })
            val avgSad = calculateAverageVector(sadVectors.map { dao.byteArrayToFloatArray(it) })

            VibeEngine.joyAnchor = avgJoy
            VibeEngine.distressAnchor = avgSad
        }
    }

    private fun calculateAverageVector(vectors: List<FloatArray>): FloatArray {
        if (vectors.isEmpty()) return FloatArray(0)
        val size = vectors[0].size
        val avg = FloatArray(size)
        for (vector in vectors) {
            for (i in 0 until size) {
                avg[i] += vector[i]
            }
        }
        for (i in 0 until size) {
            avg[i] /= vectors.size.toFloat()
        }
        return avg
    }

    fun initializeEngine() {
        if (isEngineActive || isEngineLoading) return
        isEngineLoading = true
        embedder.initialize()
    }

    fun toggleAutoLoad(enabled: Boolean) {
        autoLoadEnabled = enabled
        viewModelScope.launch {
            db.diaryDao().saveSettings(AppSettings(autoLoadEngine = enabled))
        }
    }

    fun addDiaryEntry(content: String, mood: String, numericMood: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val encryptedContent = securityManager.encrypt(content)
            val encryptedMood = securityManager.encrypt(mood)

            if (isEngineActive) {
                val vector = embedder.embed(content)
                if (vector != null) {
                    val aiVibe = VibeEngine.calculateVibeScore(vector)
                    val entry = DiaryEntry(
                        dateTime = System.currentTimeMillis(),
                        content = encryptedContent,
                        mood = encryptedMood,
                        numericMood = numericMood,
                        latentVibe = aiVibe.toDouble(),
                        isVectorized = true
                    )
                    db.diaryDao().insertEntryWithVector(entry, vector)
                    refreshData()
                    return@launch
                }
            }

            // Fallback: save without vectorization if engine is not active or embedding fails
            val entry = DiaryEntry(
                dateTime = System.currentTimeMillis(),
                content = encryptedContent,
                mood = encryptedMood,
                numericMood = numericMood,
                latentVibe = 0.0,
                isVectorized = false
            )
            db.diaryDao().insertEntry(entry)
            refreshData()
        }
    }

    fun processMissingEntries() {
        if (isProcessing || !isEngineActive) return
        
        viewModelScope.launch(Dispatchers.IO) {
            isProcessing = true
            val dao = db.diaryDao()
            val missing = dao.getUnprocessedEntries()
            
            missing.forEach { entry ->
                try {
                    val decryptedContent = securityManager.decrypt(entry.content)
                    val vector = embedder.embed(decryptedContent)
                    
                    if (vector != null) {
                        val aiVibe = VibeEngine.calculateVibeScore(vector)
                        val updatedEntry = entry.copy(
                            latentVibe = aiVibe.toDouble(),
                            isVectorized = true
                        )
                        dao.updateEntry(updatedEntry)
                        dao.insertVector(SentenceVector(
                            entryId = entry.id,
                            vector = dao.floatArrayToByteArray(vector)
                        ))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                refreshProcessingStats()
            }
            isProcessing = false
            refreshData()
        }
    }

    private suspend fun refreshProcessingStats() {
        val dao = db.diaryDao()
        val total = dao.getTotalEntriesCount()
        val unprocessed = dao.getUnprocessedEntriesCount()
        withContext(Dispatchers.Main) {
            totalEntries = total
            unprocessedCount = unprocessed
        }
    }

    fun refreshData() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshProcessingStats()
            val dao = db.diaryDao()
            
            // Recalibrate anchors if possible before calculating scores
            if (isEngineActive) {
                calibrateAnchors()
            }

            // Optimization: Only fetch the scores, not the full encrypted content/blobs
            val scores = dao.getAllMoodScores()
            val manualValues = scores.map { it.numericMood.toFloat() }
            val aiValues = scores.map { it.latentVibe.toFloat() }

            val oldest = dao.getOldestTimestamp()
            val year = if (oldest != null) {
                val cal = Calendar.getInstance()
                cal.timeInMillis = oldest
                cal.get(Calendar.YEAR).toString()
            } else LocalDate.now().year.toString()

            withContext(Dispatchers.Main) {
                manualMomentum = calculateMomentum(manualValues)
                aiMomentum = calculateMomentum(aiValues)
                vibeYear = year
            }
        }
    }

    fun calculateMomentum(values: List<Float>, span: Int = 7): Triple<Float, String, List<Float>> {
        if (values.size < 2) return Triple(0f, "STABLE", emptyList())

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

                    val timestamp = try {
                        dateFormatter.parse(dateString)?.time ?: System.currentTimeMillis()
                    } catch (_: Exception) {
                        System.currentTimeMillis()
                    }

                    val vector = embedder.embed(content)

                    if (vector != null) {
                        val encryptedContent = securityManager.encrypt(content)
                        val encryptedMood = securityManager.encrypt(mood)

                        val numericMood = when (mood.trim()) {
                            "Awesome" -> 2.0
                            "Good" -> 1.0
                            "Fine" -> 0.0
                            "Bad" -> -1.0
                            "Terrible" -> -2.0
                            else -> 0.0
                        }
                        
                        // We use default anchors for initial import calculation
                        // or recalibrate after a batch
                        val aiVibe = VibeEngine.calculateVibeScore(vector)
                        val entry = DiaryEntry(
                            dateTime = timestamp,
                            content = encryptedContent,
                            mood = encryptedMood,
                            numericMood = numericMood,
                            latentVibe = aiVibe.toDouble(),
                            isVectorized = true
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