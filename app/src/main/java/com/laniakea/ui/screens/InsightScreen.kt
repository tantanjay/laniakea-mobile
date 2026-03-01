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
import com.laniakea.ui.components.insight.MomentumGauge
import com.laniakea.ui.components.shared.InsightBox

@Composable
fun InsightScreen(padding: PaddingValues, vm: LaniakeaViewModel) {
    var showDisclaimer by remember { mutableStateOf(false) }

    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = { showDisclaimer = false },
            confirmButton = {
                TextButton(onClick = { showDisclaimer = false }) {
                    Text("Got it")
                }
            },
            title = {
                Text(
                    text = "Vibe vs. Reality",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DisclaimerSection(
                        title = "🧠 How your \"Latent Vibe\" works",
                        content = "We use a private, on-device AI to look at the patterns in your writing—not just the words, but the emotional \"weight\" behind them."
                    )
                    DisclaimerSection(
                        title = "⚖️ The \"Mirror\" Effect",
                        content = "Sometimes, your Manual Mood (how you feel) and your Latent Vibe (how you write) won't match. We call this the Emotional Gap."
                    )
                    DisclaimerSection(
                        title = "The Benefit",
                        content = "This gap can help you see subtext in your own life—like when you're feeling \"Fine\" but writing with high-stress patterns."
                    )
                    DisclaimerSection(
                        title = "The Limitation",
                        content = "AI is literal. If you use sarcasm—like writing \"Oh great, another wonderful day in paradise\" while having a terrible day—the AI might see the \"wonderful\" words and miss your irony."
                    )
                    Text(
                        text = "Important Note: Math cannot compute the soul. This AI is a tool for reflection, not a diagnosis. You are always the final authority on how you feel. Your data stays on your device, protected by mathematical noise and encryption.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }
        )
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
            text = "Daily Insights",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )
        )

        // Sentiment Trajectory Section
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Momentum Analysis",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { showDisclaimer = true }) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Disclaimer Information",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                tonalElevation = 2.dp,
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    MomentumGauge(
                        manualScore = vm.manualMomentum.first,
                        manualStatus = vm.manualMomentum.second,
                        aiScore = vm.aiMomentum.first,
                        aiStatus = vm.aiMomentum.second,
                        manualTrend = vm.manualMomentum.third,
                        aiTrend = vm.aiMomentum.third
                    )
                }
            }
        }

        // Deep Dive Insight
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "The Narrative Gap",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            InsightBox(
                manualScore = vm.manualMomentum.first,
                manualStatus = vm.manualMomentum.second,
                aiScore = vm.aiMomentum.first,
                aiStatus = vm.aiMomentum.second
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun DisclaimerSection(title: String, content: String) {
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
