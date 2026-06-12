package com.laniakea.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.gestures.detectTransformGestures
import com.laniakea.data.ObjectBoxSentenceVector
import com.laniakea.engine.GraphEdge
import com.laniakea.engine.GraphEngine
import com.laniakea.engine.GraphNode
import com.laniakea.manager.SecurityManager
import com.laniakea.viewmodel.LaniakeaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.platform.LocalLocale
import com.laniakea.engine.LayoutMode
import com.laniakea.ui.components.map.getCommunityColor
import com.laniakea.ui.components.map.getMoodNodeColor
import com.laniakea.ui.components.map.projectPoint
import com.laniakea.ui.components.map.MapControls
import com.laniakea.ui.components.map.MapLegend
import com.laniakea.ui.components.map.MapNodeDetailPanel
import com.laniakea.ui.components.map.MapStatsBadge
import com.laniakea.ui.components.map.MapConnectionsDialog

enum class ColorMode { MOOD, COMMUNITY }

data class GalaxyStar(val x: Float, val y: Float, val z: Float, val size: Float, val color: Color)

@Composable
fun MapScreen(padding: PaddingValues, vm: LaniakeaViewModel) {
    val allEntries by vm.allEntries.collectAsState()
    val isEngineActive = vm.isEngineActive
    val isEngineLoading = vm.isEngineLoading

    var colorMode by remember { mutableStateOf(ColorMode.MOOD) }
    var layoutMode by remember { mutableStateOf(LayoutMode.CLUSTERS) }

    val backgroundStars = remember {
        val list = mutableListOf<GalaxyStar>()
        val numStars = 4000
        val numArms = 2
        val random = java.util.Random()

        for (i in 0 until numStars) {
            val isCore = i < numStars * 0.35f
            if (isCore) {
                val r = (kotlin.math.abs(random.nextGaussian()) * 150f).toFloat()
                val theta = random.nextDouble() * 2 * Math.PI
                val phi = kotlin.math.acos(2 * random.nextDouble() - 1)
                
                val x = r * kotlin.math.sin(phi) * kotlin.math.cos(theta)
                val y = r * kotlin.math.sin(phi) * kotlin.math.sin(theta)
                val z = r * kotlin.math.cos(phi) * 0.8
                
                val color = Color(0xFFFFFFFF)
                val size = random.nextFloat() * 1.5f + 0.5f
                list.add(GalaxyStar(x.toFloat(), y.toFloat(), z.toFloat(), size, color))
            } else {
                val armIndex = i % numArms
                val dist = (random.nextFloat() * 1000f) + 80f
                val baseAngle = dist * 0.004f + (armIndex * (2 * Math.PI / numArms))
                val spread = (random.nextGaussian() * (dist * 0.18f)).toFloat()
                val angle = baseAngle + (spread * 0.002f)
                val x = (kotlin.math.cos(angle) * dist).toFloat() + spread * kotlin.math.cos(angle + Math.PI/2).toFloat()
                val y = (kotlin.math.sin(angle) * dist).toFloat() + spread * kotlin.math.sin(angle + Math.PI/2).toFloat()
                val zThickness = (1000f - dist).coerceAtLeast(0f) * 0.03f
                val z = (random.nextGaussian() * zThickness).toFloat()
                
                val color = Color(0xFFB0B0B0)
                val size = random.nextFloat() * 2.0f + 0.5f
                list.add(GalaxyStar(x, y, z, size, color))
            }
        }
        list
    }

    var allNodes by remember { mutableStateOf<List<GraphNode>>(emptyList()) }
    var allEdges by remember { mutableStateOf<List<GraphEdge>>(emptyList()) }
    
    // Replay controls visibility only — no physics during replay
    var isReplaying by remember { mutableStateOf(false) }
    var replayProgress by remember { mutableIntStateOf(0) }
    
    // Settle button re-runs live physics briefly
    var isSettling by remember { mutableStateOf(false) }
    
    val graphEngine = remember { GraphEngine() }
    var layoutWidth by remember { mutableFloatStateOf(0f) }
    var layoutHeight by remember { mutableFloatStateOf(0f) }
    
    var yaw by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(-0.6f) }
    var roll by remember { mutableFloatStateOf(0f) }
    var cameraX by remember { mutableFloatStateOf(0f) }
    var cameraY by remember { mutableFloatStateOf(0f) }
    var cameraZ by remember { mutableFloatStateOf(800f) }
    var activePointers by remember { mutableIntStateOf(0) }
    
    var selectedNode by remember { mutableStateOf<GraphNode?>(null) }
    var isIsolateMode by remember { mutableStateOf(false) }
    var showConnectionsFor by remember { mutableStateOf<GraphNode?>(null) }
    var showDetailPanelInFocusMode by remember { mutableStateOf(false) }
    
    var vectorsFetched by remember { mutableStateOf(false) }
    var vectors by remember { mutableStateOf<List<ObjectBoxSentenceVector>>(emptyList()) }
    
    var hasBuiltGraph by remember { mutableStateOf(false) }
    var isBuildingGraph by remember { mutableStateOf(false) }
    
    var entryLimit by remember { mutableIntStateOf(300) }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val securityManager = remember { SecurityManager(context) }
    val textMeasurer = rememberTextMeasurer()
    
    // Pulsing glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )
    
    LaunchedEffect(isEngineActive) {
        if (isEngineActive) {
            vectors = com.laniakea.data.ObjectBoxManager.vectorBox.all
            vectorsFetched = true
        }
    }
    
    // Build and pre-settle the graph — layout is already done when it appears
    LaunchedEffect(vectorsFetched, allEntries.size, layoutWidth, layoutHeight, entryLimit) {
        if (vectorsFetched && allEntries.isNotEmpty() && layoutWidth > 0 && layoutHeight > 0) {
            val entriesToProcess = allEntries.sortedByDescending { it.dateTime }.take(entryLimit)
            if (hasBuiltGraph && allNodes.size == entriesToProcess.size) return@LaunchedEffect
            
            isBuildingGraph = true
            
            val entriesIds = entriesToProcess.map { it.id }.toSet()
            val vectorsToProcess = vectors.filter { it.entryId in entriesIds }
            
            val (initialNodes, initialEdges) = withContext(Dispatchers.Default) {
                graphEngine.buildGraph(
                    entries = entriesToProcess,
                    vectors = vectorsToProcess,
                    similarityThreshold = 0.55f,
                    width = layoutWidth,
                    height = layoutHeight,
                    securityManager = securityManager
                )
            }
            allNodes = initialNodes.sortedBy { it.date }
            allEdges = initialEdges
            replayProgress = allNodes.size
            hasBuiltGraph = true
            isBuildingGraph = false
        }
    }
    
    // Live physics for the "Settle" button only
    LaunchedEffect(isSettling) {
        if (isSettling) {
            graphEngine.layoutMode = layoutMode
            graphEngine.startLiveSimulation()
            var steps = 0
            while (isSettling && steps < 200) {
                delay(16L.milliseconds)
                graphEngine.applyLiveStep(allNodes, allEdges, layoutWidth, layoutHeight)
                allNodes = allNodes.toList() // trigger recomposition
                steps++
            }
            isSettling = false
        }
    }
    
    // Automatically settle when switching layout modes
    LaunchedEffect(layoutMode) {
        if (hasBuiltGraph && !isReplaying) {
            isSettling = true
        }
    }
    
    // Replay: reveal nodes, then transition into a continuous animation
    LaunchedEffect(isReplaying) {
        if (isReplaying) {
            replayProgress = 0
            // Small delay before starting
            delay(300L.milliseconds)
            while (replayProgress < allNodes.size && isReplaying) {
                replayProgress++
                // Faster for large graphs, slower for small
                val delayMs = if (allNodes.size > 30) 60L else 200L
                delay(delayMs.milliseconds)
            }
            
            // Post-replay continuous animation
            var animTime = 0f
            while (isReplaying) {
                delay(16L.milliseconds) // ~60fps
                animTime += 0.016f
                
                if (layoutMode == LayoutMode.CLUSTERS) {
                    // Spin the clusters slowly like a disk
                    roll -= 0.003f
                } else if (layoutMode == LayoutMode.TIME_WARP) {
                    // Warpy time tunnel animation: fly forward with a gentle warp wobble
                    cameraZ -= 3f
                    yaw = kotlin.math.sin(animTime * 0.5f) * 0.05f
                    pitch = -0.6f + kotlin.math.cos(animTime * 0.3f) * 0.05f
                }
            }
        }
    }
    
    // Continuous Galaxy Rotation
    LaunchedEffect(layoutMode) {
        if (layoutMode == LayoutMode.GALAXY) {
            var t = 0f
            while (true) {
                delay(16L.milliseconds)
                t += 0.016f
                if (selectedNode == null && !showDetailPanelInFocusMode) {
                    roll -= 0.002f // Spin around the galaxy's center
                    yaw = kotlin.math.cos(t * 0.1f) * 0.1f
                }
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(Brush.verticalGradient(listOf(Color(0xFF0A0E21), Color(0xFF1A1A2E), Color(0xFF16213E))))
    ) {
        if (!isEngineActive || isBuildingGraph) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!isEngineActive && !isEngineLoading) {
                        Icon(Icons.Default.Stop, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Core Offline",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { vm.initializeEngine() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64FFDA), contentColor = Color.Black)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Initialize Engine")
                        }
                    } else {
                        CircularProgressIndicator(color = Color(0xFF64FFDA), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            if (!isEngineActive) "Warming up the Vector Engine..." else "Building your constellation...",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                layoutWidth = size.width
                layoutHeight = size.height
            }
            return@Box
        }

        val baseVisibleNodes = allNodes.take(replayProgress)
        val visibleNodes = if (isIsolateMode && selectedNode != null) {
            val connectedIds = allEdges.filter { it.source.entryId == selectedNode!!.entryId || it.target.entryId == selectedNode!!.entryId }
                .flatMap { listOf(it.source.entryId, it.target.entryId) }
                .toSet()
            baseVisibleNodes.filter { it.entryId == selectedNode!!.entryId || it.entryId in connectedIds }
        } else {
            baseVisibleNodes
        }

        val visibleIds = visibleNodes.map { it.entryId }.toSet()
        val visibleEdges = if (isIsolateMode && selectedNode != null) {
            allEdges.filter { (it.source.entryId == selectedNode!!.entryId || it.target.entryId == selectedNode!!.entryId) && 
                              it.source.entryId in visibleIds && it.target.entryId in visibleIds }
        } else {
            allEdges.filter { it.source.entryId in visibleIds && it.target.entryId in visibleIds }
        }
        
        val currentVisibleNodes by rememberUpdatedState(visibleNodes)

        Canvas(
            modifier = Modifier
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
                        if (zoom != 1f) {
                            cameraZ = (cameraZ / zoom).coerceIn(50f, 4000f)
                        }
                        if (activePointers >= 3) {
                            cameraX -= pan.x * 1.5f
                            cameraY -= pan.y * 1.5f
                        } else {
                            yaw += pan.x * 0.005f
                            pitch -= pan.y * 0.005f
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            if (isIsolateMode) {
                                val center = Offset(layoutWidth / 2f, layoutHeight / 2f)
                                val projectedNodes = currentVisibleNodes.map { node ->
                                    node to projectPoint(node.x, node.y, node.z, center, yaw, pitch, roll, cameraX, cameraY, cameraZ)
                                }.sortedByDescending { it.second.z }
                                
                                val clicked = projectedNodes.findLast { (_, p) ->
                                    val dx = p.x - tapOffset.x
                                    val dy = p.y - tapOffset.y
                                    val hitScale = p.scale.coerceAtMost(3f)
                                    (dx * dx + dy * dy) < (2500f * hitScale)
                                }?.first
                                if (clicked != null) {
                                    selectedNode = clicked
                                    showDetailPanelInFocusMode = true
                                }
                            }
                        },
                        onTap = { tapOffset ->
                            val center = Offset(layoutWidth / 2f, layoutHeight / 2f)
                            val projectedNodes = currentVisibleNodes.map { node ->
                                node to projectPoint(node.x, node.y, node.z, center, yaw, pitch, roll, cameraX, cameraY, cameraZ)
                            }.sortedByDescending { it.second.z }
                            
                            val clicked = projectedNodes.findLast { (_, p) ->
                                val dx = p.x - tapOffset.x
                                val dy = p.y - tapOffset.y
                                val hitScale = p.scale.coerceAtMost(3f)
                                (dx * dx + dy * dy) < (2500f * hitScale)
                            }?.first
                            selectedNode = clicked
                        }
                    )
                }
        ) {
            layoutWidth = size.width
            layoutHeight = size.height
            val canvasCenter = Offset(size.width / 2f, size.height / 2f)
            
            // Draw Background Stars (Galaxy Mode only)
            if (layoutMode == LayoutMode.GALAXY) {
                backgroundStars.forEach { star ->
                    val p = projectPoint(star.x + canvasCenter.x, star.y + canvasCenter.y, star.z, canvasCenter, yaw, pitch, roll, cameraX, cameraY, cameraZ)
                    if (p.z + cameraZ > 0) {
                        val visualScale = if (p.scale < 1f) p.scale * p.scale else p.scale
                        if (visualScale > 0.05f) {
                            val alphaFactor = visualScale.coerceIn(0.1f, 1f)
                            drawCircle(color = star.color.copy(alpha = alphaFactor * 0.3f), radius = star.size * visualScale * 2.5f, center = Offset(p.x, p.y))
                            drawCircle(color = star.color.copy(alpha = alphaFactor * 0.9f), radius = star.size * visualScale, center = Offset(p.x, p.y))
                        }
                    }
                }
            }
            
            // Draw Edges
            visibleEdges.forEach { edge ->
                val p1 = projectPoint(edge.source.x, edge.source.y, edge.source.z, canvasCenter, yaw, pitch, roll, cameraX, cameraY, cameraZ)
                val p2 = projectPoint(edge.target.x, edge.target.y, edge.target.z, canvasCenter, yaw, pitch, roll, cameraX, cameraY, cameraZ)
                
                if (p1.z + cameraZ > 0 && p2.z + cameraZ > 0) {
                    val normalizedWeight = ((edge.weight - 0.55f) / 0.45f).coerceIn(0f, 1f)
                    val avgScale = (p1.scale + p2.scale) / 2f
                    val drawScale = avgScale.coerceAtMost(3f)
                    // Shrink even more when zooming way out
                    val visualScale = if (drawScale < 1f) drawScale * drawScale else drawScale
                    val edgeAlpha = (0.04f + normalizedWeight * 0.25f) * drawScale.coerceIn(0.05f, 1f)
                    val edgeWidth = (0.5f + normalizedWeight * 2f) * visualScale
                    
                    val edgeColor = if (colorMode == ColorMode.MOOD) {
                        val avgMood = (edge.source.moodScore + edge.target.moodScore) / 2.0
                        when {
                            avgMood >= 0.5 -> Color(0xFF64FFDA)
                            avgMood >= -0.2 -> Color(0xFF82B1FF)
                            avgMood >= -0.6 -> Color(0xFFFFAB40)
                            else -> Color(0xFFFF5252)
                        }
                    } else {
                        if (edge.source.clusterName == edge.target.clusterName) getCommunityColor(edge.source.clusterName) else Color.Gray.copy(alpha = 0.3f)
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
            
            // Draw Nodes
            val projectedNodes = visibleNodes.map { node ->
                node to projectPoint(node.x, node.y, node.z, canvasCenter, yaw, pitch, roll, cameraX, cameraY, cameraZ)
            }.sortedByDescending { it.second.z } // Distant nodes first
            
            projectedNodes.forEach { (node, p) ->
                if (p.z + cameraZ > 0) {
                    val isSelected = node == selectedNode
                    val nodeColor = if (colorMode == ColorMode.MOOD) getMoodNodeColor(node.moodScore) else getCommunityColor(node.clusterName)

                    val drawScale = p.scale.coerceAtMost(3f)
                    // Shrink nodes faster when zoomed out so they look like tiny stars
                    val visualScale = if (drawScale < 1f) drawScale * drawScale else drawScale
                    
                    val alphaFactor = p.scale.coerceIn(0.1f, 1f)
                    
                    val importanceSq = node.importance * node.importance
                    val importanceScale = 1f + (importanceSq * 1.0f) // Supernovas get up to 2x larger
                    
                    // Base radius doesn't double-count connection count anymore, importance handles it
                    val baseRadius = 3.5f * visualScale * importanceScale
                    val glowRadius = baseRadius * (1.8f + node.importance * 1.2f) // Glow scales linearly with importance
                    
                    // Outer glow
                    drawCircle(
                        color = nodeColor.copy(alpha = (if (isSelected) 0.5f else glowPulse * 0.3f) * alphaFactor),
                        radius = if (isSelected) glowRadius * 1.5f else glowRadius,
                        center = Offset(p.x, p.y)
                    )
                    
                    // Mid-glow
                    drawCircle(
                        color = nodeColor.copy(alpha = (if (isSelected) 0.4f else 0.15f) * alphaFactor),
                        radius = if (isSelected) baseRadius * 1.8f else baseRadius * 1.4f,
                        center = Offset(p.x, p.y)
                    )
                    
                    // Core
                    drawCircle(
                        color = (if (isSelected) Color.White else nodeColor).copy(alpha = alphaFactor),
                        radius = if (isSelected) baseRadius * 1.2f else baseRadius,
                        center = Offset(p.x, p.y)
                    )
                    
                    // Bright center
                    drawCircle(
                        color = Color.White.copy(alpha = (if (isSelected) 1f else 0.8f) * alphaFactor),
                        radius = (if (isSelected) 3f else 1.5f) * visualScale,
                        center = Offset(p.x, p.y)
                    )
                    
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
                        val nodeMoodLabel = when {
                            node.moodScore > 1.5 -> "🤩 Awesome"
                            node.moodScore > 0.5 -> "🙂 Good"
                            node.moodScore > -0.5 -> "😐 Fine"
                            node.moodScore > -1.5 -> "🙁 Bad"
                            else -> "😫 Terrible"
                        }
                        
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
                        val dateProgress = (tickTime - minDate).toFloat() / dateSpan.toFloat()
                        val targetX = (layoutWidth / 2f - timelineWidth / 2f) + (dateProgress * timelineWidth)
                        
                        val p = projectPoint(targetX, timelineY, timelineZ, canvasCenter, yaw, pitch, roll, cameraX, cameraY, cameraZ)
                        
                        if (p.z + cameraZ > 0 && p.scale > 0.1f) {
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

        // Stats badge
        MapStatsBadge(
            nodeCount = visibleNodes.size,
            edgeCount = visibleEdges.size,
            modifier = Modifier.align(Alignment.TopEnd)
        )
        
        // Node detail panel
        val showPanel = (!isIsolateMode && selectedNode != null) || (isIsolateMode && showDetailPanelInFocusMode && selectedNode != null)
        if (showPanel) {
            val nodeToShow = selectedNode!!
            val decryptedContent = remember(nodeToShow) {
                try {
                    securityManager.decrypt(nodeToShow.content)
                } catch (_: Exception) {
                    nodeToShow.content
                }
            }
            val nodeColor = getMoodNodeColor(nodeToShow.moodScore)
            val moodLabel = when {
                nodeToShow.moodScore > 1.5 -> "🤩 Awesome"
                nodeToShow.moodScore > 0.5 -> "🙂 Good"
                nodeToShow.moodScore > -0.5 -> "😐 Fine"
                nodeToShow.moodScore > -1.5 -> "🙁 Bad"
                else -> "😫 Terrible"
            }
            
            MapNodeDetailPanel(
                nodeToShow = nodeToShow,
                decryptedContent = decryptedContent,
                nodeColor = nodeColor,
                moodLabel = moodLabel,
                nodeConnections = visibleEdges.count { it.source.entryId == nodeToShow.entryId || it.target.entryId == nodeToShow.entryId },
                isIsolateMode = isIsolateMode,
                onClose = {
                    if (isIsolateMode) showDetailPanelInFocusMode = false else selectedNode = null
                },
                onViewAll = { showConnectionsFor = nodeToShow },
                onFocus = {
                    isIsolateMode = true
                    showDetailPanelInFocusMode = false
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        
        // Controls
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Constellation Map",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Light
            )
            Text(
                "A semantic web of your thoughts",
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodyMedium
            )
            
            // Controls moved to BottomEnd
            
            Spacer(modifier = Modifier.height(12.dp))
            MapControls(
                layoutMode = layoutMode,
                onLayoutModeChange = { layoutMode = it },
                colorMode = colorMode,
                onColorModeChange = { colorMode = it }
            )
            
            // Legend
            if (colorMode == ColorMode.MOOD) {
                Spacer(modifier = Modifier.height(12.dp))
                MapLegend()
            }
        }
        
        // Floating Controls (Bottom Right)
        if (isIsolateMode) {
            FloatingActionButton(
                onClick = { 
                    isIsolateMode = false 
                    showDetailPanelInFocusMode = false
                },
                containerColor = Color.White.copy(alpha = 0.1f),
                contentColor = Color(0xFF64FFDA),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Exit Focus Mode")
            }
        } else {
            val fabBottomOffset = if (selectedNode != null) 220.dp else 16.dp
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = fabBottomOffset)
            ) {
                if (isReplaying) {
                    FloatingActionButton(
                        onClick = { 
                            isReplaying = false
                            replayProgress = allNodes.size
                            yaw = 0f
                            pitch = -0.6f
                            roll = 0f
                            cameraX = 0f
                            cameraY = 0f
                            cameraZ = 800f
                        },
                        containerColor = Color(0xFF2D1B1B),
                        contentColor = Color(0xFFFF5252)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop Replay")
                    }
                } else {
                    FloatingActionButton(
                        onClick = { isReplaying = true },
                        containerColor = Color(0xFF1B2D2D),
                        contentColor = Color(0xFF64FFDA)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Replay Timeline")
                    }
                }
                
                if (!isSettling && !isReplaying) {
                    FloatingActionButton(
                        onClick = { 
                            yaw = 0f
                            pitch = -0.6f
                            roll = 0f
                            cameraX = 0f
                            cameraY = 0f
                            cameraZ = 800f
                        },
                        containerColor = Color.White.copy(alpha = 0.1f),
                        contentColor = Color.White.copy(alpha = 0.8f)
                    ) {
                        Icon(Icons.Default.Home, contentDescription = "Reset View")
                    }
                    FloatingActionButton(
                        onClick = { 
                            isSettling = true 
                            cameraX = 0f
                            cameraY = 0f
                        },
                        containerColor = Color.White.copy(alpha = 0.1f),
                        contentColor = Color.White.copy(alpha = 0.8f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Re-settle")
                    }
                    if (allEntries.size > entryLimit) {
                        FloatingActionButton(
                            onClick = { entryLimit += 300 },
                            containerColor = Color.White.copy(alpha = 0.1f),
                            contentColor = Color.White.copy(alpha = 0.8f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Load More")
                        }
                    }
                }
            }
        }
        
        if (showConnectionsFor != null) {
            MapConnectionsDialog(
                targetNode = showConnectionsFor!!,
                visibleEdges = visibleEdges,
                securityManager = securityManager,
                onDismiss = { showConnectionsFor = null }
            )
        }
        
    }
}