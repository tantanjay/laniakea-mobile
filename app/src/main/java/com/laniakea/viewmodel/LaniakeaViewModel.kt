package com.laniakea.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Base64
import android.util.JsonReader
import android.util.JsonWriter
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.laniakea.data.AppSettings
import com.laniakea.data.DiaryDatabase
import com.laniakea.data.DiaryEntry
import com.laniakea.data.ObjectBoxManager
import com.laniakea.data.ObjectBoxSentenceVector
import com.laniakea.data.ObjectBoxSentenceVector_
import com.laniakea.data.TaglineTemplates
import com.laniakea.engine.SentenceEmbedder
import com.laniakea.engine.VibeEngine
import com.laniakea.security.SecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
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

    // Vault states
    var isVaultRestoring by mutableStateOf(false)
    var isVaultBackingUp by mutableStateOf(false)
    var isXlsxImporting by mutableStateOf(false)
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

    // Token Quality State
    var tokenQuality by mutableFloatStateOf(0f)
    var tokenCount by mutableIntStateOf(0)
    var isEntryValid by mutableStateOf(false)
    private var qualityCheckJob: Job? = null

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
        ObjectBoxManager.init(application)
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
                    val joy = embedder.embed("I feel happy, calm, and satisfied with life.")
                    val distress = embedder.embed("I feel stressed, tired, and discouraged.")

                    if (joy != null && distress != null) {
                        VibeEngine.setAnchors(
                            joy,
                            distress,
                            rotate = { it }, // already rotated by shield
                            normalize = { it } // already normalized
                        )
                    }

                    withContext(Dispatchers.IO) { calibrateAnchors() }
                    refreshData()
                }
            }
        }
    }

    private suspend fun calibrateAnchors() {
        val dao = db.diaryDao()
        val joyIds = dao.getRecentEntryIdsByNumericMood(2.0, 20)
        val sadIds = dao.getRecentEntryIdsByNumericMood(-2.0, 20)

        if (joyIds.size >= 5 && sadIds.size >= 5) {
            val vectorBox = ObjectBoxManager.vectorBox
            
            val joyFloatVectors = joyIds.mapNotNull { id -> 
                vectorBox.query(ObjectBoxSentenceVector_.entryId.equal(id)).build().findFirst()?.vector 
            }
            val sadFloatVectors = sadIds.mapNotNull { id -> 
                vectorBox.query(ObjectBoxSentenceVector_.entryId.equal(id)).build().findFirst()?.vector 
            }

            if (joyFloatVectors.size >= 5 && sadFloatVectors.size >= 5 &&
                joyFloatVectors.all { it.size == 768 } &&
                sadFloatVectors.all { it.size == 768 }) {

                val joyAvg = calculateAverageVector(joyFloatVectors)
                val sadAvg = calculateAverageVector(sadFloatVectors)

                VibeEngine.setAnchors(
                    joy = joyAvg,
                    distress = sadAvg,
                    rotate = { it },      // already rotated by embedder
                    normalize = { it }    // already normalized
                )

                Log.i(
                    "VibeCalibration",
                    "Anchors updated with ${joyFloatVectors.size} Joy and ${sadFloatVectors.size} Sad vectors."
                )
            }

        } else {
            Log.i("VibeCalibration", "Not enough entries to calibrate anchors yet.")
        }
    }

    private fun calculateAverageVector(vectors: List<FloatArray>): FloatArray {
        if (vectors.isEmpty()) return FloatArray(768)

        val size = 768
        val result = FloatArray(size)
        val count = vectors.size.toFloat()

        for (vector in vectors) {
            require(vector.size == size) { "All vectors must have the same dimension" }

            for (i in 0 until size) {
                result[i] += vector[i]
            }
        }

        for (i in 0 until size) {
            result[i] /= count
        }

        return result
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

    fun checkTextQuality(text: String) {
        qualityCheckJob?.cancel()
        qualityCheckJob = viewModelScope.launch(Dispatchers.Default) {
            delay(300) // Debounce
            val quality = embedder.calculateTokenQuality(text)
            val count = embedder.getUsedTokenCount(text)
            
            // Context Validation: 
            // Must have variety (handled in SentenceEmbedder) and enough words
            val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            
            withContext(Dispatchers.Main) {
                tokenQuality = quality
                tokenCount = count
                isEntryValid = count in 5..256 && quality > 0.4 && words.size >= 5
            }
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
        // FAIL-SAFE RE-VALIDATION
        val words = content.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val quality = embedder.calculateTokenQuality(content)
        val count = embedder.getUsedTokenCount(content)
        
        if (count !in 5..256 || words.size < 5 || quality <= 0.4) {
            Log.e("LaniakeaViewModel", "Aborted save: Context validation failed at runtime check.")
            return
        }

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

                    val anchors = VibeEngine.getAnchors()

                    Log.d("LaniakeaViewModel", "----------------------------")
                    Log.d("LaniakeaViewModel", "Joy Anchor: ${anchors.first?.contentToString()}")
                    Log.d("LaniakeaViewModel", "Distress Anchor: ${anchors.second?.contentToString()}")
                    Log.d("LaniakeaViewModel", "Adding entry with vector - Mood: ${entry.numericMood}, Vibe: ${entry.latentVibe}, Context: $content")
                    Log.d("LaniakeaViewModel", "----------------------------")

                    val entryId = db.diaryDao().insertEntry(entry)
                    ObjectBoxManager.vectorBox.put(ObjectBoxSentenceVector(entryId = entryId, vector = vector))
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
                        ObjectBoxManager.vectorBox.put(ObjectBoxSentenceVector(entryId = entry.id, vector = vector))
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
                val span = 7
                val statusMap = mapOf(
                    -10f to "SHARP DECLINE",
                    -5f to "DECLINING",
                    5f to "STABLE",
                    10f to "IMPROVING",
                    Float.MAX_VALUE to "STRONG UPTURN"
                )

                val manual = calculateMomentum(manualDaily, span, 3f, statusMap)
                val ai = calculateMomentum(aiDaily, span, 3f, statusMap)
                val anchors = VibeEngine.getAnchors()

                Log.d("LaniakeaViewModel", "----------------------------")
                Log.d("LaniakeaViewModel", "Span: $span")
                Log.d("LaniakeaViewModel", "Joy Anchor: ${anchors.first.contentToString()}")
                Log.d("LaniakeaViewModel", "Distress Anchor: ${anchors.second.contentToString()}")
                Log.d("LaniakeaViewModel", "Moods Momentum: ${manual.first}, Status ${manual.second}")
                Log.d("LaniakeaViewModel", "Vibes: $aiDaily")
                Log.d("LaniakeaViewModel", "Vibes Momentum: ${ai.first}, Status ${ai.second}")
                Log.d("LaniakeaViewModel", "----------------------------")

                val entries = dao.getAllEntries().map { securityManager.decrypt(it.content) }
                for ((index, decryptedContent) in entries.withIndex()) {
                    if (index >= manualDaily.size) break

                    Log.d("LaniakeaViewModel", "Content: $decryptedContent")
                    Log.d("LaniakeaViewModel","Manual: ${manualDaily[index]}")
                    Log.d("LaniakeaViewModel","AI Vibe: ${aiDaily[index]}")
                    Log.d("LaniakeaViewModel", "----------------------------")
                }

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
                val vectors = ObjectBoxManager.vectorBox.all
                vectors.forEach { vector ->
                    val floatArray = vector.vector
                    if (floatArray != null) {
                        writer.beginObject()
                        writer.name("entryId").value(vector.entryId)
                        
                        // Convert FloatArray to ByteArray for Base64 encoding
                        val byteBuffer = java.nio.ByteBuffer.allocate(floatArray.size * 4)
                        floatArray.forEach { byteBuffer.putFloat(it) }
                        val byteArray = byteBuffer.array()
                        
                        writer.name("vector").value(Base64.encodeToString(byteArray, Base64.DEFAULT))
                        writer.endObject()
                    }
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
                ObjectBoxManager.vectorBox.removeAll()

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
                                    val byteArray = Base64.decode(vectorBase64, Base64.DEFAULT)
                                    val byteBuffer = java.nio.ByteBuffer.wrap(byteArray)
                                    val floats = FloatArray(byteArray.size / 4)
                                    for (i in floats.indices) floats[i] = byteBuffer.getFloat()
                                    
                                    ObjectBoxManager.vectorBox.put(ObjectBoxSentenceVector(
                                        entryId = newEntryId,
                                        vector = floats
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

    private fun getCellStringValue(cell: Cell?): String {
        return when (cell?.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> {
                val value = cell.numericCellValue
                if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> cell.cellFormula
            else -> cell?.toString() ?: ""
        }
    }

    fun importXlsxStream(uri: Uri, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isXlsxImporting = true
                vaultProgress = 0 to 0
            }
            try {
                val context = getApplication<Application>()
                val inputStream = context.contentResolver.openInputStream(uri) ?: throw Exception("Failed to open URI")

                val workbook = WorkbookFactory.create(inputStream)
                val sheet = workbook.getSheetAt(0)
                val totalRows = sheet.lastRowNum

                val dao = db.diaryDao()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                for (i in 1..totalRows) { // Skip header
                    val row = sheet.getRow(i) ?: continue

                    try {
                        val dateStr = getCellStringValue(row.getCell(0)).substringBefore(".")
                        val timeStr = getCellStringValue(row.getCell(1)).substringBefore(".")
                        val mood = getCellStringValue(row.getCell(2))
                        val category = getCellStringValue(row.getCell(3))
                        val weather = getCellStringValue(row.getCell(4))
                        val activity = getCellStringValue(row.getCell(5))
                        val content = getCellStringValue(row.getCell(6))

                        // Validation: date, time, mood must not be empty
                        if (dateStr.isBlank() || timeStr.isBlank() || mood.isBlank()) continue

                        val dateTime = try {
                            dateFormat.parse("$dateStr $timeStr")?.time ?: continue
                        } catch (_: Exception) { continue }

                        // Validation: content must have at least 5 words
                        val words = content.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                        if (words.size < 5) continue

                        // Numeric mood mapping
                        val numericMood = when(mood.lowercase()) {
                            "joy", "happy", "great", "excellent" -> 2.0
                            "good", "pleasant" -> 1.0
                            "neutral", "ok" -> 0.0
                            "sad", "bad", "unhappy" -> -1.0
                            "miserable", "terrible", "awful" -> -2.0
                            else -> 0.0
                        }

                        val entry = DiaryEntry(
                            dateTime = dateTime,
                            content = securityManager.encrypt(content),
                            mood = securityManager.encrypt(mood),
                            category = securityManager.encrypt(category),
                            weather = securityManager.encrypt(weather),
                            activities = securityManager.encrypt(activity),
                            numericMood = numericMood,
                            latentVibe = 0.0,
                            isVectorized = false
                        )
                        dao.insertEntry(entry)
                    } catch (e: Exception) {
                        Log.e("LaniakeaViewModel", "Error importing row $i", e)
                    }

                    withContext(Dispatchers.Main) { vaultProgress = i to totalRows }
                }

                workbook.close()
                inputStream.close()

                withContext(Dispatchers.Main) {
                    refreshData()
                    onComplete(true)
                }
            } catch (e: Exception) {
                Log.e("LaniakeaViewModel", "XLSX Import failed", e)
                withContext(Dispatchers.Main) { onComplete(false) }
            } finally {
                withContext(Dispatchers.Main) { isXlsxImporting = false }
            }
        }
    }

    suspend fun semanticSearch(query: String, limit: Int = 5): List<DiaryEntry> {
        if (!isEngineActive) return emptyList()
        return withContext(Dispatchers.IO) {
            val vector = embedder.embed(query) ?: return@withContext emptyList()
            val similarVectors = ObjectBoxManager.search(vector, limit)
            val similarIds = similarVectors.map { it.entryId }
            
            if (similarIds.isEmpty()) return@withContext emptyList()
            
            val entries = db.diaryDao().getEntriesByIds(similarIds)
            val entryMap = entries.associateBy { it.id }
            similarIds.mapNotNull { entryMap[it] }.map { decryptEntry(it) }
        }
    }

    suspend fun findSimilarEntries(entryId: Long, limit: Int = 5): List<DiaryEntry> {
        return withContext(Dispatchers.IO) {
            val vectorObj = ObjectBoxManager.vectorBox.query(ObjectBoxSentenceVector_.entryId.equal(entryId)).build().findFirst()
            val vector = vectorObj?.vector ?: return@withContext emptyList()
            
            val similarVectors = ObjectBoxManager.search(vector, limit + 1)
            val similarIds = similarVectors.map { it.entryId }.filter { it != entryId }.take(limit)
            
            if (similarIds.isEmpty()) return@withContext emptyList()
            
            val entries = db.diaryDao().getEntriesByIds(similarIds)
            val entryMap = entries.associateBy { it.id }
            similarIds.mapNotNull { entryMap[it] }.map { decryptEntry(it) }
        }
    }

    suspend fun getThemeClusters(): Map<String, List<DiaryEntry>> {
        if (!isEngineActive) return emptyMap()
        return withContext(Dispatchers.IO) {
            val themes = listOf(
                "Relationships, Love, Friends",
                "Career, Work, Goals, Success",
                "Mental Health, Anxiety, Stress, Depression",
                "Physical Health, Exercise, Body, Sleep",
                "Personal Growth, Learning, Philosophy"
            )
            
            val themeClusters = mutableMapOf<String, List<DiaryEntry>>()
            
            for (theme in themes) {
                val themeVector = embedder.embed(theme) ?: continue
                val similarVectors = ObjectBoxManager.search(themeVector, 3)
                val similarIds = similarVectors.map { it.entryId }
                
                if (similarIds.isNotEmpty()) {
                    val entries = db.diaryDao().getEntriesByIds(similarIds)
                    val entryMap = entries.associateBy { it.id }
                    val decryptedEntries = similarIds.mapNotNull { entryMap[it] }.map { decryptEntry(it) }
                    
                    val shortTheme = theme.split(",").first()
                    themeClusters[shortTheme] = decryptedEntries
                }
            }
            
            themeClusters
        }
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
