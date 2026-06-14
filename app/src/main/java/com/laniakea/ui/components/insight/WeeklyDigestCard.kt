package com.laniakea.ui.components.insight

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laniakea.data.WeeklyDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun WeeklyDigestCard(digest: WeeklyDigest) {
    val formatter = DateTimeFormatter.ofPattern("MMM d")
    val startDate = Instant.ofEpochMilli(digest.weekStart).atZone(ZoneId.systemDefault()).toLocalDate()
    val endDate = Instant.ofEpochMilli(digest.weekEnd).atZone(ZoneId.systemDefault()).toLocalDate()
    val dateRangeStr = "${startDate.format(formatter)} – ${endDate.format(formatter)}"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 0.5.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📋", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "This Week's Reflection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = dateRangeStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 32.dp, top = 2.dp)
                )
            }

            // Overview
            Text(
                text = "${digest.entryCount} entries across ${digest.activeDays} days",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            // Metrics Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DigestMetricRow(
                        icon = "📏",
                        label = "Avg Length",
                        value = "${digest.avgEntryLength.toInt()} words",
                        arrow = getTrendArrow(digest.avgEntryLength, digest.avgEntryLengthPrior)
                    )
                    DigestMetricRow(
                        icon = "🔤",
                        label = "Vocabulary",
                        value = "${(digest.vocabularyDiversity * 100).toInt()}% unique",
                        arrow = getTrendArrow(digest.vocabularyDiversity, digest.vocabularyDiversityPrior, 0.05f)
                    )
                    DigestMetricRow(
                        icon = "❓",
                        label = "Questions",
                        value = "${(digest.questionRatio * 100).toInt()}%",
                        arrow = getTrendArrow(digest.questionRatio, digest.questionRatioPrior, 0.05f)
                    )
                }
            }

            // Themes (only if available)
            if (digest.dominantThemes.isNotEmpty()) {
                Column {
                    Text(
                        text = "Dominant Themes:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = digest.dominantThemes.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Vibe Score (only if available)
            if (digest.avgVibeScore != null) {
                Column {
                    Text(
                        text = "Vibe Analysis:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val vibeText = when {
                        digest.avgVibeScore > 0.5f -> "Your writing seems to be very positive."
                        digest.avgVibeScore > 0.1f -> "Your writing seems to be on the positive side."
                        digest.avgVibeScore < -0.5f -> "Your writing seems to be very negative."
                        digest.avgVibeScore < -0.1f -> "Your writing seems to be on the negative side."
                        else -> "Your writing seems to be balanced."
                    }
                    Text(
                        text = vibeText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun DigestMetricRow(icon: String, label: String, value: String, arrow: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = arrow,
                style = MaterialTheme.typography.bodyMedium,
                color = when (arrow) {
                    "↑" -> MaterialTheme.colorScheme.primary
                    "↓" -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

private fun getTrendArrow(current: Float, prior: Float?, threshold: Float = 5f): String {
    if (prior == null) return "—"
    val delta = current - prior
    return when {
        delta > threshold -> "↑"
        delta < -threshold -> "↓"
        else -> "→"
    }
}
