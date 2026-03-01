package com.laniakea.viewmodel

import android.app.Application
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
import com.laniakea.data.TaglineTemplates
import com.laniakea.engine.SentenceEmbedder
import com.laniakea.engine.VibeEngine
import com.laniakea.security.SecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

class LaniakeaViewModel(application: Application) : AndroidViewModel(application) {
    private val db = DiaryDatabase.getDatabase(application)
    private val securityManager = SecurityManager(application)
    private val embedder = SentenceEmbedder(application, db, securityManager)

    // UI State
    var userName by mutableStateOf("Traveller")
    var theme by mutableStateOf("PURPLE")
    var manualMomentum by mutableStateOf(Triple(0f, "STABLE", emptyList<Float>()))
    var aiMomentum by mutableStateOf(Triple(0f, "STABLE", emptyList<Float>()))
    var isImporting by mutableStateOf(false)
    var importProgress by mutableStateOf(0 to 0)

    var isEngineActive by mutableStateOf(false)
    var isEngineLoading by mutableStateOf(false)
    var autoLoadEnabled by mutableStateOf(false)
    var vibeYear by mutableStateOf("2025")
    var tagline by mutableStateOf("Analyzing your cosmic vibes...")

    // Processing State
    var totalEntries by mutableIntStateOf(0)
    var unprocessedCount by mutableIntStateOf(0)
    var isProcessing by mutableStateOf(false)

