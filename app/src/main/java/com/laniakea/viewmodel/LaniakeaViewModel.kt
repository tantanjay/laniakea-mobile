package com.laniakea.viewmodel

import android.app.Application
import android.net.Uri
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
import com.laniakea.engine.SentenceEmbedder
import com.laniakea.engine.VibeEngine
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Calendar

import com.laniakea.manager.AnalyticsManager
import com.laniakea.manager.SecurityManager
import com.laniakea.manager.SemanticManager
import com.laniakea.manager.VaultManager
import com.laniakea.manager.WritingMetrics

class LaniakeaViewModel(application: Application) : AndroidViewModel(application) {
    private val db = DiaryDatabase.getDatabase(application)
    private val securityManager = SecurityManager(application)
    private val embedder = SentenceEmbedder(application, db, securityManager)

    private val vaultManager = VaultManager(
        application = application,
        db = db,
        securityManager = securityManager,
        onProgress = { current, total -> vaultProgress = current to total },
        onStateChange = { isBackingUp, isRestoring, isImporting ->
            isVaultBackingUp = isBackingUp
            isVaultRestoring = isRestoring
            isXlsxImporting = isImporting
        }
    )

    private val analyticsManager = AnalyticsManager(db, securityManager)
    
    private val semanticManager = SemanticManager(db, embedder, securityManager) { isEngineActive }

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

    // Writing Trends State
    var writingMetrics by mutableStateOf<WritingMetrics?>(null)
    var isMetricsLoading by mutableStateOf(false)

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
        entries.map { securityManager.decryptEntry(it) }
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
            val rawEntry = DiaryEntry(
                dateTime = System.currentTimeMillis(),
                content = content,
                mood = mood,
                category = category,
                weather = weather,
                activities = activities,
                numericMood = numericMood,
                latentVibe = 0.0,
                isVectorized = false
            )

            if (isEngineActive) {
                val vector = embedder.embed(content)
                if (vector != null) {
                    val aiVibe = VibeEngine.calculateVibeScore(vector)
                    val entryToSave = securityManager.encryptEntry(
                        rawEntry.copy(latentVibe = aiVibe.toDouble(), isVectorized = true)
                    )

                    val anchors = VibeEngine.getAnchors()

                    Log.d("LaniakeaViewModel", "----------------------------")
                    Log.d("LaniakeaViewModel", "Joy Anchor: ${anchors.first?.contentToString()}")
                    Log.d("LaniakeaViewModel", "Distress Anchor: ${anchors.second?.contentToString()}")
                    Log.d("LaniakeaViewModel", "Adding entry with vector - Mood: ${entryToSave.numericMood}, Vibe: ${entryToSave.latentVibe}, Context: $content")
                    Log.d("LaniakeaViewModel", "----------------------------")

                    val entryId = db.diaryDao().insertEntry(entryToSave)
                    ObjectBoxManager.vectorBox.put(ObjectBoxSentenceVector(entryId = entryId, vector = vector))
                    refreshData()
                    return@launch
                }
            }

            val entryToSave = securityManager.encryptEntry(rawEntry)
            db.diaryDao().insertEntry(entryToSave)
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
            val manualDaily = analyticsManager.aggregateByDay(scores.map { it.dateTime to it.numericMood.toFloat() })
            val aiDaily = analyticsManager.aggregateByDay(scores.map { it.dateTime to it.latentVibe.toFloat() })

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

                val manual = analyticsManager.calculateMomentum(manualDaily, span, 3f, statusMap)
                val ai = analyticsManager.calculateMomentum(aiDaily, span, 3f, statusMap)
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
                    tagline = analyticsManager.generateTagline(year)
                }
            }
        }
    }

    fun exportDataStream(uri: Uri, password: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            vaultManager.exportDataStream(uri, password, onComplete)
        }
    }

    fun importDataStream(uri: Uri, password: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            vaultManager.importDataStream(uri, password) { success ->
                if (success) {
                    if (isEngineActive) embedder.initialize()
                    refreshData()
                }
                onComplete(success)
            }
        }
    }

    fun importXlsxStream(uri: Uri, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            vaultManager.importXlsxStream(uri) { success ->
                if (success) refreshData()
                onComplete(success)
            }
        }
    }

    suspend fun semanticSearch(query: String, limit: Int = 5): List<DiaryEntry> {
        return semanticManager.semanticSearch(query, limit)
    }

    suspend fun findSimilarEntries(entryId: Long, limit: Int = 5): List<DiaryEntry> {
        return semanticManager.findSimilarEntries(entryId, limit)
    }

    suspend fun getThemeClusters(): Map<String, List<DiaryEntry>> {
        return semanticManager.getThemeClusters()
    }

    suspend fun analyzeWritingTrends(limit: Int = 30): WritingMetrics {
        return analyticsManager.analyzeWritingTrends(limit)
    }
}
