package com.laniakea.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laniakea.LaniakeaApplication
import com.laniakea.ui.components.insight.CognitiveRadarChart
import com.laniakea.ui.components.insight.GradientGauge
import com.laniakea.ui.components.insight.PeriodDigestCard
import com.laniakea.ui.components.insight.WritingTrendCard
import com.laniakea.ui.components.insight.ThemeSelectionDialog
import com.laniakea.ui.components.insight.ThemeClusterCard
import com.laniakea.ui.components.insight.InfoSection
import com.laniakea.viewmodel.InsightState
import com.laniakea.viewmodel.LaniakeaViewModel
import kotlinx.coroutines.launch
import kotlin.math.atanh

@Composable
fun InsightScreen(padding: PaddingValues, vm: LaniakeaViewModel) {
    val context = LocalContext.current
    val app = context.applicationContext as LaniakeaApplication
    val state = remember { 
        val prefs = context.getSharedPreferences("insight_prefs", android.content.Context.MODE_PRIVATE)
        InsightState(
            analyticsManager = app.container.analyticsManager,
            digestManager = app.container.digestManager,
            semanticManager = app.container.semanticManager,
            prefs = prefs
        ) 
    }
    val coroutineScope = rememberCoroutineScope()
    
    if (state.showInfo) {
        AlertDialog(
            onDismissRequest = { state.showInfo = false },
            confirmButton = { TextButton(onClick = { state.showInfo = false }) { Text("Got it") } },
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
                        IconButton(onClick = { state.showInfo = true }) {
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
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Generating period digest...", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
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
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Calculating writing trends...", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
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

                        val avgPacing = metrics.syntacticPacing.avgScore()
                        val avgProcessing = metrics.processingMarkers.avgScore()
                        val avgFuturePast = metrics.futureVsPast.avgScore()

                        // Desaturate vibe scores: scaleVibeSmooth uses tanh(raw/0.1)*2
                        // which pushes nearly all scores to ±1.8-2.0, destroying variance.
                        // atanh reverses this to recover the original linear projection.
                        fun desaturate(score: Float): Float {
                            val clamped = (score / 2f).coerceIn(-0.95f, 0.95f)
                            val raw = atanh(clamped.toDouble()).toFloat() * 0.1f
                            // Raw projections typically in [-0.2, 0.2]; rescale to [-1.5, 1.5]
                            return (raw / 0.13f).coerceIn(-1.5f, 1.5f)
                        }

                        val avgAgency = desaturate(metrics.agencyScore.avgScore())
                        val avgModality = desaturate(metrics.epistemicModality.avgScore())
                        val avgHorizon = desaturate(metrics.temporalHorizon.avgScore())

                        CognitiveRadarChart(
                            agencyScore = avgAgency, epistemicModality = avgModality,
                            temporalHorizon = avgHorizon, syntacticPacing = avgPacing,
                            processingMarkers = avgProcessing
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Compact Gauges side-by-side
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GradientGauge(title = "Future vs Past", leftLabel = "Past", rightLabel = "Future", score = avgFuturePast, minScore = -1f, maxScore = 1f, modifier = Modifier.weight(1f))
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
            if (state.showThemeSelection) {
                ThemeSelectionDialog(
                    vm = vm, 
                    onDismiss = { state.showThemeSelection = false },
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
                IconButton(onClick = { state.showThemeSelection = true }, enabled = vm.isEngineActive) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Themes", tint = if (vm.isEngineActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                }
            }

            if (vm.isEngineActive) {

                if (state.isThemesLoading) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Analyzing semantic themes...", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
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
