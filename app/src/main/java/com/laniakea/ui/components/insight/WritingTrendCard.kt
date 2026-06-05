package com.laniakea.ui.components.insight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WritingTrendCard(
    label: String,
    emoji: String,
    dataPoints: List<Pair<Long, Float>>,
    formatValue: (Float) -> String = { String.format("%.0f", it) },
    sparklineColor: Color = MaterialTheme.colorScheme.primary
) {
    val values = remember(dataPoints) { dataPoints.map { it.second } }
    val currentValue = remember(values) { values.lastOrNull() }
    val trendArrow = remember(values) {
        if (values.size < 2) "→"
        else {
            // Compare average of last 5 vs previous 5
            val recentWindow = values.takeLast(5).average().toFloat()
            val previousWindow = values.dropLast(5).takeLast(5).average().toFloat()
            val delta = recentWindow - previousWindow
            when {
                delta > 0.05f -> "↑"
                delta < -0.05f -> "↓"
                else -> "→"
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 0.5.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji + Label + Value
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(emoji, fontSize = 18.sp)
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                if (currentValue != null) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = formatValue(currentValue),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = trendArrow,
                            style = MaterialTheme.typography.titleMedium,
                            color = when (trendArrow) {
                                "↑" -> MaterialTheme.colorScheme.primary
                                "↓" -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                } else {
                    Text(
                        text = "No data yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Sparkline
            if (values.size >= 2) {
                Sparkline(
                    values = values,
                    modifier = Modifier
                        .width(100.dp)
                        .height(40.dp),
                    color = sparklineColor
                )
            }
        }
    }
}

@Composable
private fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val normalizedValues = remember(values) {
        val min = values.minOrNull() ?: 0f
        val max = values.maxOrNull() ?: 1f
        val range = (max - min).coerceAtLeast(0.001f)
        values.map { (it - min) / range }
    }

    Canvas(modifier = modifier) {
        if (normalizedValues.size < 2) return@Canvas

        val stepX = size.width / (normalizedValues.size - 1).coerceAtLeast(1)
        val paddingY = 4.dp.toPx()
        val graphHeight = size.height - paddingY * 2

        val path = Path()
        normalizedValues.forEachIndexed { index, value ->
            val x = index * stepX
            val y = paddingY + graphHeight * (1f - value)
            if (index == 0) path.moveTo(x, y)
            else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Draw end dot
        val lastX = (normalizedValues.size - 1) * stepX
        val lastY = paddingY + graphHeight * (1f - normalizedValues.last())
        drawCircle(
            color = color,
            radius = 3.dp.toPx(),
            center = Offset(lastX, lastY)
        )
    }
}
