package com.laniakea.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

val communityColors = listOf(
    Color(0xFFE91E63), // Pink
    Color(0xFF9C27B0), // Purple
    Color(0xFF3F51B5), // Indigo
    Color(0xFF00BCD4), // Cyan
    Color(0xFF4CAF50), // Green
    Color(0xFFFFC107), // Amber
    Color(0xFFFF5722), // Deep Orange
    Color(0xFF795548), // Brown
    Color(0xFF607D8B), // Blue Grey
    Color(0xFF8BC34A)  // Light Green
)

enum class ColorMode { MOOD, COMMUNITY }

fun getCommunityColor(clusterId: Int): Color {
    if (clusterId < 0) return Color.Gray
    return communityColors[clusterId % communityColors.size]
}

@Composable
fun ConnectionsScreen(padding: PaddingValues, vm: LaniakeaViewModel) {
    val allEntries by vm.allEntries.collectAsState()
    val isEngineActive = vm.isEngineActive

    var colorMode by remember { mutableStateOf(ColorMode.MOOD) }
    var layoutMode by remember { mutableStateOf(LayoutMode.GALAXY) }

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
    var pitch by remember { mutableFloatStateOf(0f) }
    var cameraZ by remember { mutableFloatStateOf(800f) }
    
    var selectedNode by remember { mutableStateOf<GraphNode?>(null) }
    var isIsolateMode by remember { mutableStateOf(false) }
    var showConnectionsFor by remember { mutableStateOf<GraphNode?>(null) }
    
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
                graphEngine.buildInitialGraph(
                    entries = entriesToProcess,
                    vectors = vectorsToProcess,
                    similarityThreshold = 0.55f,
                    width = layoutWidth,
                    height = layoutHeight
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
    
    // Replay: just reveal nodes one by one at their settled positions
    LaunchedEffect(isReplaying) {
        if (isReplaying) {
            replayProgress = 0
            // Small delay before starting
            delay(300L.milliseconds)
            while (replayProgress < allNodes.size) {
                replayProgress++
                // Faster for large graphs, slower for small
                val delayMs = if (allNodes.size > 30) 60L else 200L
                delay(delayMs.milliseconds)
            }
            isReplaying = false
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
                    CircularProgressIndicator(color = Color(0xFF64FFDA), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        if (!isEngineActive) "Warming up the Vector Engine..." else "Building your constellation...",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
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
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (zoom != 1f) {
                            cameraZ = (cameraZ / zoom).coerceIn(50f, 4000f)
                        }
                        yaw += pan.x * 0.005f
                        pitch -= pan.y * 0.005f
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        val center = Offset(layoutWidth / 2f, layoutHeight / 2f)
                        val projectedNodes = currentVisibleNodes.map { node ->
                            node to projectPoint(node.x, node.y, node.z, center, yaw, pitch, cameraZ)
                        }.sortedByDescending { it.second.z }
                        
                        val clicked = projectedNodes.findLast { (_, p) ->
                            val dx = p.x - tapOffset.x
                            val dy = p.y - tapOffset.y
                            val hitScale = p.scale.coerceAtMost(3f)
                            (dx * dx + dy * dy) < (2500f * hitScale)
                        }?.first
                        selectedNode = clicked
                    }
                }
        ) {
            layoutWidth = size.width
            layoutHeight = size.height
            val canvasCenter = Offset(size.width / 2f, size.height / 2f)
            
            // Draw Edges
            visibleEdges.forEach { edge ->
                val p1 = projectPoint(edge.source.x, edge.source.y, edge.source.z, canvasCenter, yaw, pitch, cameraZ)
                val p2 = projectPoint(edge.target.x, edge.target.y, edge.target.z, canvasCenter, yaw, pitch, cameraZ)
                
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
                        if (edge.source.clusterId == edge.target.clusterId) getCommunityColor(edge.source.clusterId) else Color.Gray.copy(alpha = 0.3f)
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
                node to projectPoint(node.x, node.y, node.z, canvasCenter, yaw, pitch, cameraZ)
            }.sortedByDescending { it.second.z } // Distant nodes first
            
            projectedNodes.forEach { (node, p) ->
                if (p.z + cameraZ > 0) {
                    val isSelected = node == selectedNode
                    val nodeColor = if (colorMode == ColorMode.MOOD) getMoodNodeColor(node.moodScore) else getCommunityColor(node.clusterId)

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
        }

        // Stats badge
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.08f),
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    "${visibleNodes.size} thoughts",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${visibleEdges.size} connections",
                    color = Color(0xFF64FFDA).copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        
        // Node detail panel
        if (selectedNode != null && !isIsolateMode) {
            val decryptedContent = remember(selectedNode) {
                try {
                    securityManager.decrypt(selectedNode!!.content)
                } catch (_: Exception) {
                    selectedNode!!.content
                }
            }
            val nodeColor = getMoodNodeColor(selectedNode!!.moodScore)
            val moodLabel = when {
                selectedNode!!.moodScore > 1.5 -> "🤩 Awesome"
                selectedNode!!.moodScore > 0.5 -> "🙂 Good"
                selectedNode!!.moodScore > -0.5 -> "😐 Fine"
                selectedNode!!.moodScore > -1.5 -> "🙁 Bad"
                else -> "😫 Terrible"
            }
            
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E1E2E).copy(alpha = 0.95f),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Canvas(modifier = Modifier.size(12.dp)) {
                                drawCircle(color = nodeColor, radius = size.minDimension / 2f)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            if (selectedNode!!.theme != "Unknown") {
                                Text(
                                    text = selectedNode!!.theme,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                        IconButton(onClick = { selectedNode = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.6f))
                        }
                    }
                    
                    val nodeConnections = visibleEdges.count {
                        it.source.entryId == selectedNode!!.entryId || it.target.entryId == selectedNode!!.entryId
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(moodLabel, style = MaterialTheme.typography.bodySmall, color = nodeColor)
                        
                        Row {
                            if (nodeConnections > 0) {
                                TextButton(
                                    onClick = { showConnectionsFor = selectedNode },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text("View All", style = MaterialTheme.typography.labelSmall, color = Color(0xFF82B1FF))
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = { isIsolateMode = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("Focus", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64FFDA))
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "$nodeConnections similar thoughts connected",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64FFDA).copy(alpha = 0.7f)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.05f)
                    ) {
                        Text(
                            text = decryptedContent.take(200) + if (decryptedContent.length > 200) "..." else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Layout Mode Toggle (Galaxy | Time Warp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(4.dp)
                ) {
                    TextButton(
                        onClick = { layoutMode = LayoutMode.GALAXY },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (layoutMode == LayoutMode.GALAXY) Color.White.copy(alpha=0.2f) else Color.Transparent
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Galaxy", color = Color.White, fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = { layoutMode = LayoutMode.TIME_WARP },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (layoutMode == LayoutMode.TIME_WARP) Color.White.copy(alpha=0.2f) else Color.Transparent
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Time Warp", color = Color.White, fontSize = 12.sp)
                    }
                }
                
                // Color Mode Toggle (Mood | Themes)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(4.dp)
                ) {
                    TextButton(
                        onClick = { colorMode = ColorMode.MOOD },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (colorMode == ColorMode.MOOD) Color.White.copy(alpha=0.2f) else Color.Transparent
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Mood", color = Color.White, fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = { colorMode = ColorMode.COMMUNITY },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (colorMode == ColorMode.COMMUNITY) Color.White.copy(alpha=0.2f) else Color.Transparent
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Themes", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
            
            // Legend
            if (colorMode == ColorMode.MOOD) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    LegendDot(Color(0xFF64FFDA), "Positive")
                    LegendDot(Color(0xFF82B1FF), "Neutral")
                    LegendDot(Color(0xFFFFAB40), "Low")
                    LegendDot(Color(0xFFFF5252), "Negative")
                }
            }
        }
        
        // Floating Controls (Bottom Right)
        if (isIsolateMode) {
            FloatingActionButton(
                onClick = { isIsolateMode = false },
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
                            pitch = 0f
                            cameraZ = 800f
                        },
                        containerColor = Color.White.copy(alpha = 0.1f),
                        contentColor = Color.White.copy(alpha = 0.8f)
                    ) {
                        Icon(Icons.Default.Home, contentDescription = "Reset View")
                    }
                    FloatingActionButton(
                        onClick = { isSettling = true },
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
            val connectedEdges = visibleEdges.filter {
                it.source.entryId == showConnectionsFor!!.entryId || it.target.entryId == showConnectionsFor!!.entryId
            }.sortedByDescending { it.weight }
            
            AlertDialog(
                onDismissRequest = { showConnectionsFor = null },
                title = {
                    Text(
                        "Connected Thoughts",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                },
                text = {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(connectedEdges) { edge ->
                            val relatedNode = if (edge.source.entryId == showConnectionsFor!!.entryId) edge.target else edge.source
                            val decryptedNodeContent = try {
                                securityManager.decrypt(relatedNode.content)
                            } catch (_: Exception) {
                                relatedNode.content
                            }
                            
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            if (relatedNode.theme != "Unknown") {
                                                Text(
                                                    relatedNode.theme,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = getMoodNodeColor(relatedNode.moodScore),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            val listDateString = SimpleDateFormat("MMM dd, yyyy", LocalLocale.current.platformLocale).format(Date(relatedNode.date))
                                            Text(
                                                listDateString,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.6f)
                                            )
                                        }
                                        Text(
                                            "${(edge.weight * 100).toInt()}% match",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        decryptedNodeContent.take(150) + if (decryptedNodeContent.length > 150) "..." else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showConnectionsFor = null }) {
                        Text("Close", color = Color(0xFF64FFDA))
                    }
                },
                containerColor = Color(0xFF1E1E2E),
                titleContentColor = Color.White,
                textContentColor = Color.White
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(color = color)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
    }
}

private fun getMoodNodeColor(moodScore: Double): Color {
    return when {
        moodScore >= 0.5 -> Color(0xFF64FFDA)
        moodScore >= -0.2 -> Color(0xFF82B1FF)
        moodScore >= -0.6 -> Color(0xFFFFAB40)
        else -> Color(0xFFFF5252)
    }
}

data class ProjectedPoint(val x: Float, val y: Float, val z: Float, val scale: Float)

fun projectPoint(nodeX: Float, nodeY: Float, nodeZ: Float, canvasCenter: Offset, yaw: Float, pitch: Float, cameraZ: Float): ProjectedPoint {
    val relX = nodeX - canvasCenter.x
    val relY = nodeY - canvasCenter.y

    val cosYaw = kotlin.math.cos(yaw.toDouble()).toFloat()
    val sinYaw = kotlin.math.sin(yaw.toDouble()).toFloat()
    val x1 = relX * cosYaw - nodeZ * sinYaw
    val z1 = relX * sinYaw + nodeZ * cosYaw
    
    val cosPitch = kotlin.math.cos(pitch.toDouble()).toFloat()
    val sinPitch = kotlin.math.sin(pitch.toDouble()).toFloat()
    val y2 = relY * cosPitch - z1 * sinPitch
    val z2 = relY * sinPitch + z1 * cosPitch

    val focalLength = 800f
    val depth = (z2 + cameraZ).coerceAtLeast(10f)
    val scale = focalLength / depth
    
    return ProjectedPoint(
        x = canvasCenter.x + x1 * scale,
        y = canvasCenter.y + y2 * scale,
        z = z2,
        scale = scale
    )
}
