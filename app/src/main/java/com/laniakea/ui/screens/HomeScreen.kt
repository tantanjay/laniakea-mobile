package com.laniakea.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.laniakea.viewmodel.LaniakeaViewModel
import com.laniakea.viewmodel.HomeScreenState
import com.laniakea.ui.theme.*
import com.laniakea.LaniakeaApplication
import com.laniakea.ui.components.home.*
import com.laniakea.viewmodel.MoodOption

@Composable
fun HomeScreen(padding: PaddingValues, vm: LaniakeaViewModel) {
    val context = LocalContext.current
    val app = context.applicationContext as LaniakeaApplication
    val coroutineScope = rememberCoroutineScope()
    
    val state = remember {
        HomeScreenState(
            db = app.container.database,
            embedder = app.container.embedder,
            vibeManager = app.container.vibeManager,
            semanticManager = app.container.semanticManager,
            cognitiveTracker = app.container.cognitiveTracker,
            anomalyDetector = app.container.anomalyDetector,
            securityManager = app.container.securityManager,
            coroutineScope = coroutineScope,
            onEntryAdded = { vm.refreshData() },
            onAnomalyDetected = { vm.triggerAnomalyAlert(it) }
        )
    }

    val moods = listOf(
        MoodOption("Terrible", "😫", -2.0, MoodTerrible),
        MoodOption("Bad", "🙁", -1.0, MoodBad),
        MoodOption("Fine", "😐", 0.0, MoodFine),
        MoodOption("Good", "🙂", 1.0, MoodGood),
        MoodOption("Awesome", "🤩", 2.0, MoodAwesome)
    )

    val categories = listOf(
        SelectOption("Home", "🏠", MaterialTheme.colorScheme.primary),
        SelectOption("Work", "💼", MaterialTheme.colorScheme.secondary),
        SelectOption("Social", "👥", MaterialTheme.colorScheme.tertiary),
        SelectOption("Other", "✨", MaterialTheme.colorScheme.outline)
    )

    val weatherOptions = listOf(
        SelectOption("Sunny", "☀️", Color(0xFFFFD600)),
        SelectOption("Rain", "🌧️", Color(0xFF448AFF)),
        SelectOption("Cloudy", "☁️", Color(0xFF90A4AE)),
        SelectOption("Snow", "❄️", Color(0xFF00B0FF))
    )

    val activityOptions = listOf(
        SelectOption("Exercise", "🏃", Color(0xFF4CAF50)),
        SelectOption("Meeting", "🤝", Color(0xFFFF9800)),
        SelectOption("Relax", "🧘", Color(0xFF9C27B0)),
        SelectOption("Travel", "✈️", Color(0xFF00BCD4))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
            )
            .padding(padding)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        HeaderSection(vm.userName)

        // Journal Input Card with AI Vibe
        MainInputCard(
            journalText = state.journalText,
            onTextChange = { state.onTextChange(it) },
            tokenQuality = state.tokenQuality,
            tokenCount = state.tokenCount,
            isEntryValid = state.isEntryValid,
            moods = moods,
            selectedMood = state.selectedMood,
            onMoodSelect = { state.selectedMood = it },
            categories = categories,
            selectedCategory = state.selectedCategory,
            onCategorySelect = { state.selectedCategory = it },
            weatherOptions = weatherOptions,
            selectedWeather = state.selectedWeather,
            onWeatherSelect = { state.selectedWeather = it },
            activityOptions = activityOptions,
            selectedActivities = state.selectedActivities,
            onActivityToggle = { state.toggleActivity(it) },
            customActivity = state.customActivity,
            onCustomActivityChange = { state.customActivity = it },
            onAddCustomActivity = { state.addCustomActivity() },
            onSave = { state.addDiaryEntry(vm.isEngineActive) }
        )

        // AI Engine Status
        EngineStatusCard(vm)

        // Analysis Progress
        AnalysisStatusCard(vm)

        Spacer(modifier = Modifier.height(32.dp))
    }
}

