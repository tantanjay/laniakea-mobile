package com.laniakea.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laniakea.viewmodel.LaniakeaViewModel
import com.laniakea.ui.components.insight.WritingTrendCard
import com.laniakea.ui.components.insight.WeeklyDigestCard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun InsightScreen(padding: PaddingValues, vm: LaniakeaViewModel) {
    var showInfo by remember { mutableStateOf(false) }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text("Got it")
                }
            },
            title = {
                Text(
                    text = "About Writing Reflections",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    InfoSection(
                        title = "📝 What This Measures",
                        content = "These metrics observe the structure of your writing—length, variety, self-reference, and temporal orientation. They answer \"How has my writing changed?\" without judging how you feel."
                    )
                    InfoSection(
                        title = "📊 How Trends Work",
                        content = "Each sparkline shows your last 30 entries in chronological order. The arrow (↑↓→) compares your recent average to your earlier average."
                    )
                    InfoSection(
                        title = "🔒 Privacy",
                        content = "All analysis runs on your device. Your entries are decrypted only in memory for analysis and never leave your phone."
                    )
                    Text(
                        text = "Note: These are structural observations, not psychological assessments. Short entries aren't \"bad\" and long entries aren't \"good\" — they're simply patterns worth noticing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }
        )
    }

    // Load writing metrics and weekly digest (cached to avoid fetching on every tab visit)
    LaunchedEffect(Unit) {
        if (vm.writingMetrics == null) {
            vm.isMetricsLoading = true
            try {
                vm.writingMetrics = vm.analyzeWritingTrends()
            } finally {
                vm.isMetricsLoading = false
            }
        }

        if (vm.weeklyDigest == null || vm.themeClusters == null) {
            vm.refreshInsights()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Writing Reflections",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )
        )

        var isDigestExpanded by remember { mutableStateOf(true) }

        // Weekly Digest Section
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(
                title = "This Week's Reflections",
                isExpanded = isDigestExpanded,
                onToggle = { isDigestExpanded = !isDigestExpanded }
            )

            AnimatedVisibility(visible = isDigestExpanded) {
                if (vm.isDigestLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    vm.weeklyDigest?.let { digest ->
                        WeeklyDigestCard(digest = digest)
                    }
                }
            }
        }

        var isWritingTrendsExpanded by remember { mutableStateOf(false) }

        // Writing Trends Section
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(
                title = "How Your Writing Has Changed",
                isExpanded = isWritingTrendsExpanded,
                onToggle = { isWritingTrendsExpanded = !isWritingTrendsExpanded },
                actionButton = {
                    IconButton(onClick = { showInfo = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About Writing Reflections",
                            tint = if (isWritingTrendsExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            )

            AnimatedVisibility(visible = isWritingTrendsExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    if (vm.isMetricsLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val metrics = vm.writingMetrics
                        if (metrics != null && metrics.entryLengths.isNotEmpty()) {
                            WritingTrendCard(
                                label = "Entry Length",
                                emoji = "📏",
                                dataPoints = metrics.entryLengths,
                                formatValue = { "${it.toInt()} words" },
                                sparklineColor = MaterialTheme.colorScheme.primary
                            )

                            WritingTrendCard(
                                label = "Vocabulary Diversity",
                                emoji = "🔤",
                                dataPoints = metrics.vocabularyDiversity,
                                formatValue = { "${(it * 100).toInt()}% unique" },
                                sparklineColor = MaterialTheme.colorScheme.tertiary
                            )

                            WritingTrendCard(
                                label = "Question Frequency",
                                emoji = "❓",
                                dataPoints = metrics.questionFrequency,
                                formatValue = { "${(it * 100).toInt()}% questions" },
                                sparklineColor = MaterialTheme.colorScheme.secondary
                            )

                            WritingTrendCard(
                                label = "First-Person Usage",
                                emoji = "🪞",
                                dataPoints = metrics.firstPersonUsage,
                                formatValue = { "${(it * 100).toInt()}% self-ref" },
                                sparklineColor = MaterialTheme.colorScheme.primary
                            )

                            WritingTrendCard(
                                label = "Future vs Past",
                                emoji = "⏳",
                                dataPoints = metrics.futureVsPast,
                                formatValue = { v ->
                                    when {
                                        v > 0.2f -> "Future-focused"
                                        v < -0.2f -> "Past-focused"
                                        else -> "Balanced"
                                    }
                                },
                                sparklineColor = MaterialTheme.colorScheme.tertiary
                            )

                            WritingTrendCard(
                                label = "Syntactic Pacing",
                                emoji = "🔗",
                                dataPoints = metrics.syntacticPacing,
                                formatValue = { "%.2f conjs/sent".format(it) },
                                sparklineColor = MaterialTheme.colorScheme.secondary
                            )

                            WritingTrendCard(
                                label = "Agency (Active vs Passive)",
                                emoji = "💪",
                                dataPoints = metrics.agencyScore,
                                formatValue = { v ->
                                    when {
                                        v > 0.3f -> "Active"
                                        v < -0.3f -> "Passive"
                                        else -> "Balanced"
                                    }
                                },
                                sparklineColor = MaterialTheme.colorScheme.primary
                            )

                            WritingTrendCard(
                                label = "Epistemic Modality",
                                emoji = "⚖️",
                                dataPoints = metrics.epistemicModality,
                                formatValue = { v ->
                                    when {
                                        v > 0.5f -> "Absolute"
                                        v < -0.5f -> "Hedged"
                                        else -> "Balanced"
                                    }
                                },
                                sparklineColor = MaterialTheme.colorScheme.tertiary
                            )

                            WritingTrendCard(
                                label = "Processing Markers",
                                emoji = "🧠",
                                dataPoints = metrics.processingMarkers,
                                formatValue = { "${it.toInt()} markers" },
                                sparklineColor = MaterialTheme.colorScheme.secondary
                            )

                            WritingTrendCard(
                                label = "Temporal Horizon",
                                emoji = "🔭",
                                dataPoints = metrics.temporalHorizon,
                                formatValue = { v ->
                                    when {
                                        v > 0.6f -> "Abstract"
                                        v < 0.4f -> "Concrete"
                                        else -> "Neutral"
                                    }
                                },
                                sparklineColor = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Box(
                                    modifier = Modifier.padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Write a few entries to see your writing patterns emerge.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Thematic Clusters
        LaunchedEffect(vm.isEngineActive) {
            if (vm.isEngineActive && vm.themeClusters == null) {
                vm.refreshInsights()
            }
        }

        var showThemeSelection by remember { mutableStateOf(false) }

        if (showThemeSelection) {
            ThemeSelectionDialog(
                vm = vm,
                onDismiss = { showThemeSelection = false }
            )
        }

        var isThemesExpanded by remember { mutableStateOf(false) }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(
                title = "Semantic Themes",
                isExpanded = isThemesExpanded,
                onToggle = { isThemesExpanded = !isThemesExpanded },
                actionButton = {
                    IconButton(
                        onClick = { showThemeSelection = true },
                        enabled = vm.isEngineActive
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Themes",
                            tint = if (vm.isEngineActive) {
                                if (isThemesExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            }
                        )
                    }
                }
            )

            AnimatedVisibility(visible = isThemesExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (vm.isEngineActive) {
                        if (vm.isThemesLoading) {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (vm.themeClusters?.isNotEmpty() == true) {
                            vm.themeClusters!!.forEach { (theme, entries) ->
                                ThemeClusterCard(theme = theme, entries = entries)
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
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                FilledTonalButton(
                                    onClick = { vm.initializeEngine() },
                                    enabled = !vm.isEngineLoading,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                ) {
                                    Text(if (vm.isEngineLoading) "Initializing Core..." else "Initialize Core")
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ThemeSelectionDialog(vm: LaniakeaViewModel, onDismiss: () -> Unit) {
    val allThemes = listOf(
        "Relationships & Connection",
        "Career & Purpose",
        "Goals & Ambition",
        "Inner Reflection",
        "Emotional Wellbeing",
        "Physical Wellbeing",
        "Stress & Anxiety",
        "Learning & Curiosity",
        "Creativity & Expression",
        "Uncertainty & Waiting",
        "Gratitude & Joy",
        "Challenges & Resilience",
        "Leisure & Recreation",
        "Travel & Exploration",
        "Food & Dining",
        "Daily Routine & Chores"
    )

    var selectedThemes by remember { mutableStateOf(vm.selectedThemes.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize Themes") },
        text = {
            Column {
                Text(
                    "Select the themes you want to track in your insights.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(allThemes.size) { index ->
                        val theme = allThemes[index]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val newSet = selectedThemes.toMutableSet()
                                    if (newSet.contains(theme)) {
                                        newSet.remove(theme)
                                    } else {
                                        newSet.add(theme)
                                    }
                                    selectedThemes = newSet
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = selectedThemes.contains(theme),
                                onCheckedChange = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(theme)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    vm.updateSelectedThemes(selectedThemes.toList())
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ThemeClusterCard(theme: String, entries: List<com.laniakea.data.DiaryEntry>) {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val isTablet = with(density) { windowInfo.containerSize.width.toDp() > 600.dp }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = theme,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            entries.take(3).forEach { entry ->
                Text(
                    text = "• " + entry.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    maxLines = if (isTablet) Int.MAX_VALUE else 2,
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
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    actionButton: @Composable (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = if (isExpanded) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isExpanded) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            }
        ),
        tonalElevation = if (isExpanded) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (actionButton != null) {
                    actionButton()
                }
                
                val rotationAngle by animateFloatAsState(
                    targetValue = if (isExpanded) 180f else 0f,
                    label = "ArrowRotation"
                )
                
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer(rotationZ = rotationAngle)
                )
            }
        }
    }
}