    init {
        observeSettings()
        observeEngineStatus()
        refreshData()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            db.diaryDao().getSettingsFlow().collectLatest { settings ->
                settings?.let {
                    autoLoadEnabled = it.autoLoadEngine
                    userName = it.userName.takeIf { name -> name.isNotEmpty() } ?: "Traveller"
                    theme = it.theme

                    if (autoLoadEnabled && !isEngineActive && !isEngineLoading) {
                        initializeEngine()
                    }
                }
            }
        }
    }

    private fun observeEngineStatus() {
        viewModelScope.launch {
            embedder.ready.collect { isReady ->
                isEngineActive = isReady
                isEngineLoading = false
                if (isReady) {
                    VibeEngine.joyAnchor = embedder.embed("I feel incredibly happy, fulfilled, and optimistic.")
                    VibeEngine.distressAnchor = embedder.embed("I feel miserable, exhausted, and hopeless.")
                    withContext(Dispatchers.IO) { calibrateAnchors() }
                    refreshData()
                }
            }
        }
    }

    private suspend fun calibrateAnchors() {
        val dao = db.diaryDao()
        val joyVectors = dao.getRecentVectorsByNumericMood(2.0, 20)
        val sadVectors = dao.getRecentVectorsByNumericMood(-2.0, 20)

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
        viewModelScope.launch {
            val currentSettings = db.diaryDao().getSettings() ?: AppSettings()
            db.diaryDao().saveSettings(currentSettings.copy(autoLoadEngine = enabled))
        }
    }

    fun updateTheme(newTheme: String) {
        viewModelScope.launch {
            val currentSettings = db.diaryDao().getSettings() ?: AppSettings()
            db.diaryDao().saveSettings(currentSettings.copy(theme = newTheme))
        }
    }

    fun addDiaryEntry(
        content: String,
        mood: String,
        numericMood: Double,
        category: String = "Home",
        weather: String = "Sunny",
        activities: String = ""
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val encryptedContent = securityManager.encrypt(content)
            val encryptedMood = securityManager.encrypt(mood)
            val encryptedCategory = securityManager.encrypt(category)
            val encryptedWeather = securityManager.encrypt(weather)
            val encryptedActivities = securityManager.encrypt(activities)

            if (isEngineActive) {
                val vector = embedder.embed(content)
                if (vector != null) {
                    val aiVibe = VibeEngine.calculateVibeScore(vector)
                    val entry = DiaryEntry(
                        dateTime = System.currentTimeMillis(),
                        content = encryptedContent,
                        mood = encryptedMood,
                        category = encryptedCategory,
                        weather = encryptedWeather,
                        activities = encryptedActivities,
                        numericMood = numericMood,
                        latentVibe = aiVibe.toDouble(),
                        isVectorized = true
                    )
                    db.diaryDao().insertEntryWithVector(entry, vector)
                    refreshData()
                    return@launch
                }
            }

            val entry = DiaryEntry(
                dateTime = System.currentTimeMillis(),
                content = encryptedContent,
                mood = encryptedMood,
                category = encryptedCategory,
                weather = encryptedWeather,
                activities = encryptedActivities,
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
                        val updatedEntry = entry.copy(latentVibe = aiVibe.toDouble(), isVectorized = true)
                        dao.updateEntry(updatedEntry)
                        dao.insertVector(SentenceVector(entryId = entry.id, vector = dao.floatArrayToByteArray(vector)))
                    }
                } catch (e: Exception) { e.printStackTrace() }
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
            if (isEngineActive) calibrateAnchors()

            val scores = dao.getAllMoodScores()
            scores.forEach { it.numericMood.toFloat() }
            scores.forEach { it.latentVibe.toFloat() }

            // --- DAILY AGGREGATE for stable trend ---
            val manualDaily = aggregateByDay(scores.map { it.dateTime to it.numericMood.toFloat() })
            val aiDaily = aggregateByDay(scores.map { it.dateTime to it.latentVibe.toFloat() })

            val oldest = dao.getOldestTimestamp()
            val year = if (oldest != null) {
                val cal = Calendar.getInstance()
                cal.timeInMillis = oldest
                cal.get(Calendar.YEAR).toString()
            } else LocalDate.now().year.toString()

            withContext(Dispatchers.Main) {
                val manual = calculateMomentum(manualDaily, 14)
                val ai = calculateMomentum(aiDaily, 14)

                // Update ViewModel state
                manualMomentum = Triple(
                    manual.first,
                    manual.second,
                    manual.third.takeLast(30)
                )

                aiMomentum = Triple(
                    ai.first,
                    ai.second,
                    ai.third.takeLast(30)
                )

                vibeYear = year
                if (tagline == "Analyzing your cosmic vibes...") {
                    generateTagline(year)
                }
            }
        }
    }

    /** Aggregate multiple entries per day to average value */
    private fun aggregateByDay(values: List<Pair<Long, Float>>): List<Float> {
        val cal = Calendar.getInstance()
        return values.groupBy { (timestamp, _) ->
            cal.timeInMillis = timestamp
            "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
        }.map { (_, dayValues) ->
            dayValues.map { it.second }.average().toFloat()
        }
    }

    private fun generateTagline(year: String) {
        tagline = TaglineTemplates.ALL.random()(year)
    }

    /**
     * Computes a robust momentum score for a series of emotional/mood values.
     *
     * This function is designed for general-purpose emotional tracking. It balances
     * responsiveness to recent trends with stability, reducing the impact of
     * single outlier entries (e.g., an unusually bad or good mood) while still
     * capturing sustained trends.
     *
     * Key features:
     * 1. Computes deltas between consecutive mood entries.
     * 2. Uses median and MAD (Median Absolute Deviation) to suppress extreme outliers.
     * 3. Normalizes deltas based on volatility to handle high-variance periods.
     * 4. Applies EMA (Exponential Moving Average) for trend smoothing.
     * 5. Produces a score in the range -100..100 and a qualitative status label.
     */
    fun calculateMomentum(
        values: List<Float>,
        span: Int = 7,
        outlierMultiplier: Float = 3f,
        statusMap: Map<Float, String> = mapOf(
            -20f to "SHARP DECLINE",
            -5f to "DECLINING",
            5f to "STABLE",
            20f to "IMPROVING",
            Float.MAX_VALUE to "STRONG UPTURN"
        )
    ): Triple<Float, String, List<Float>> {

        if (values.size < 2) return Triple(0f, "STABLE", emptyList())

        // 1. Compute deltas
        val deltas = mutableListOf(0f)
        for (i in 1 until values.size) deltas.add(values[i] - values[i - 1])

        // 2. Compute median and MAD for outlier detection
        val sortedDeltas = deltas.sorted()
        val median = sortedDeltas[sortedDeltas.size / 2]
        val mad = sortedDeltas.map { kotlin.math.abs(it - median) }.sorted()[sortedDeltas.size / 2].coerceAtLeast(0.001f)

        // 3. Outlier suppression: reduce influence of extreme deltas
        val filteredDeltas = deltas.map { delta ->
            val deviation = delta - median
            if (kotlin.math.abs(deviation) > outlierMultiplier * mad) {
                median + deviation.coerceIn(-1.5f * mad, 1.5f * mad)
            } else delta
        }

        // 4. Optional: volatility normalization
        val volatility = filteredDeltas.maxOrNull()!! - filteredDeltas.minOrNull()!!
        val normalizedDeltas = if (volatility > 0f) {
            filteredDeltas.map { it / volatility }  // scale -1..1
        } else filteredDeltas

        // 5. EMA smoothing
        val alpha = 2f / (span + 1f)
        val trend = mutableListOf<Float>()
        var ema = normalizedDeltas[0]
        trend.add(ema)
        for (i in 1 until normalizedDeltas.size) {
            ema = normalizedDeltas[i] * alpha + ema * (1f - alpha)
            trend.add(ema)
        }

        // 6. Map EMA to -100..100 score
        val score = (ema * 100f).coerceIn(-100f, 100f)

        // 7. Status mapping
        val status = statusMap.entries
            .sortedBy { it.key }
            .firstOrNull { score < it.key }?.value ?: "UNKNOWN"

        return Triple(score, status, trend)
    }

    fun importDummyData() {
        viewModelScope.launch {
            isImporting = true
            withContext(Dispatchers.IO) {
                val dao = db.diaryDao()
                dao.clearDatabase()
                val lines = getApplication<Application>().assets.open("real.csv").bufferedReader().use { it.readLines() }
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
                    val timestamp = runCatching { dateFormatter.parse(dateString)?.time ?: System.currentTimeMillis() }.getOrDefault(System.currentTimeMillis())
                    val vector = embedder.embed(content)
                    if (vector != null) {
                        val encryptedContent = securityManager.encrypt(content)
                        val encryptedMood = securityManager.encrypt(mood)
                        val numericMood = when (mood.trim()) {
                            "Awesome" -> 2.0; "Good" -> 1.0; "Fine" -> 0.0; "Bad" -> -1.0; "Terrible" -> -2.0; else -> 0.0
                        }
                        val aiVibe = VibeEngine.calculateVibeScore(vector)
                        val entry = DiaryEntry(dateTime = timestamp, content = encryptedContent, mood = encryptedMood, numericMood = numericMood, latentVibe = aiVibe.toDouble(), isVectorized = true)
                        dao.insertEntryWithVector(entry, vector)
                    }
                    withContext(Dispatchers.Main) { importProgress = (index + 1) to dataLines.size }
                }
            }
            refreshData()
            isImporting = false
        }
    }
}
