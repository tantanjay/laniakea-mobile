package com.laniakea.ui.components.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.laniakea.engine.GraphEdge
import com.laniakea.engine.GraphEngine
import com.laniakea.engine.GraphNode
import com.laniakea.engine.LayoutMode
import com.laniakea.util.*
import com.laniakea.ui.screens.CameraState
import com.laniakea.ui.screens.ColorMode
import com.laniakea.ui.screens.GalaxyStar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The core 3D-projected Canvas that renders background stars, edges, nodes,
 * and Time Warp HUD labels.  Extracted from MapScreen to keep the screen
 * composable under ~200 lines and to isolate the draw/gesture scope.
 */
@Composable
fun ConstellationCanvas(
    visibleNodes: List<GraphNode>,
    visibleEdges: List<GraphEdge>,
    allNodes: List<GraphNode>,
    backgroundStars: List<GalaxyStar>,
    layoutMode: LayoutMode,
    colorMode: ColorMode,
    camera: CameraState,
    onCameraChange: (CameraState) -> Unit,
    graphEngine: GraphEngine,
    isIsolateMode: Boolean,
    selectedNode: GraphNode?,
    onNodeTap: (GraphNode?) -> Unit,
    onNodeDoubleTap: (GraphNode) -> Unit,
    onLayoutSize: (Float, Float) -> Unit,
    glowPulse: Float,
    modifier: Modifier = Modifier,
    showDecorations: Boolean = true
) {
    val textMeasurer = rememberTextMeasurer()
    var activePointers by remember { mutableIntStateOf(0) }
    val currentVisibleNodes by rememberUpdatedState(visibleNodes)
    val currentCamera by rememberUpdatedState(camera)
    val currentOnCameraChange by rememberUpdatedState(onCameraChange)
    val currentIsIsolateMode by rememberUpdatedState(isIsolateMode)
    val currentOnNodeTap by rememberUpdatedState(onNodeTap)
    val currentOnNodeDoubleTap by rememberUpdatedState(onNodeDoubleTap)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        activePointers = event.changes.count { it.pressed }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    var cam = currentCamera
                    if (zoom != 1f) {
                        cam = cam.copy(cameraZ = (cam.cameraZ / zoom).coerceIn(50f, 1800f))
                    }
                    cam = if (activePointers >= 3) {
                        cam.copy(
                            cameraX = cam.cameraX - pan.x * 1.5f,
                            cameraY = cam.cameraY - pan.y * 1.5f
                        )
                    } else {
                        cam.copy(
                            yaw = cam.yaw + pan.x * 0.005f,
                            pitch = cam.pitch - pan.y * 0.005f
                        )
                    }
                    currentOnCameraChange(cam)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        if (currentIsIsolateMode) {
                            val layoutWidth = size.width.toFloat()
                            val layoutHeight = size.height.toFloat()
                            val center = Offset(layoutWidth / 2f, layoutHeight / 2f)
                            val projectedNodes = currentVisibleNodes.map { node ->
                                node to projectPoint(node.x, node.y, node.z, center, currentCamera.yaw, currentCamera.pitch, currentCamera.roll, currentCamera.cameraX, currentCamera.cameraY, currentCamera.cameraZ)
                            }.sortedByDescending { it.second.z }

                            val clicked = projectedNodes.findLast { (_, p) ->
                                val dx = p.x - tapOffset.x
                                val dy = p.y - tapOffset.y
                                val hitScale = p.scale.coerceAtMost(3f)
                                (dx * dx + dy * dy) < (625f * hitScale) // Reduced to 25px radius (was 50px) to prevent massive glow overlapping
                            }?.first
                            if (clicked != null) {
                                currentOnNodeDoubleTap(clicked)
                            }
                        }
                    },
                    onTap = { tapOffset ->
                        val layoutWidth = size.width.toFloat()
                        val layoutHeight = size.height.toFloat()
                        val center = Offset(layoutWidth / 2f, layoutHeight / 2f)
                        val projectedNodes = currentVisibleNodes.map { node ->
                            node to projectPoint(node.x, node.y, node.z, center, currentCamera.yaw, currentCamera.pitch, currentCamera.roll, currentCamera.cameraX, currentCamera.cameraY, currentCamera.cameraZ)
                        }.sortedByDescending { it.second.z }

                        val clicked = projectedNodes.findLast { (_, p) ->
                            val dx = p.x - tapOffset.x
                            val dy = p.y - tapOffset.y
                            val hitScale = p.scale.coerceAtMost(3f)
                            (dx * dx + dy * dy) < (625f * hitScale) // Reduced to 25px radius
                        }?.first
                        currentOnNodeTap(clicked)
                    }
                )
            }
    ) {
        val layoutWidth = size.width
        val layoutHeight = size.height
        onLayoutSize(layoutWidth, layoutHeight)
        val canvasCenter = Offset(layoutWidth / 2f, layoutHeight / 2f)

        // Pre-calculate projections for all visible nodes
        val projectedNodes = visibleNodes.map { node ->
            node to projectPoint(node.x, node.y, node.z, canvasCenter, camera.yaw, camera.pitch, camera.roll, camera.cameraX, camera.cameraY, camera.cameraZ)
        }.sortedByDescending { it.second.z } // Distant nodes first

        val projectedMap = projectedNodes.associateBy { it.first.entryId }

        // Draw Background Stars (Galaxy Mode only)
        if (layoutMode == LayoutMode.GALAXY) {
            backgroundStars.forEach { star ->
                val p = projectPoint(star.x + canvasCenter.x, star.y + canvasCenter.y, star.z, canvasCenter, camera.yaw, camera.pitch, camera.roll, camera.cameraX, camera.cameraY, camera.cameraZ)
                if (p.z + camera.cameraZ > 0) {
                    val visualScale = if (p.scale < 1f) p.scale * p.scale else p.scale
                    if (visualScale > 0.05f) {
                        val alphaFactor = visualScale.coerceIn(0.1f, 1f)
                        drawCircle(color = star.color.copy(alpha = alphaFactor * 0.3f), radius = star.size * visualScale * 2.5f, center = Offset(p.x, p.y))
                        drawCircle(color = star.color.copy(alpha = alphaFactor * 0.9f), radius = star.size * visualScale, center = Offset(p.x, p.y))
                    }
                }
            }
        }

        // Draw Cluster Nebulae
        if (showDecorations && (layoutMode == LayoutMode.CLUSTERS || layoutMode == LayoutMode.GALAXY) && projectedNodes.isNotEmpty()) {
            val clusters = projectedNodes.groupBy { it.first.clusterName }
            for ((clusterName, nodesInCluster) in clusters) {
                if (clusterName == "Unknown" || clusterName.endsWith(" Thought") || nodesInCluster.size < 3) continue
                
                val avgX = nodesInCluster.map { it.second.x }.average().toFloat()
                val avgY = nodesInCluster.map { it.second.y }.average().toFloat()
                
                val maxDist = nodesInCluster.maxOf { 
                    val dx = it.second.x - avgX
                    val dy = it.second.y - avgY
                    kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                }
                
                if (maxDist > 20f) {
                    val clusterColor = getCommunityColor(clusterName)
                    val drawRadius = maxDist * 1.5f
                    
                    drawCircle(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(clusterColor.copy(alpha = 0.08f), Color.Transparent),
                            center = Offset(avgX, avgY),
                            radius = drawRadius
                        ),
                        radius = drawRadius,
                        center = Offset(avgX, avgY)
                    )
                }
            }
        }

        // Draw Time Warp Semantic Trails
        if (showDecorations && layoutMode == LayoutMode.TIME_WARP && projectedNodes.size >= 2) {
            val chronologicalNodes = projectedNodes.sortedBy { it.first.date }
            
            for (i in 0 until chronologicalNodes.size - 1) {
                val n1 = chronologicalNodes[i]
                val n2 = chronologicalNodes[i+1]
                val p1 = n1.second
                val p2 = n2.second
                
                if (p1.z + camera.cameraZ > 0 && p2.z + camera.cameraZ > 0) {
                    val c1 = getMixedCommunityColor(n1.first.clusterName, n1.first.themeDistances)
                    val c2 = getMixedCommunityColor(n2.first.clusterName, n2.first.themeDistances)
                    
                    val avgScale = (p1.scale + p2.scale) / 2f
                    val drawScale = avgScale.coerceAtMost(3f)
                    val alpha = (0.25f * drawScale).coerceIn(0.1f, 0.6f)
                    
                    drawLine(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(c1.copy(alpha = alpha), c2.copy(alpha = alpha)),
                            start = Offset(p1.x, p1.y),
                            end = Offset(p2.x, p2.y)
                        ),
                        start = Offset(p1.x, p1.y),
                        end = Offset(p2.x, p2.y),
                        strokeWidth = 1.5f * drawScale,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // Draw Edges
        if (layoutMode != LayoutMode.TIME_WARP) {
            visibleEdges.forEach { edge ->
                    val sourceProj = projectedMap[edge.sourceId] ?: return@forEach
                    val targetProj = projectedMap[edge.targetId] ?: return@forEach
                    val sourceNode = sourceProj.first
                    val targetNode = targetProj.first
                    val p1 = sourceProj.second
                    val p2 = targetProj.second

                if (p1.z + camera.cameraZ > 0 && p2.z + camera.cameraZ > 0) {
                    val normalizedWeight = ((edge.weight - 0.55f) / 0.45f).coerceIn(0f, 1f)
                    val avgScale = (p1.scale + p2.scale) / 2f
                    val drawScale = avgScale.coerceAtMost(3f)
                    // Shrink even more when zooming way out
                    val visualScale = if (drawScale < 1f) drawScale * drawScale else drawScale
                    val edgeAlpha = (0.04f + normalizedWeight * 0.25f) * drawScale.coerceIn(0.05f, 1f)
                    val edgeWidth = (0.5f + normalizedWeight * 2f) * visualScale

                    val edgeColor = if (colorMode == ColorMode.MOOD) {
                        val avgMood = (sourceNode.moodScore + targetNode.moodScore) / 2.0
                        when {
                            avgMood >= 0.5 -> Color(0xFF64FFDA)
                            avgMood >= -0.2 -> Color(0xFF82B1FF)
                            avgMood >= -0.6 -> Color(0xFFFFAB40)
                            else -> Color(0xFFFF5252)
                        }
                    } else {
                        if (sourceNode.clusterName == targetNode.clusterName) getCommunityColor(sourceNode.clusterName) else Color.Gray.copy(alpha = 0.3f)
                    }

                    drawLine(
                        color = edgeColor.copy(alpha = edgeAlpha),
                        start = Offset(p1.x, p1.y),
                        end = Offset(p2.x, p2.y),
                        strokeWidth = edgeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // Draw Nodes

        projectedNodes.forEach { (node, p) ->
            if (p.z + camera.cameraZ > 0) {
                val isSelected = node == selectedNode
                val nodeColor = if (colorMode == ColorMode.MOOD) getMoodNodeColor(node.moodScore) else getCommunityColor(node.clusterName)
                val glowColor = if (colorMode == ColorMode.MOOD) nodeColor else getMixedCommunityColor(node.clusterName, node.themeDistances)

                val drawScale = p.scale.coerceAtMost(3f)
                // Shrink nodes faster when zoomed out so they look like tiny stars
                val visualScale = if (drawScale < 1f) drawScale * drawScale else drawScale

                val alphaFactor = p.scale.coerceIn(0.1f, 1f) * (1f - node.entropy * 0.3f)

                val importanceSq = node.importance * node.importance
                val importanceScale = 1f + (importanceSq * 1.0f) // Supernovas get up to 2x larger
                val entropyScale = 1f + (node.entropy * 1.5f) // Mixed thoughts get larger, diffuse bodies

                // Base radius doesn't double-count connection count anymore, importance handles it
                val baseRadius = 3.5f * visualScale * importanceScale * entropyScale
                val glowRadius = baseRadius * (1.8f + node.importance * 1.2f + node.entropy * 1.5f) // Massive glow for high entropy

                // Outer glow
                drawCircle(
                    color = glowColor.copy(alpha = (if (isSelected) 0.5f else glowPulse * 0.3f) * alphaFactor),
                    radius = if (isSelected) glowRadius * 1.5f else glowRadius,
                    center = Offset(p.x, p.y)
                )

                // Mid-glow
                drawCircle(
                    color = glowColor.copy(alpha = (if (isSelected) 0.4f else 0.15f) * alphaFactor),
                    radius = if (isSelected) baseRadius * 1.8f else baseRadius * 1.4f,
                    center = Offset(p.x, p.y)
                )

                // Core
                val coreAlpha = if (node.entropy > 0.6f) 0.6f else 1.0f // Highly mixed thoughts lack a dense solid core
                drawCircle(
                    color = (if (isSelected) Color.White else nodeColor).copy(alpha = alphaFactor * coreAlpha),
                    radius = if (isSelected) baseRadius * 1.2f else baseRadius,
                    center = Offset(p.x, p.y)
                )

                // Bright center - only visible for purer thoughts!
                if (node.entropy < 0.4f || isSelected) {
                    val brightScale = 1f - node.entropy
                    drawCircle(
                        color = Color.White.copy(alpha = (if (isSelected) 1f else 0.8f) * alphaFactor * brightScale),
                        radius = (if (isSelected) 3f else 1.5f) * visualScale * brightScale,
                        center = Offset(p.x, p.y)
                    )
                }

                // Selection ring
                if (isSelected) {
                    drawCircle(
                        color = nodeColor,
                        radius = 2.5f * drawScale,
                        center = Offset(p.x, p.y),
                        style = Stroke(width = 1.0f * drawScale)
                    )
                }

                // Text label in focus mode OR high importance node
                if (isIsolateMode || (node.importance > 0.85f && node.theme != "Unknown" && drawScale > 0.3f)) {
                    val nodeMoodLabel = getMoodLabel(node.moodScore)
                    val dateString = SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(node.date))

                    val labelText = buildAnnotatedString {
                        if (node.theme != "Unknown") {
                            withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = if (isIsolateMode) 0.9f else 0.75f))) {
                                append(node.theme)
                            }
                            append("\n")
                        }
                        withStyle(SpanStyle(fontWeight = FontWeight.Light, color = Color.White.copy(alpha = if (isIsolateMode) 0.7f else 0.5f), fontSize = (7f * visualScale).coerceIn(5f, 9f).sp)) {
                            append("$dateString  •  $nodeMoodLabel")
                        }
                    }

                    val textLayoutResult = textMeasurer.measure(
                        text = labelText,
                        style = TextStyle(
                            fontSize = (8f * visualScale).coerceIn(6f, 11f).sp,
                            lineHeight = (11f * visualScale).coerceIn(8f, 14f).sp
                        )
                    )

                    val padding = 6f * visualScale.coerceIn(0.5f, 1.2f)
                    val cardWidth = textLayoutResult.size.width + padding * 2
                    val cardHeight = textLayoutResult.size.height + padding * 2

                    val cardOffset = Offset(
                        p.x - cardWidth / 2f,
                        p.y + baseRadius * 2f + 4f
                    )

                    // Draw background card
                    drawRoundRect(
                        color = Color(0xFF1E1E2E).copy(alpha = if (isIsolateMode) 0.85f else 0.45f),
                        topLeft = cardOffset,
                        size = Size(cardWidth, cardHeight),
                        cornerRadius = CornerRadius(12f, 12f)
                    )

                    val textOffset = Offset(
                        cardOffset.x + padding,
                        cardOffset.y + padding
                    )
                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = textOffset
                    )
                }
            }
        }

        // Time Warp HUD Labels
        if (layoutMode == LayoutMode.TIME_WARP && allNodes.isNotEmpty()) {
            val minDate = allNodes.minOf { it.date }
            val maxDate = allNodes.maxOf { it.date }
            val dateSpan = (maxDate - minDate).coerceAtLeast(1L)
            val spanDays = dateSpan / (1000L * 60 * 60 * 24)

            val calendar = java.util.Calendar.getInstance()
            calendar.timeInMillis = minDate

            val (calendarField, formatStr) = when {
                spanDays <= 365 * 3 -> java.util.Calendar.MONTH to "MMM yyyy"
                else -> java.util.Calendar.YEAR to "yyyy"
            }
            // Quarterly if > 6 months, else Monthly
            val step = if (spanDays > 180 && spanDays <= 365 * 3) 3 else 1

            if (calendarField == java.util.Calendar.MONTH) {
                calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
            } else {
                calendar.set(java.util.Calendar.DAY_OF_YEAR, 1)
            }

            val formatter = SimpleDateFormat(formatStr, Locale.getDefault())
            val timelineWidth = layoutWidth * 2.0f
            val timelineY = layoutHeight / 2f - 600f // Floating high above the tunnel
            val timelineZ = 0f

            while (calendar.timeInMillis <= maxDate) {
                val tickTime = calendar.timeInMillis
                if (tickTime >= minDate) {
                    val rawProgress = (tickTime - minDate).toFloat() / dateSpan.toFloat()
                    val dateProgress = (rawProgress + graphEngine.state.warpTimeOffset) % 1.0f
                    val targetX = (layoutWidth / 2f - timelineWidth / 2f) + (dateProgress * timelineWidth)

                    val p = projectPoint(targetX, timelineY, timelineZ, canvasCenter, camera.yaw, camera.pitch, camera.roll, camera.cameraX, camera.cameraY, camera.cameraZ)

                    if (p.z + camera.cameraZ > 0 && p.scale > 0.1f) {
                        val label = formatter.format(Date(tickTime))
                        val textLayoutResult = textMeasurer.measure(
                            text = label,
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = (12f * p.scale.coerceIn(0.5f, 2.5f)).sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        drawText(
                            textLayoutResult = textLayoutResult,
                            topLeft = Offset(p.x - textLayoutResult.size.width / 2f, p.y - textLayoutResult.size.height / 2f)
                        )
                        // Draw indicator line pointing down to the timeline
                        drawLine(
                            color = Color.White.copy(alpha = 0.2f),
                            start = Offset(p.x, p.y + textLayoutResult.size.height / 2f + 8f),
                            end = Offset(p.x, p.y + textLayoutResult.size.height / 2f + 50f * p.scale),
                            strokeWidth = 2f * p.scale
                        )
                    }
                }
                calendar.add(calendarField, step)
            }
        }
    }
}
