package com.laniakea.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.laniakea.data.DiaryDatabase
import com.laniakea.data.DiaryEntry
import com.laniakea.data.ObjectBoxManager
import com.laniakea.data.ObjectBoxSentenceVector
import com.laniakea.manager.*
import com.laniakea.engine.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

data class MoodOption(val name: String, val emoji: String, val value: Double, val color: Color)

class HomeScreenState(
    private val db: DiaryDatabase,
    private val embedder: SentenceEmbedder,
    private val vibeManager: VibeManager,
    private val semanticManager: SemanticManager,
    private val cognitiveTracker: CognitiveTracker,
    private val anomalyDetector: AnomalyDetector,
    private val securityManager: SecurityManager,
    private val coroutineScope: CoroutineScope,
    private val onEntryAdded: () -> Unit,
    private val onAnomalyDetected: (Pair<DiaryEntry, Float>) -> Unit
) {
    var journalText by mutableStateOf("")
    var selectedMood by mutableStateOf<MoodOption?>(null)
    var selectedCategory by mutableStateOf<String?>(null)
    var selectedWeather by mutableStateOf<String?>(null)
    var selectedActivities by mutableStateOf(setOf<String>())
    var customActivity by mutableStateOf("")

    var tokenQuality by mutableFloatStateOf(0f)
    var tokenCount by mutableIntStateOf(0)
    var isEntryValid by mutableStateOf(false)
    private var qualityCheckJob: Job? = null

    fun onTextChange(text: String) {
        journalText = text
        qualityCheckJob?.cancel()
        qualityCheckJob = coroutineScope.launch(Dispatchers.Default) {
            delay(300.milliseconds)
            val quality = embedder.calculateTokenQuality(text)
            val count = embedder.getUsedTokenCount(text)
            
            val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            
            withContext(Dispatchers.Main) {
                tokenQuality = quality
                tokenCount = count
                isEntryValid = count in 5..256 && quality > 0.4 && words.size >= 5
            }
        }
    }

    fun addDiaryEntry(isEngineActive: Boolean) {
        val content = journalText
        val mood = selectedMood?.name ?: return
        val numericMood = selectedMood?.value ?: 0.0
        val category = selectedCategory ?: ""
        val weather = selectedWeather ?: ""
        val activities = selectedActivities.joinToString(", ")

        val words = content.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val quality = embedder.calculateTokenQuality(content)
        val count = embedder.getUsedTokenCount(content)
        
        if (count !in 5..256 || words.size < 5 || quality <= 0.4) {
            return
        }

        coroutineScope.launch(Dispatchers.IO) {
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
                    
                    val anomalyResult = anomalyDetector.detectAnomaly(vector)
                    if (anomalyResult.second) {
                        withContext(Dispatchers.Main) {
                            onAnomalyDetected(Pair(rawEntry, anomalyResult.first))
                        }
                    }

                    withContext(Dispatchers.Main) {
                        resetInput()
                        onEntryAdded()
                    }
                    return@launch
                }
            }

            val entryToSave = securityManager.encryptEntry(rawEntry)
            db.diaryDao().insertEntry(entryToSave)
            withContext(Dispatchers.Main) {
                resetInput()
                onEntryAdded()
            }
        }
    }

    fun toggleActivity(activity: String) {
        selectedActivities = if (selectedActivities.contains(activity)) {
            selectedActivities - activity
        } else {
            selectedActivities + activity
        }
    }

    fun addCustomActivity() {
        if (customActivity.isNotBlank()) {
            selectedActivities = selectedActivities + customActivity.trim()
            customActivity = ""
        }
    }

    private fun resetInput() {
        journalText = ""
        selectedMood = null
        selectedActivities = emptySet()
        selectedCategory = null
        selectedWeather = null
        isEntryValid = false
    }
}
