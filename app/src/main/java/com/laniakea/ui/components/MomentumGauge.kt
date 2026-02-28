package com.laniakea.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MomentumGauge(
    manualScore: Float,
    manualStatus: String,
    aiScore: Float,
    aiStatus: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatusIndicator("Perceived Mood", manualStatus, Color.Cyan, "(How you felt)")
            StatusIndicator("Latent Sentiment", aiStatus, Color(0xFFBB86FC), "(How you wrote)")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(280.dp, 140.dp)) {
                val center = Offset(size.width / 2, size.height)
                val radius = size.width / 2
                val strokeWidth = 35f

                // 1. Draw Background Segments (The Color Track)
                // Total sweep is 180 degrees.

                // Sharp Decline (Crimson)
                drawArc(
                    color = Color(0xFFDC143C).copy(alpha = 0.2f),
                    startAngle = 180f,
                    sweepAngle = 72f,
                    useCenter = false,
                    style = Stroke(strokeWidth, cap = StrokeCap.Butt)
                )
                // Declining/Stable (Neutral/Orange)
                drawArc(
                    color = Color.LightGray.copy(alpha = 0.2f),
                    startAngle = 252f,
                    sweepAngle = 36f,
                    useCenter = false,
                    style = Stroke(strokeWidth, cap = StrokeCap.Butt)
                )
                // Improving (Spring Green)
                drawArc(
                    color = Color(0xFF00FF7F).copy(alpha = 0.2f),
                    startAngle = 288f,
                    sweepAngle = 72f,
                    useCenter = false,
                    style = Stroke(strokeWidth, cap = StrokeCap.Butt)
                )

                // 2. Draw Needles
                drawNeedle(manualScore, center, radius, Color.Cyan, 10f)
                drawNeedle(aiScore, center, radius * 0.75f, Color(0xFFBB86FC), 7f)

                // 3. Center Pivot
                drawCircle(color = Color.DarkGray, radius = 12f, center = center)
            }
        }
    }
}

private fun DrawScope.drawNeedle(
    score: Float,
    center: Offset,
    length: Float,
    color: Color,
    width: Float
) {
    // Map -100..100 to 180..360 degrees
    val angle = 180f + ((score + 100f) / 200f * 180f)
    val rad = Math.toRadians(angle.toDouble())

    drawLine(
        color = color,
        start = center,
        end = Offset(
            center.x + length * cos(rad).toFloat(),
            center.y + length * sin(rad).toFloat()
        ),
        strokeWidth = width,
        cap = StrokeCap.Round
    )
}

@Composable
fun StatusIndicator(
    label: String,
    status: String,
    color: Color,
    subLabel: String? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        if (subLabel != null) {
            Text(
                text = subLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = color
            )
        )
    }
}

@Composable
fun InsightBox(
    manualScore: Float,
    manualStatus: String,
    aiScore: Float,
    aiStatus: String
) {
    val insight = remember(manualScore, aiScore) {
        getDivergenceInsight(manualScore, manualStatus, aiScore, aiStatus)
    }

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("💡", fontSize = 20.sp)
                }
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = insight,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp
            )
        }
    }
}
fun getDivergenceInsight(
    manualScore: Float,
    manualStatus: String,
    aiScore: Float,
    aiStatus: String
): String {
    return when {
        // Case 1: Perfect Alignment
        manualStatus == aiStatus -> "Your self-perception and writing style are perfectly in sync today."

        // Case 2: Positive Masking (Blue needle high, Purple needle low)
        manualScore > 10 && aiScore < 5 ->
            "Insight: You're pushing for a positive outlook, but your words suggest a more cautious, stable pace."

        // Case 3: Subconscious Optimism (Purple needle higher than Blue)
        aiScore > manualScore + 15 ->
            "Insight: Your writing carries more optimism than you're giving yourself credit for!"

        // Case 4: Critical Divergence (Manual is Good, AI is Bad)
        manualScore > 0 && aiScore < -15 ->
            "Alert: There's a notable gap between your reported mood and your writing context. Consider a self-care break."

        // Default/Neutral
        else -> "You are maintaining a steady balance between your reported feelings and your inner context."
    }
}