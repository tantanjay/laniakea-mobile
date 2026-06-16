package com.laniakea.ui.components.insight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer

@Composable
fun CognitiveRadarChart(
    agencyScore: Float,
    epistemicModality: Float,
    temporalHorizon: Float,
    syntacticPacing: Float,
    processingMarkers: Float,
    modifier: Modifier = Modifier
) {
    // Normalize values from VibeManager's [-2.0, 2.0] range to 0.0 - 1.0 range
    val normalizedAgency = ((agencyScore.coerceIn(-2f, 2f) + 2f) / 4f)
    val normalizedModality = ((epistemicModality.coerceIn(-2f, 2f) + 2f) / 4f)
    val normalizedHorizon = ((temporalHorizon.coerceIn(-2f, 2f) + 2f) / 4f)
    val normalizedPacing = (syntacticPacing / 3f).coerceIn(0f, 1f)
    val normalizedProcessing = (processingMarkers / 10f).coerceIn(0f, 1f)

    val dataPoints = listOf(
        normalizedAgency to "Agency",
        normalizedModality to "Certainty",
        normalizedHorizon to "Abstract",
        normalizedPacing to "Complexity",
        normalizedProcessing to "Processing"
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
                "Cognitive Footprint",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            val primaryColor = MaterialTheme.colorScheme.primary
            val onSurfaceColor = MaterialTheme.colorScheme.onSurfaceVariant
            val textMeasurer = rememberTextMeasurer()
            val labelStyle = MaterialTheme.typography.labelSmall.copy(color = onSurfaceColor, fontWeight = FontWeight.Medium)
            
            Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2 * 0.7f
                    val center = Offset(size.width / 2, size.height / 2)
                    val angleStep = (2 * Math.PI / dataPoints.size).toFloat()

                    // Draw web background
                    for (step in 1..4) {
                        val stepRadius = radius * (step / 4f)
                        val webPath = Path()
                        for (i in dataPoints.indices) {
                            val angle = i * angleStep - Math.PI / 2
                            val x = center.x + stepRadius * cos(angle).toFloat()
                            val y = center.y + stepRadius * sin(angle).toFloat()
                            if (i == 0) webPath.moveTo(x, y)
                            else webPath.lineTo(x, y)
                        }
                        webPath.close()
                        drawPath(
                            path = webPath,
                            color = onSurfaceColor.copy(alpha = 0.2f),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }

                    // Draw axes lines
                    for (i in dataPoints.indices) {
                        val angle = i * angleStep - Math.PI / 2
                        val x = center.x + radius * cos(angle).toFloat()
                        val y = center.y + radius * sin(angle).toFloat()
                        drawLine(
                            color = onSurfaceColor.copy(alpha = 0.2f),
                            start = center,
                            end = Offset(x, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Draw data polygon
                    val dataPath = Path()
                    val dotOffsets = mutableListOf<Offset>()
                    for (i in dataPoints.indices) {
                        val value = dataPoints[i].first
                        val angle = i * angleStep - Math.PI / 2
                        // Ensure a minimum radius of 10% so the shape doesn't mathematically collapse to a microscopic dot
                        val paddedValue = 0.1f + (value * 0.9f)
                        val pointRadius = radius * paddedValue
                        val x = center.x + pointRadius * cos(angle).toFloat()
                        val y = center.y + pointRadius * sin(angle).toFloat()
                        dotOffsets.add(Offset(x, y))
                        
                        if (i == 0) dataPath.moveTo(x, y)
                        else dataPath.lineTo(x, y)
                    }
                    dataPath.close()

                    drawPath(
                        path = dataPath,
                        color = primaryColor.copy(alpha = 0.3f),
                        style = Fill
                    )
                    drawPath(
                        path = dataPath,
                        color = primaryColor,
                        style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round)
                    )

                    for (dot in dotOffsets) {
                        drawCircle(
                            color = primaryColor,
                            radius = 4.dp.toPx(),
                            center = dot
                        )
                    }
                    // Draw text labels at the end of the axes
                    for (i in dataPoints.indices) {
                        val label = dataPoints[i].second
                        val angle = i * angleStep - Math.PI / 2
                        // Place labels slightly outside the outermost ring
                        val labelRadius = radius * 1.2f
                        val labelX = center.x + labelRadius * cos(angle).toFloat()
                        val labelY = center.y + labelRadius * sin(angle).toFloat()
                        
                        val textLayoutResult = textMeasurer.measure(
                            text = androidx.compose.ui.text.AnnotatedString(label),
                            style = labelStyle,
                            maxLines = 1,
                            softWrap = false
                        )
                        val textCenterOffset = Offset(
                            labelX - textLayoutResult.size.width / 2f,
                            labelY - textLayoutResult.size.height / 2f
                        )
                        
                        drawText(
                            textLayoutResult = textLayoutResult,
                            topLeft = textCenterOffset
                        )
                    }
                }
            }
        }
    }
}
