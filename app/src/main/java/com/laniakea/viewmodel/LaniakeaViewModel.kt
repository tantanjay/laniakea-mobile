package com.laniakea.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Base64
import android.util.JsonReader
import android.util.JsonWriter
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
import com.laniakea.data.TaglineTemplates
import com.laniakea.engine.SentenceEmbedder
import com.laniakea.engine.VibeEngine
import com.laniakea.security.SecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Calendar

class LaniakeaViewModel(application: Application) : AndroidViewModel(application) {
    private val db = DiaryDatabase.getDatabase(application)
    private val securityManager = SecurityManager(application)
    private val embedder = SentenceEmbedder(application, db, securityManager)

    // UI State
    var userName by mutableStateOf("Traveller")
    var theme by mutableStateOf("PURPLE")
    var manualMomentum by mutableStateOf(Triple(0f, "STABLE", emptyList<Float>()))
    var aiMomentum by mutableStateOf(Triple(0f, "STABLE", emptyList<Float>()))
    
    // Vault states
    var isVaultRestoring by mutableStateOf(false)
    var isVaultBackingUp by mutableStateOf(false)
    var vaultProgress by mutableStateOf(0 to 0)

    var isEngineActive by mutableStateOf(false)
    var isEngineLoading by mutableStateOf(false)
    var autoLoadEnabled by mutableStateOf(false)
    var vibeYear by mutableStateOf("2025")
    var tagline by mutableStateOf("Analyzing your cosmic vibes...")

    // Processing State
    var totalEntries by mutableIntStateOf(0)
    var unprocessedCount by mutableIntStateOf(0)
    var isProcessing by mutableStateOf(false)

    // Journal Screen State
    private val _selectedDateRange = MutableStateFlow<Pair<Long, Long>?>(null)
    val selectedDateRange: StateFlow<Pair<Long, Long>?> = _selectedDateRange.asStateFlow()

    private val _viewingMonth = MutableStateFlow(YearMonth.now())
    val viewingMonth: StateFlow<YearMonth> = _viewingMonth.asStateFlow()

    val allEntries = db.diaryDao().getAllEntriesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredEntries = combine(_selectedDateRange, _viewingMonth) { range, month ->
        range ?: run {
            val start = month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val end = month.atEndOfMonth().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            start to end
        }
    }.flatMapLatest { (start, end) ->
        db.diaryDao().getEntriesInRange(start, end)
    }.map { entries ->
        entries.map { it.copy(
            content = try { securityManager.decrypt(it.content) } catch (_: Exception) { "[Encrypted]" },
            mood = try { securityManager.decrypt(it.mood) } catch (_: Exception) { "" },
            category = try { securityManager.decrypt(it.category) } catch (_: Exception) { "" },
            weather = try { securityManager.decrypt(it.weather) } catch (_: Exception) { "" },
            activities = try { securityManager.decrypt(it.activities) } catch (_: Exception) { "" }
        )}
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        observeSettings()
        observeEngineStatus()
        refreshData()
    }

    fun setSelectedDateRange(start: Long?, end: Long?) {
        if (start == null || end == null) {
            _selectedDateRange.value = null
        } else {
            _selectedDateRange.value = minOf(start, end) to maxOf(start, end)
        }
    }

