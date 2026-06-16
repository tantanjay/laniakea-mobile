package com.laniakea.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laniakea.LaniakeaApplication
import com.laniakea.data.DiaryEntry
import com.laniakea.manager.SemanticManager
import com.laniakea.ui.components.insight.CognitiveRadarChart
import com.laniakea.ui.components.insight.GradientGauge
import com.laniakea.ui.components.insight.PeriodDigestCard
import com.laniakea.ui.components.insight.WritingTrendCard
import com.laniakea.viewmodel.InsightScreenState
import com.laniakea.viewmodel.LaniakeaViewModel
import kotlinx.coroutines.launch

@Composable
fun InsightScreen(padding: PaddingValues, vm: LaniakeaViewModel) {
    val context = LocalContext.current
    val app = context.applicationContext as LaniakeaApplication
    val state = remember { 
        val prefs = context.getSharedPreferences("insight_prefs", android.content.Context.MODE_PRIVATE)
        InsightScreenState(
            analyticsManager = app.container.analyticsManager,
            digestManager = app.container.digestManager,
            semanticManager = app.container.semanticManager,
            prefs = prefs
        ) 
    }
    val coroutineScope = rememberCoroutineScope()
    
    var showInfo by remember { mutableStateOf(false) }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Got it") } },
            title = { Text("About Insights", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    InfoSection("🧠 Semantic Themes", "We use on-device AI to group your entries and discover the main themes you write about. This helps you understand what's on your mind over the selected time period.")
                    InfoSection("📝 Writing Patterns", "These metrics observe the structure of your writing—length, variety, self-reference, and temporal orientation. They answer \"How has my writing changed?\"")
                    InfoSection("📊 How Trends Work", "Each sparkline shows your entries in chronological order within your selected date range. The arrow (↑↓→) compares your more recent average to your earlier average.")
                    InfoSection("🔒 Privacy", "All analysis runs entirely on your device. Your entries are decrypted only in memory for analysis and never leave your phone.")
                    Text("Note: These are structural and thematic observations, not psychological assessments. They're simply patterns worth noticing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
                }
            }
        )
    }

    LaunchedEffect(state.insightSelectedRange, state.insightTimeOffset) {
        state.isMetricsLoading = true
        try {
            state.analyzeWritingTrends()
            state.refreshInsights(vm.selectedThemes)
        } finally {
            state.isMetricsLoading = false
        }
    }

    LaunchedEffect(Unit) {
        if (state.periodDigest == null || state.themeClusters == null) {
            state.refreshInsights(vm.selectedThemes)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        // Sticky Header for Date Navigation
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        IconButton(onClick = { 
                            state.insightTimeOffset++ 
                        }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Period")
                        }
                    }

                    Box(modifier = Modifier.weight(2f), contentAlignment = Alignment.Center) {
                        var expanded by remember { mutableStateOf(false) }
                        TextButton(onClick = { expanded = true }) {
                            Text("${state.insightSelectedRange} ▾", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            state.insightRanges.forEach { range ->
                                DropdownMenuItem(
                                    text = { Text(range) },
                                    onClick = {
                                        state.updateSelectedRange(range)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showInfo = true }) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { 
                                if (state.insightTimeOffset > 0) {
                                    state.insightTimeOffset--
                                }
                            },
                            enabled = state.insightTimeOffset > 0
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Period")
                        }
                    }
                }
                Text(
                    text = state.getInsightDisplayDates(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }

        // Scrollable Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Period Digest
            val currentDigest = state.periodDigest
            if (currentDigest == null && state.isDigestLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (currentDigest != null) {
                Box(modifier = Modifier.alpha(if (state.isDigestLoading) 0.5f else 1f)) {
                    PeriodDigestCard(digest = currentDigest)
                }
            } else {
                Text("No entries found for this period.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Writing Trends Section
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Create, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text("Writing Trends", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            val metrics = state.writingMetrics
            if (metrics == null && state.isMetricsLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (metrics != null && metrics.entryLengths.isNotEmpty()) {
                Box(modifier = Modifier.alpha(if (state.isMetricsLoading) 0.5f else 1f)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Horizontal scroll for trends to save vertical space
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val cardModifier = Modifier.width(260.dp)
                            
                            Box(modifier = cardModifier) {
                                WritingTrendCard(
                                    label = "Entry Length", emoji = "📏", dataPoints = metrics.entryLengths,
                                    formatValue = { "${it.toInt()} words" }, sparklineColor = MaterialTheme.colorScheme.primary
                                )
                            }
                            Box(modifier = cardModifier) {
                                WritingTrendCard(
                                    label = "Vocabulary", emoji = "🔤", dataPoints = metrics.vocabularyDiversity,
                                    formatValue = { "${(it * 100).toInt()}% unique" }, sparklineColor = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            Box(modifier = cardModifier) {
                                WritingTrendCard(
                                    label = "Questions", emoji = "❓", dataPoints = metrics.questionFrequency,
                                    formatValue = { "${(it * 100).toInt()}% qs" }, sparklineColor = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Box(modifier = cardModifier) {
                                WritingTrendCard(
                                    label = "First-Person", emoji = "🪞", dataPoints = metrics.firstPersonUsage,
                                    formatValue = { "${(it * 100).toInt()}% self" }, sparklineColor = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        fun List<Pair<Long, Float>>.avgScore(): Float {
                            if (isEmpty()) return 0f
                            val avg = map { it.second }.average().toFloat()
                            return if (avg.isNaN()) 0f else avg
                        }

                        val avgAgency = metrics.agencyScore.avgScore()
                        val avgModality = metrics.epistemicModality.avgScore()
                        val avgHorizon = metrics.temporalHorizon.avgScore()
                        val avgPacing = metrics.syntacticPacing.avgScore()
                        val avgProcessing = metrics.processingMarkers.avgScore()
                        val avgFuturePast = metrics.futureVsPast.avgScore()

                        CognitiveRadarChart(
                            agencyScore = avgAgency, epistemicModality = avgModality,
                            temporalHorizon = avgHorizon, syntacticPacing = avgPacing,
                            processingMarkers = avgProcessing
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Compact Gauges side-by-side
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GradientGauge(title = "Future vs Past", leftLabel = "Past", rightLabel = "Future", score = avgFuturePast, modifier = Modifier.weight(1f))
                            GradientGauge(title = "Agency", leftLabel = "Helpless", rightLabel = "Active", score = avgAgency, modifier = Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GradientGauge(title = "Certainty", leftLabel = "Rumination", rightLabel = "Certain", score = avgModality, modifier = Modifier.weight(1f))
                            GradientGauge(title = "Horizon", leftLabel = "Concrete", rightLabel = "Abstract", score = avgHorizon, modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                Text("Not enough data yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Semantic Themes
            var showThemeSelection by remember { mutableStateOf(false) }

            if (showThemeSelection) {
                ThemeSelectionDialog(
                    vm = vm, 
                    onDismiss = { showThemeSelection = false },
                    onSave = { 
                        coroutineScope.launch { state.refreshInsights(vm.selectedThemes) }
                    }
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("Semantic Themes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { showThemeSelection = true }, enabled = vm.isEngineActive) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Themes", tint = if (vm.isEngineActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                }
            }

            if (vm.isEngineActive) {
                if (state.isThemesLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else if (state.themeClusters?.isNotEmpty() == true) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        state.themeClusters!!.forEach { (theme, entries) ->
                            ThemeClusterCard(theme = theme, entries = entries, modifier = Modifier.width(280.dp).fillMaxHeight())
                        }
                    }
                } else {
                    Text("Not enough data to form semantic themes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Laniakea Core engine is offline. Initialize it to identify and group your memories by semantic theme.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        FilledTonalButton(onClick = { vm.initializeEngine() }, enabled = !vm.isEngineLoading) {
                            Text(if (vm.isEngineLoading) "Initializing Core..." else "Initialize Core")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ThemeSelectionDialog(vm: LaniakeaViewModel, onDismiss: () -> Unit, onSave: () -> Unit) {
    val allThemes = SemanticManager.richThemes.keys.toList()
    var selectedThemes by remember { mutableStateOf(vm.selectedThemes.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize Themes") },
        text = {
            Column {
                Text("Select the themes you want to track in your insights.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(allThemes.size) { index ->
                        val theme = allThemes[index]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                val newSet = selectedThemes.toMutableSet()
                                if (newSet.contains(theme)) newSet.remove(theme) else newSet.add(theme)
                                selectedThemes = newSet
                            }.padding(vertical = 4.dp)
                        ) {
                            Checkbox(checked = selectedThemes.contains(theme), onCheckedChange = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(theme)
                        }
                    }
                }
            }
        },
        confirmButton = { 
            TextButton(onClick = { 
                vm.updateSelectedThemes(selectedThemes.toList())
                onSave()
                onDismiss() 
            }) { Text("Save") } 
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ThemeClusterCard(theme: String, entries: List<DiaryEntry>, modifier: Modifier = Modifier) {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val isTablet = with(density) { windowInfo.containerSize.width.toDp() > 600.dp }

    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(theme, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(modifier = Modifier.height(8.dp))
            entries.take(3).forEach { entry ->
                Text(
                    text = "• " + entry.content, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    maxLines = if (isTablet) Int.MAX_VALUE else 3,
                    overflow = if (isTablet) TextOverflow.Clip else TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun InfoSection(title: String, content: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
