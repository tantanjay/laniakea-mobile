package com.laniakea.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
    val animatedManualScore by animateFloatAsState(
        targetValue = manualScore,
        animationSpec = tween(durationMillis = 1500),
        label = "manualScore"
    )
    val animatedAiScore by animateFloatAsState(
        targetValue = aiScore,
        animationSpec = tween(durationMillis = 1800),
        label = "aiScore"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(300.dp, 180.dp)) {
                val center = Offset(size.width / 2, size.height - 20f)
                val radius = size.width / 2 - 30f
                val strokeWidth = 28f

                // 1. Soft Background Glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Gray.copy(alpha = 0.05f), Color.Transparent),
                        center = center,
                        radius = radius * 1.5f
                    ),
                    center = center
                )

                // 2. Track Background
                drawArc(
                    color = Color.LightGray.copy(alpha = 0.1f),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                )

                // 3. Colored Segments (Gradient-style transition)
                val sections = listOf(
                    Triple(180f, 60f, Color(0xFFE91E63)), // Decline (Crimson/Pink)
                    Triple(240f, 60f, Color.LightGray),    // Stable
                    Triple(300f, 60f, Color(0xFF00E676))  // Improving (Spring Green)
                )

                sections.forEach { (start, sweep, color) ->
                    drawArc(
                        color = color.copy(alpha = 0.35f),
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(strokeWidth, cap = StrokeCap.Butt)
                    )
                }

                // 4. Needles
                // Shadow for manual needle
                drawNeedle(animatedManualScore, center, radius, Color.Black.copy(alpha = 0.2f), 14f, offset = 4f)
                // Manual Needle (Perceive Mood)
                drawNeedle(animatedManualScore, center, radius, Color.Cyan, 12f)
                
                // AI Needle (Latent Sentiment)
                drawNeedle(animatedAiScore, center, radius * 0.78f, Color(0xFFBB86FC), 8f)

                // 5. Center Pivot
                drawCircle(color = Color(0xFF212121), radius = 20f, center = center)
                drawCircle(color = Color.DarkGray, radius = 12f, center = center)
                drawCircle(color = Color.LightGray, radius = 4f, center = center)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Status Indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusCard(
                label = "Perceive Mood",
                subLabel = "(How you felt)",
                status = manualStatus,
                color = Color.Cyan,
                modifier = Modifier.weight(1f)
            )
            StatusCard(
                label = "Latent Sentiment",
                subLabel = "(How you wrote)",
                status = aiStatus,
                color = Color(0xFFBB86FC),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun DrawScope.drawNeedle(
    score: Float,
    center: Offset,
    length: Float,
    color: Color,
    width: Float,
    offset: Float = 0f
) {
    val angle = 180f + ((score + 100f) / 200f * 180f)
    val rad = Math.toRadians(angle.toDouble())

    val drawCenter = if (offset != 0f) center.copy(x = center.x + offset, y = center.y + offset) else center
    
    val end = Offset(
        drawCenter.x + length * cos(rad).toFloat(),
        drawCenter.y + length * sin(rad).toFloat()
    )

    drawLine(
        color = color,
        start = drawCenter,
        end = end,
        strokeWidth = width,
        cap = StrokeCap.Round
    )
}

@Composable
fun StatusCard(
    label: String,
    subLabel: String,
    status: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Text(
                text = subLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    color = color,
                    letterSpacing = 0.5.sp
                )
            )
        }
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
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("💡", fontSize = 26.sp)
                }
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = insight,
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer
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
        manualStatus == aiStatus -> "Your self-perception and writing style are perfectly in sync today."
        manualScore > 10 && aiScore < 5 ->
            "Insight: You're pushing for a positive outlook, but your words suggest a more cautious, stable pace."
        aiScore > manualScore + 15 ->
            "Insight: Your writing carries more optimism than you're giving yourself credit for!"
        manualScore > 0 && aiScore < -15 ->
            "Alert: There's a notable gap between your reported mood and your writing context. Consider a self-care break."
        else -> "You are maintaining a steady balance between your reported feelings and your inner context."
    }
}