    fun setViewingMonth(month: YearMonth) {
        _viewingMonth.value = month
        // Reset range selection when changing months to show the new month's entries
        _selectedDateRange.value = null
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

        // Ensure we don't blow up tiny changes into massive scores
        val sensitivityFloor = 5f // If the real volatility is smaller than 5, pretend it’s 5
        val rawVolatility = filteredDeltas.maxOrNull()!! - filteredDeltas.minOrNull()!!
        val volatility = if (rawVolatility < 0.001f) 1f else rawVolatility.coerceAtLeast(sensitivityFloor)
        val normalizedDeltas = filteredDeltas.map { it / volatility }

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

    fun exportDataStream(uri: Uri, password: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { 
                isVaultBackingUp = true
                vaultProgress = 0 to 0 
            }
            try {
                val context = getApplication<Application>()
                val outputStream = context.contentResolver.openOutputStream(uri) ?: throw Exception("Failed to open URI")
                
                val encryptedStream = securityManager.getEncryptingStream(outputStream, password.toCharArray())
                val writer = JsonWriter(OutputStreamWriter(encryptedStream, "UTF-8"))
                
                writer.beginObject()
                
                val dao = db.diaryDao()
                
                // Settings
                val settings = dao.getSettings()
                if (settings != null) {
                    writer.name("settings")
                    writer.beginObject()
                    writer.name("userName").value(settings.userName)
                    writer.name("theme").value(settings.theme)
                    settings.privacySeed?.let {
                        writer.name("privacySeed").value(securityManager.decrypt(it))
                    }
                    writer.endObject()
                }
                
                // Entries
                writer.name("entries")
                writer.beginArray()
                val entries = dao.getAllEntries()
                val totalCount = entries.size
                entries.forEachIndexed { index, entry ->
                    writer.beginObject()
                    writer.name("id").value(entry.id)
                    writer.name("dateTime").value(entry.dateTime)
                    writer.name("content").value(securityManager.decrypt(entry.content))
                    writer.name("mood").value(securityManager.decrypt(entry.mood))
                    writer.name("category").value(securityManager.decrypt(entry.category))
                    writer.name("weather").value(securityManager.decrypt(entry.weather))
                    writer.name("activities").value(securityManager.decrypt(entry.activities))
                    writer.name("numericMood").value(entry.numericMood)
                    writer.name("latentVibe").value(entry.latentVibe)
                    writer.name("isVectorized").value(entry.isVectorized)
                    writer.endObject()
                    withContext(Dispatchers.Main) { vaultProgress = (index + 1) to totalCount }
                }
                writer.endArray()
                
                // Vectors
                writer.name("vectors")
                writer.beginArray()
                val vectors = dao.getAllVectors()
                vectors.forEach { vector ->
                    writer.beginObject()
                    writer.name("entryId").value(vector.entryId)
                    writer.name("vector").value(Base64.encodeToString(vector.vector, Base64.DEFAULT))
                    writer.endObject()
                }
                writer.endArray()
                
                writer.endObject()
                writer.close()
                
                withContext(Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                e.message?.let { Log.e("LaniakeaViewModel", it) }
                e.printStackTrace()
                withContext(Dispatchers.Main) { onComplete(false) }
            } finally {
                withContext(Dispatchers.Main) { isVaultBackingUp = false }
            }
        }
    }

    fun importDataStream(uri: Uri, password: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { 
                isVaultRestoring = true
                vaultProgress = 0 to 0 
            }
            try {
                val context = getApplication<Application>()
                val inputStream = context.contentResolver.openInputStream(uri) ?: throw Exception("Failed to open URI")
                
                val decryptedStream = securityManager.getDecryptingStream(inputStream, password.toCharArray())
                val reader = JsonReader(InputStreamReader(decryptedStream, "UTF-8"))
                
                val dao = db.diaryDao()
                dao.clearDatabase()
                
                val oldToNewIdMap = mutableMapOf<Long, Long>()
                
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "settings" -> {
                            reader.beginObject()
                            val currentSettings = dao.getSettings() ?: AppSettings()
                            var userName = currentSettings.userName
                            var theme = currentSettings.theme
                            var privacySeed = currentSettings.privacySeed
                            
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "userName" -> userName = reader.nextString()
                                    "theme" -> theme = reader.nextString()
                                    "privacySeed" -> privacySeed = securityManager.encrypt(reader.nextString())
                                    else -> reader.skipValue()
                                }
                            }
                            dao.saveSettings(currentSettings.copy(
                                userName = userName,
                                theme = theme,
                                privacySeed = privacySeed
                            ))
                            reader.endObject()
                        }
                        "entries" -> {
                            reader.beginArray()
                            var count = 0
                            while (reader.hasNext()) {
                                reader.beginObject()
                                var oldId = -1L
                                var dateTime = 0L
                                var content = ""
                                var mood = ""
                                var category = ""
                                var weather = ""
                                var activities = ""
                                var numericMood = 0.0
                                var latentVibe = 0.0
                                var isVectorized = false
                                
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "id" -> oldId = reader.nextLong()
                                        "dateTime" -> dateTime = reader.nextLong()
                                        "content" -> content = reader.nextString()
                                        "mood" -> mood = reader.nextString()
                                        "category" -> category = reader.nextString()
                                        "weather" -> weather = reader.nextString()
                                        "activities" -> activities = reader.nextString()
                                        "numericMood" -> numericMood = reader.nextDouble()
                                        "latentVibe" -> latentVibe = reader.nextDouble()
                                        "isVectorized" -> isVectorized = reader.nextBoolean()
                                        else -> reader.skipValue()
                                    }
                                }
                                
                                val entry = DiaryEntry(
                                    dateTime = dateTime,
                                    content = securityManager.encrypt(content),
                                    mood = securityManager.encrypt(mood),
                                    category = securityManager.encrypt(category),
                                    weather = securityManager.encrypt(weather),
                                    activities = securityManager.encrypt(activities),
                                    numericMood = numericMood,
                                    latentVibe = latentVibe,
                                    isVectorized = isVectorized
                                )
                                val newId = dao.insertEntry(entry)
                                if (oldId != -1L) oldToNewIdMap[oldId] = newId
                                
                                count++
                                withContext(Dispatchers.Main) { vaultProgress = count to 0 }
                                reader.endObject()
                            }
                            reader.endArray()
                        }
                        "vectors" -> {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                reader.beginObject()
                                var oldEntryId = -1L
                                var vectorBase64 = ""
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "entryId" -> oldEntryId = reader.nextLong()
                                        "vector" -> vectorBase64 = reader.nextString()
                                        else -> reader.skipValue()
                                    }
                                }
                                val newEntryId = oldToNewIdMap[oldEntryId]
                                if (newEntryId != null && vectorBase64.isNotEmpty()) {
                                    dao.insertVector(SentenceVector(
                                        entryId = newEntryId,
                                        vector = Base64.decode(vectorBase64, Base64.DEFAULT)
                                    ))
                                }
                                reader.endObject()
                            }
                            reader.endArray()
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                reader.close()
                
                withContext(Dispatchers.Main) {
                    if (isEngineActive) embedder.initialize()
                    refreshData()
                    onComplete(true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { onComplete(false) }
            } finally {
                withContext(Dispatchers.Main) { isVaultRestoring = false }
            }
        }
    }
}
