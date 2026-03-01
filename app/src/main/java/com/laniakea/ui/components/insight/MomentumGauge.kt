package com.laniakea.ui.components.insight

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.laniakea.ui.components.shared.StatusCard
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MomentumGauge(
    manualScore: Float,
    manualStatus: String,
    aiScore: Float,
    aiStatus: String,
    manualTrend: List<Float> = emptyList(),
    aiTrend: List<Float> = emptyList()
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

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

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

                // --- Soft Background Glow ---
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(onSurfaceColor.copy(alpha = 0.05f), Color.Transparent),
                        center = center,
                        radius = radius * 1.5f
                    ),
                    center = center
                )

                // --- Track Background ---
                drawArc(
                    color = onSurfaceColor.copy(alpha = 0.1f),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                )

                // --- Colored Segments (Decline / Stable / Improving) ---
                val sections = listOf(
                    Triple(180f, 60f, Color(0xFFE91E63)), // Red-ish for decline
                    Triple(240f, 60f, onSurfaceColor.copy(alpha = 0.2f)), // Neutral
                    Triple(300f, 60f, Color(0xFF00E676)) // Green-ish for improvement
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

                fun drawMiniTrend(trend: List<Float>, color: Color) {
                    if (trend.isEmpty()) return

                    val graphHeight = radius * 0.35f
                    val graphRadius = radius - strokeWidth - 12f
                    val scaledTrend = trend.map { it * graphHeight }
                    val stepAngle = 180f / (scaledTrend.size - 1).coerceAtLeast(1)

                    val pathPoints = scaledTrend.mapIndexed { i, value ->
                        val angle = 180f + i * stepAngle
                        val rad = Math.toRadians(angle.toDouble())
                        Offset(
                            center.x + (graphRadius + value) * cos(rad).toFloat(),
                            center.y + (graphRadius + value) * sin(rad).toFloat()
                        )
                    }

                    // Glow line
                    for (i in 0 until pathPoints.size - 1) {
                        drawLine(
                            color = color.copy(alpha = 0.2f),
                            start = pathPoints[i],
                            end = pathPoints[i + 1],
                            strokeWidth = 6f,
                            cap = StrokeCap.Round
                        )
                    }

                    // Main line
                    for (i in 0 until pathPoints.size - 1) {
                        drawLine(
                            color = color.copy(alpha = 0.7f),
                            start = pathPoints[i],
                            end = pathPoints[i + 1],
                            strokeWidth = 3f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                drawMiniTrend(aiTrend, tertiaryColor)
                drawMiniTrend(manualTrend, primaryColor)

                // --- Needles ---
                drawNeedle(animatedManualScore, center, radius, onSurfaceColor.copy(alpha = 0.15f), 14f, offset = 4f)
                drawNeedle(animatedManualScore, center, radius, primaryColor, 12f)
                drawNeedle(animatedAiScore, center, radius * 0.78f, tertiaryColor, 8f)

                // --- Center Pivot ---
                drawCircle(color = onSurfaceColor.copy(alpha = 0.8f), radius = 20f, center = center)
                drawCircle(color = onSurfaceColor.copy(alpha = 0.5f), radius = 12f, center = center)
                drawCircle(color = onSurfaceColor.copy(alpha = 0.2f), radius = 4f, center = center)
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
                color = primaryColor,
                modifier = Modifier.weight(1f)
            )
            StatusCard(
                label = "Latent Sentiment",
                subLabel = "(How you wrote)",
                status = aiStatus,
                color = tertiaryColor,
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