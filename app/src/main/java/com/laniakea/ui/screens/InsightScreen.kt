package com.laniakea.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laniakea.viewmodel.LaniakeaViewModel
import com.laniakea.ui.components.insight.WritingTrendCard

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

    // Load writing metrics
    LaunchedEffect(Unit) {
        vm.isMetricsLoading = true
        try {
            vm.writingMetrics = vm.analyzeWritingTrends()
        } finally {
            vm.isMetricsLoading = false
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

        // Writing Trends Section
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "How Your Writing Has Changed",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { showInfo = true }) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "About Writing Reflections",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
            }

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

        // Thematic Clusters
        var themeClusters by remember { mutableStateOf<Map<String, List<com.laniakea.data.DiaryEntry>>>(emptyMap()) }
        var isThemesLoading by remember { mutableStateOf(false) }

        LaunchedEffect(vm.isEngineActive) {
            if (vm.isEngineActive) {
                isThemesLoading = true
                try {
                    themeClusters = vm.getThemeClusters()
                } finally {
                    isThemesLoading = false
                }
            }
        }

        if (vm.isEngineActive) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Semantic Themes",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                if (isThemesLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (themeClusters.isNotEmpty()) {
                    themeClusters.forEach { (theme, entries) ->
                        ThemeClusterCard(theme = theme, entries = entries)
                    }
                } else {
                    Text("Not enough data to form semantic themes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ThemeClusterCard(theme: String, entries: List<com.laniakea.data.DiaryEntry>) {
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
                    text = "• " + entry.content.take(60) + if(entry.content.length > 60) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
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
