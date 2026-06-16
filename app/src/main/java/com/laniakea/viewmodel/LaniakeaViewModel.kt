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
import com.laniakea.LaniakeaApplication
import com.laniakea.data.AppSettings
import com.laniakea.data.DiaryEntry
import com.laniakea.data.ObjectBoxManager
import com.laniakea.data.ObjectBoxSentenceVector
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
import com.laniakea.manager.VaultManager
import kotlin.time.Duration.Companion.milliseconds

class LaniakeaViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LaniakeaApplication
    private val container = app.container

    private val db = container.database
    private val securityManager = container.securityManager
    private val embedder = container.embedder

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

    private val analyticsManager = container.analyticsManager
    private val semanticManager = container.semanticManager
    private val vibeManager = container.vibeManager
    
    private val anomalyDetector = container.anomalyDetector
    private val cognitiveTracker = container.cognitiveTracker

    // UI State
    var currentAnomalyAlert by mutableStateOf<Pair<DiaryEntry, Float>?>(null)
        private set

    var userName by mutableStateOf("Traveller")
    var profilePicture by mutableStateOf("Person")
    var theme by mutableStateOf("PURPLE")
    var selectedThemes by mutableStateOf<List<String>>(emptyList())

    // Vault states
    var isVaultRestoring by mutableStateOf(false)
    var isVaultBackingUp by mutableStateOf(false)
    var isXlsxImporting by mutableStateOf(false)
    var vaultProgress by mutableStateOf(0 to 0)

    var isEngineActive by mutableStateOf(false)
    var isEngineLoading by mutableStateOf(false)
    var isThemesInitialized by mutableStateOf(false)
        private set
    var isAxesInitialized by mutableStateOf(false)
        private set
    var autoLoadEnabled by mutableStateOf(false)
    var vibeYear by mutableStateOf("2026")
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
        entries.map { securityManager.decryptEntry(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        container.engineActiveProvider = { isEngineActive }
        ObjectBoxManager.init(application)
        observeSettings()
        observeEngineStatus()
        refreshData()
    }

    fun dismissAnomalyAlert() {
        currentAnomalyAlert = null
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
                    profilePicture = it.profilePicture.takeIf { pic -> pic.isNotEmpty() } ?: "Person"
                    theme = it.theme
                    selectedThemes = it.selectedThemes.split(",").filter { s -> s.isNotBlank() }

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
                    withContext(Dispatchers.IO) { 
                        vibeManager.initializeAxes() 
                        semanticManager.initializeThemes()
                    }
                    isThemesInitialized = true
                    isAxesInitialized = true
                    refreshData()
                } else {
                    isThemesInitialized = false
                    isAxesInitialized = false
                }
            }
        }
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

    fun updateProfile(newName: String, newPicture: String) {
        viewModelScope.launch {
            val currentSettings = db.diaryDao().getSettings() ?: AppSettings()
            db.diaryDao().saveSettings(currentSettings.copy(userName = newName, profilePicture = newPicture))
        }
    }

    fun checkTextQuality(text: String) {
        qualityCheckJob?.cancel()
        qualityCheckJob = viewModelScope.launch(Dispatchers.Default) {
            delay(300.milliseconds) // Debounce
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
                isVectorized = false
            )

            if (isEngineActive && semanticManager.isThemesInitialized() && vibeManager.isAxesInitialized()) {
                val vectors = embedder.embedBoth(content)
                
                if (vectors != null) {
                    val rawVector = vectors.first
                    val vector = vectors.second
                    val vibeScoresJson = vibeManager.calculateVibesJson(vector)
                    val semanticTheme = semanticManager.classifyTheme(rawVector)
                    val themeDistancesJson = semanticManager.calculateAllThemeDistancesJson(rawVector)
                    
                    val metrics = cognitiveTracker.analyze(content, vector) { axisName, vec ->
                        vibeManager.getAxisScore(axisName, vec)
                    }
                    
                    val entryToSave = securityManager.encryptEntry(
                        rawEntry.copy(
                            isVectorized = true,
                            syntacticPacing = metrics.syntacticPacing,
                            agencyScore = metrics.agencyScore,
                            epistemicModality = metrics.epistemicModality,
                            processingMarkers = metrics.processingMarkers,
                            temporalHorizon = metrics.temporalHorizon
                        )
                    )

                    val entryId = db.diaryDao().insertEntry(entryToSave)
                    ObjectBoxManager.vectorBox.put(
                        ObjectBoxSentenceVector(entryId = entryId, vector = vector, semanticTheme = semanticTheme, themeDistancesJson = themeDistancesJson, vibeScoresJson = vibeScoresJson)
                    )
                    
                    // Check for anomaly
                    val anomalyResult = anomalyDetector.detectAnomaly(vector)
                    if (anomalyResult.second) { // isAnomaly
                        withContext(Dispatchers.Main) {
                            currentAnomalyAlert = Pair(rawEntry, anomalyResult.first)
                        }
                    }

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
        if (isProcessing || !isEngineActive || !semanticManager.isThemesInitialized() || !vibeManager.isAxesInitialized()) return
        viewModelScope.launch(Dispatchers.IO) {
            isProcessing = true
            val dao = db.diaryDao()
            val missing = dao.getUnprocessedEntries()
            var processedCount = 0
            var lastRefreshTime = System.currentTimeMillis()
            
            val updatedEntriesBatch = mutableListOf<DiaryEntry>()
            val vectorBatch = mutableListOf<ObjectBoxSentenceVector>()

            missing.forEach { entry ->
                try {
                    val decryptedContent = securityManager.decrypt(entry.content)
                    val vectors = embedder.embedBoth(decryptedContent)
                    
                    if (vectors != null) {
                        val rawVector = vectors.first
                        val vector = vectors.second
                        
                        val vibeScoresJson = vibeManager.calculateVibesJson(vector)
                        val semanticTheme = semanticManager.classifyTheme(rawVector)
                        val themeDistancesJson = semanticManager.calculateAllThemeDistancesJson(rawVector)
                        
                        val metrics = cognitiveTracker.analyze(decryptedContent, vector) { axisName, vec ->
                            vibeManager.getAxisScore(axisName, vec)
                        }
                        
                        updatedEntriesBatch.add(entry.copy(
                            isVectorized = true,
                            syntacticPacing = metrics.syntacticPacing,
                            agencyScore = metrics.agencyScore,
                            epistemicModality = metrics.epistemicModality,
                            processingMarkers = metrics.processingMarkers,
                            temporalHorizon = metrics.temporalHorizon
                        ))
                        vectorBatch.add(
                            ObjectBoxSentenceVector(entryId = entry.id, vector = vector, semanticTheme = semanticTheme, themeDistancesJson = themeDistancesJson, vibeScoresJson = vibeScoresJson)
                        )
                    }
                } catch (e: Exception) { e.printStackTrace() }
                
                processedCount++
                val currentTime = System.currentTimeMillis()
                
                // Flush to DB and UI every 10 items OR if 5 seconds have passed, whichever comes first
                if (processedCount >= 10 || (currentTime - lastRefreshTime) > 5000) {
                    if (updatedEntriesBatch.isNotEmpty()) {
                        dao.updateEntries(updatedEntriesBatch)
                        ObjectBoxManager.vectorBox.put(vectorBatch)
                        updatedEntriesBatch.clear()
                        vectorBatch.clear()
                    }
                    refreshProcessingStats()
                    lastRefreshTime = currentTime
                    processedCount = 0
                }
            }
            
            // Final flush
            if (updatedEntriesBatch.isNotEmpty()) {
                dao.updateEntries(updatedEntriesBatch)
                ObjectBoxManager.vectorBox.put(vectorBatch)
            }
            refreshProcessingStats() // Final refresh
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
            if (isEngineActive) vibeManager.initializeAxes()

            val oldest = dao.getOldestTimestamp()
            val year = if (oldest != null) {
                val cal = Calendar.getInstance()
                cal.timeInMillis = oldest
                cal.get(Calendar.YEAR).toString()
            } else LocalDate.now().year.toString()

            withContext(Dispatchers.Main) {
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

    fun updateSelectedThemes(themes: List<String>) {
        viewModelScope.launch {
            val dao = db.diaryDao()
            val current = dao.getSettings() ?: AppSettings()
            dao.saveSettings(current.copy(selectedThemes = themes.joinToString(",")))
            selectedThemes = themes
            
            if (isEngineActive) {
                refreshData()
            }
        }
    }
}
