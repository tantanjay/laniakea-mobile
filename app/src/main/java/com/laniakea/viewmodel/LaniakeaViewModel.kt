package com.laniakea.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Calendar
import com.laniakea.manager.VaultManager

class LaniakeaViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LaniakeaApplication
    private val container = app.container

    private val db = container.database
    private val securityManager = container.securityManager
    private val embedder = container.embedder

    val vaultManager = VaultManager(
        application = application,
        db = db,
        securityManager = securityManager,
        onProgress = { _, _ -> },
        onStateChange = { _, _, _ -> }
    )

    private val analyticsManager = container.analyticsManager
    private val semanticManager = container.semanticManager
    private val vibeManager = container.vibeManager
    private val cognitiveTracker = container.cognitiveTracker
    var currentAnomalyAlert by mutableStateOf<Pair<DiaryEntry, Float>?>(null)
    var userName by mutableStateOf("Traveller")
    var profilePicture by mutableStateOf("Person")
    var theme by mutableStateOf("PURPLE")
    var selectedThemes by mutableStateOf<List<String>>(emptyList())

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

    val allEntries = db.diaryDao().getAllEntriesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun triggerAnomalyAlert(pair: Pair<DiaryEntry, Float>?) { currentAnomalyAlert = pair }

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
