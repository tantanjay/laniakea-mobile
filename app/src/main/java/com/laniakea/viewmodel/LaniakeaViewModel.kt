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
    private val embedder = SentenceEmbedder(application)
    private val securityManager = SecurityManager(application)

    // UI State
    var userName by mutableStateOf("Traveller")
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
                val manual = calculateMomentum(manualDaily, 7)
                val ai = calculateMomentum(aiDaily, 7)

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
        val templates: List<(String) -> String> = listOf(
            // Calm & Reflective
            { y -> "Finding patterns in your entries since $y" },
            { y -> "Uncovering trends from your journal since $y" },
            { y -> "Connecting insights across your entries since $y" },
            { y -> "Learning from your 기록 since $y" },
            { y -> "Reading between the lines since $y" },
            { y -> "Quietly learning from your entries since $y" },
            { y -> "A quiet witness to your journey since $y" },
            { y -> "Holding space for your reflections since $y" },
            { y -> "Listening to the rhythm of your heart since $y" },
            { y -> "Your silent partner in reflection since $y" },
            { y -> "Tracing the threads of your story since $y" },
            { y -> "A mirror to your evolving self since $y" },
            { y -> "Capturing the essence of your days since $y" },
            
            // Witty & Funny
            { y -> "Making sense of your entries since $y" },
            { y -> "Knowing you better than your future self since $y" },
            { y -> "Overthinking your overthinking since $y" },
            { y -> "Your diary's favorite eavesdropper since $y" },
            { y -> "The only one who actually reads these since $y" },
            { y -> "Knowing exactly what you did last summer (and $y)" },
            { y -> "Decoding your cosmic chaos since $y" },
            { y -> "Keeping your secrets (mostly) since $y" },
            { y -> "Tracing patterns in your thoughts since $y" },
            { y -> "Analyzing your character arc since $y" },
            { y -> "Archiving your adventures and misadventures since $y" },
            { y -> "Your digital confidante (and occasional critic) since $y" },
            { y -> "Remembering what you forgot since $y" },
            
            // Playful & Cosmic
            { y -> "Stargazing through your memories since $y" },
            { y -> "Translating your late-night thoughts since $y" },
            { y -> "Sifting through your cosmic crumbs since $y" },
            { y -> "Dancing with your data since $y" },
            { y -> "Floating through your reflections since $y" },
            { y -> "Whispering to your inner universe since $y" },
            { y -> "Mapping your emotional nebula since $y" },
            { y -> "Charting your orbit through the emotional galaxy since $y" },
            { y -> "Chasing shooting stars in your journal since $y" },
            { y -> "Building a constellation out of your moods since $y" },
            { y -> "Observing how things evolve since $y" },
            { y -> "Navigating your inner labyrinth since $y" },
            { y -> "Seeing the magic in your mundane since $y" },
            { y -> "Your personal time capsule since $y" }
        )
        tagline = templates.random()(year)
    }

    /**
     * calculateMomentum
     *
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
     *
     * Parameters:
     * - values: List of mood/emotion values (Float). Can be any scale (0–10, -5–5, etc.).
     * - span: EMA span for smoothing. Larger span → smoother, slower-reacting momentum.
     * - outlierMultiplier: Multiplier for MAD to define what counts as an outlier.
     *
     * Returns:
     * - Triple<Float, String, List<Float>>:
     *      1. score: Smoothed momentum score (-100 to 100).
     *      2. status: Qualitative label ("STABLE", "IMPROVING", etc.).
     *      3. trend: List of EMA values representing the momentum trend over time.
     */
    fun calculateMomentum(
        values: List<Float>,
        span: Int = 21,
        outlierMultiplier: Float = 3f
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