package com.laniakea.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.laniakea.viewmodel.LaniakeaViewModel
import com.laniakea.ui.theme.*
import java.util.Locale

@Composable
fun HomeScreen(padding: PaddingValues, vm: LaniakeaViewModel) {
    var journalText by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf<MoodOption?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedWeather by remember { mutableStateOf<String?>(null) }
    var selectedActivities by remember { mutableStateOf(setOf<String>()) }
    var customActivity by remember { mutableStateOf("") }

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
            journalText = journalText,
            onTextChange = { 
                journalText = it
                vm.checkTextQuality(it)
            },
            tokenQuality = vm.tokenQuality,
            tokenCount = vm.tokenCount,
            isEntryValid = vm.isEntryValid,
            moods = moods,
            selectedMood = selectedMood,
            onMoodSelect = { selectedMood = it },
            categories = categories,
            selectedCategory = selectedCategory,
            onCategorySelect = { selectedCategory = it },
            weatherOptions = weatherOptions,
            selectedWeather = selectedWeather,
            onWeatherSelect = { selectedWeather = it },
            activityOptions = activityOptions,
            selectedActivities = selectedActivities,
            onActivityToggle = { activity ->
                selectedActivities = if (selectedActivities.contains(activity)) {
                    selectedActivities - activity
                } else {
                    selectedActivities + activity
                }
            },
            customActivity = customActivity,
            onCustomActivityChange = { customActivity = it },
            onAddCustomActivity = {
                if (customActivity.isNotBlank()) {
                    selectedActivities = selectedActivities + customActivity.trim()
                    customActivity = ""
                }
            },
            onSave = {
                if (journalText.isNotBlank() && selectedMood != null) {
                    vm.addDiaryEntry(
                        content = journalText,
                        mood = selectedMood!!.name,
                        numericMood = selectedMood!!.value,
                        category = selectedCategory ?: "",
                        weather = selectedWeather ?: "",
                        activities = selectedActivities.joinToString(", ")
                    )
                    journalText = ""
                    selectedMood = null
                    selectedActivities = emptySet()
                    selectedCategory = null
                    selectedWeather = null
                    vm.isEntryValid = false // Reset validation
                }
            }
        )

        // AI Engine Status
        EngineStatusCard(vm)

        // Analysis Progress
        AnalysisStatusCard(vm)

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun HeaderSection(userName: String) {
    Column {
        Text(
            "Hello, $userName",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        )
        Text(
            "Document your journey through the Laniakea.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun MainInputCard(
    journalText: String,
    onTextChange: (String) -> Unit,
    tokenQuality: Float,
    tokenCount: Int,
    isEntryValid: Boolean,
    moods: List<MoodOption>,
    selectedMood: MoodOption?,
    onMoodSelect: (MoodOption) -> Unit,
    categories: List<SelectOption>,
    selectedCategory: String?,
    onCategorySelect: (String) -> Unit,
    weatherOptions: List<SelectOption>,
    selectedWeather: String?,
    onWeatherSelect: (String) -> Unit,
    activityOptions: List<SelectOption>,
    selectedActivities: Set<String>,
    onActivityToggle: (String) -> Unit,
    customActivity: String,
    onCustomActivityChange: (String) -> Unit,
    onAddCustomActivity: () -> Unit,
    onSave: () -> Unit
) {
    var showMoreDetails by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }

    // Track the text that was there BEFORE we started listening
    var textBeforeListening by remember { mutableStateOf("") }

    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
    }

    val recognitionListener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) {
                isListening = false
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Speech recognition error"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val recognized = matches[0]
                    // Append to the specific buffer we saved when the button was clicked
                    val newText = if (textBeforeListening.isBlank()) {
                        recognized
                    } else {
                        // Ensure we don't accidentally double-space if the user left a space at the end
                        val base = textBeforeListening.trimEnd()
                        "$base $recognized"
                    }
                    onTextChange(newText)
                }
                isListening = false
            }
            override fun onPartialResults(partialResults: Bundle?) {
                // Real-time feedback for the user
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val partial = matches[0]
                    val previewText = if (textBeforeListening.isBlank()) partial else "${textBeforeListening.trimEnd()} $partial"
                    onTextChange(previewText)
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    DisposableEffect(Unit) {
        speechRecognizer.setRecognitionListener(recognitionListener)
        onDispose {
            speechRecognizer.destroy()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            textBeforeListening = journalText
            speechRecognizer.startListening(speechIntent)
        } else {
            Toast.makeText(context, "Microphone permission required for voice-to-text", Toast.LENGTH_SHORT).show()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Current Thought",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = {
                        if (isListening) {
                            speechRecognizer.stopListening()
                            isListening = false
                        } else {
                            when (PackageManager.PERMISSION_GRANTED) {
                                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) -> {
                                    textBeforeListening = journalText // CAPTURE CURRENT TEXT
                                    speechRecognizer.startListening(speechIntent)
                                }
                                else -> {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.MicNone else Icons.Default.Mic,
                        contentDescription = if (isListening) "Stop Listening" else "Start Voice Input",
                        modifier = Modifier.graphicsLayer(scaleX = if (isListening) 1.2f else 1f, scaleY = if (isListening) 1.2f else 1f)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = journalText,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    placeholder = { Text(if (isListening) "Listening..." else "What's on your mind? Safe to write.", color = MaterialTheme.colorScheme.outline) },
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        autoCorrectEnabled = true,
                        keyboardType = KeyboardType.Text
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                // Token Quality and Validation Info
                AnimatedVisibility(visible = journalText.isNotBlank()) {
                    val words = journalText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                    val goodWordCount = (tokenQuality * words.size).toInt()
                    
                    val (icon, tint, message) = when {
                        tokenCount > 256 -> Triple(Icons.Default.Warning, MaterialTheme.colorScheme.error, "Journal too long ($tokenCount/256 tokens)")
                        goodWordCount < 5 -> Triple(Icons.Default.Info, MaterialTheme.colorScheme.outline, "Please write at least 5 meaningful words ($goodWordCount/5)")
                        tokenQuality >= 0.9f -> Triple(Icons.Default.CheckCircle, Color(0xFF00E676), "Your journal is tokenized well")
                        tokenQuality >= 0.6f -> Triple(Icons.Default.Warning, Color(0xFFFFB74D), "Your journal has a bit of unknown words")
                        else -> Triple(Icons.Default.Error, MaterialTheme.colorScheme.error, "Your journal might not be processed by the Core")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.labelSmall,
                            color = tint,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Mood Selection
            SelectionSection("How does it feel?", moods.map { it.toSelectOption() }, selectedMood?.name) {
                val mood = moods.find { m -> m.name == it }
                if (mood != null) onMoodSelect(mood)
            }

            // Expandable details section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextButton(
                    onClick = { showMoreDetails = !showMoreDetails },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (showMoreDetails) "Show Less" else "Add More Details")
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        if (showMoreDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null
                    )
                }
            }

            AnimatedVisibility(visible = showMoreDetails) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    // Category Selection
                    SelectionSection("Category", categories, selectedCategory, onCategorySelect)

                    // Weather Selection
                    SelectionSection("Weather", weatherOptions, selectedWeather, onWeatherSelect)

                    // Activities Selection
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Activities",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            activityOptions.forEach { activity ->
                                SelectableEmoji(
                                    option = activity,
                                    isSelected = selectedActivities.contains(activity.name),
                                    onSelect = { onActivityToggle(activity.name) }
                                )
                            }

                            // Display selected custom activities as chips
                            selectedActivities.filter { name -> activityOptions.none { it.name == name } }.forEach { custom ->
                                AssistChip(
                                    onClick = { onActivityToggle(custom) },
                                    label = { Text(custom) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        labelColor = MaterialTheme.colorScheme.primary,
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customActivity,
                                onValueChange = onCustomActivityChange,
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Add more...", fontSize = 12.sp) },
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                            IconButton(onClick = onAddCustomActivity) {
                                Icon(Icons.Default.Add, contentDescription = "Add Activity", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = journalText.isNotBlank() && selectedMood != null && isEntryValid,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Keep This Thought", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SelectionSection(
    title: String,
    options: List<SelectOption>,
    selectedName: String?,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            options.forEach { option ->
                SelectableEmoji(
                    option = option,
                    isSelected = selectedName == option.name,
                    onSelect = { onSelect(option.name) }
                )
            }
        }
    }
}

data class SelectOption(val name: String, val emoji: String, val color: Color)
fun MoodOption.toSelectOption() = SelectOption(name, emoji, color)

@Composable
fun SelectableEmoji(
    option: SelectOption,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onSelect() }
            .background(
                if (isSelected) option.color.copy(alpha = 0.15f)
                else Color.Transparent
            )
            .border(
                width = 1.dp,
                color = if (isSelected) option.color.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(8.dp)
    ) {
        Text(
            text = option.emoji,
            fontSize = 24.sp,
            modifier = Modifier.graphicsLayer(
                scaleX = if (isSelected) 1.2f else 1.0f,
                scaleY = if (isSelected) 1.2f else 1.0f,
                alpha = if (isSelected) 1f else 0.7f
            )
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = option.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) option.color else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EngineStatusCard(vm: LaniakeaViewModel) {
    val statusColor by animateColorAsState(
        targetValue = when {
            vm.isEngineLoading -> MaterialTheme.colorScheme.secondary
            vm.isEngineActive -> Color(0xFF00E676)
            else -> MaterialTheme.colorScheme.error
        }, label = "statusColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                        .shadow(4.dp, CircleShape)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Laniakea Core",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (vm.isEngineActive) "Active & Processing" else "Core Offline",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (vm.isEngineLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            } else if (!vm.isEngineActive) {
                FilledTonalButton(
                    onClick = { vm.initializeEngine() },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.BuildCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Initialize")
                }
            }
        }
    }
}

@Composable
fun AnalysisStatusCard(vm: LaniakeaViewModel) {
    AnimatedVisibility(visible = true) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Journal Analysis",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (vm.unprocessedCount == 0 && !vm.isProcessing) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00E676))
                    }
                }

                if (vm.unprocessedCount > 0 || vm.isProcessing) {
                    val progress = if (vm.totalEntries > 0) {
                        (vm.totalEntries - vm.unprocessedCount).toFloat() / vm.totalEntries.toFloat()
                    } else 1f

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${vm.unprocessedCount} fragments pending sync",
                            style = MaterialTheme.typography.labelMedium
                        )

                        Button(
                            onClick = { vm.processMissingEntries() },
                            enabled = vm.isEngineActive && !vm.isProcessing,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            if (vm.isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Process", fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    Text(
                        "All thought fragments have been processed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

data class MoodOption(val name: String, val emoji: String, val value: Double, val color: Color)
