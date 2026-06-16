package com.laniakea.ui.components.insight

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GradientGauge(
    title: String,
    leftLabel: String,
    rightLabel: String,
    score: Float, // assumed -1f to 1f
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary
    )
) {
    var animationPlayed by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        animationPlayed = true
    }

    val normalizedScore = (score.coerceIn(-2f, 2f) + 2f) / 4f
    val animatedScore by animateFloatAsState(
        targetValue = if (animationPlayed) normalizedScore else 0.5f,
        animationSpec = tween(durationMillis = 1000),
        label = "gauge_animation"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 0.5.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            val onSurfaceColor = MaterialTheme.colorScheme.onSurface
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f),
                contentAlignment = Alignment.BottomCenter
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 12.dp.toPx()
                    val arcRectSize = Size(size.width, size.height * 2)
                    
                    // Draw track
                    drawArc(
                        color = colors.first().copy(alpha = 0.1f),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        size = arcRectSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Draw gradient
                    drawArc(
                        brush = Brush.horizontalGradient(colors),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        size = arcRectSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Draw indicator needle
                    val angle = 180f + (180f * animatedScore)
                    val angleRad = angle * (Math.PI / 180f)
                    val radius = size.width / 2f
                    val center = Offset(size.width / 2f, size.height)
                    
                    val needleInnerRadius = radius - strokeWidth * 1.5f
                    val needleOuterRadius = radius + strokeWidth * 0.5f

                    val start = Offset(
                        x = center.x + needleInnerRadius * cos(angleRad).toFloat(),
                        y = center.y + needleInnerRadius * sin(angleRad).toFloat()
                    )
                    
                    val end = Offset(
                        x = center.x + needleOuterRadius * cos(angleRad).toFloat(),
                        y = center.y + needleOuterRadius * sin(angleRad).toFloat()
                    )

                    drawLine(
                        color = onSurfaceColor,
                        start = start,
                        end = end,
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    leftLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    rightLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}
