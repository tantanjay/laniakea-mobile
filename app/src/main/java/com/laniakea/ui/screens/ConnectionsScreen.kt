package com.laniakea.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.laniakea.data.ObjectBoxSentenceVector
import com.laniakea.engine.GraphEdge
import com.laniakea.engine.GraphEngine
import com.laniakea.engine.GraphNode
import com.laniakea.manager.SecurityManager
import com.laniakea.viewmodel.LaniakeaViewModel
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ConnectionsScreen(padding: PaddingValues, vm: LaniakeaViewModel) {
    val allEntries by vm.allEntries.collectAsState()
    val isEngineActive = vm.isEngineActive

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
    
    var selectedNode by remember { mutableStateOf<GraphNode?>(null) }
    
    var vectorsFetched by remember { mutableStateOf(false) }
    var vectors by remember { mutableStateOf<List<ObjectBoxSentenceVector>>(emptyList()) }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val securityManager = remember { SecurityManager(context) }
    
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
    LaunchedEffect(vectorsFetched, layoutWidth, layoutHeight) {
        if (vectorsFetched && layoutWidth > 0 && layoutHeight > 0 && allNodes.isEmpty()) {
            val (initialNodes, initialEdges) = graphEngine.buildInitialGraph(
                entries = allEntries,
                vectors = vectors,
                similarityThreshold = 0.55f,
                width = layoutWidth,
                height = layoutHeight
            )
            allNodes = initialNodes.sortedBy { it.date }
            allEdges = initialEdges
            // Show all nodes immediately — the layout is already settled
            replayProgress = allNodes.size
        }
    }
    
    // Live physics for the "Settle" button only
    LaunchedEffect(isSettling) {
        if (isSettling) {
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
        if (!isEngineActive) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF64FFDA), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Warming up the Vector Engine...",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            return@Box
        }
        
        if (allNodes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF64FFDA), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Building your constellation...",
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

        val visibleNodes = allNodes.take(replayProgress)
        val visibleIds = visibleNodes.map { it.entryId }.toSet()
        val visibleEdges = allEdges.filter { it.source.entryId in visibleIds && it.target.entryId in visibleIds }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val clicked = visibleNodes.find { node ->
                            val dx = node.x - offset.x
                            val dy = node.y - offset.y
                            (dx * dx + dy * dy) < 900f
                        }
                        selectedNode = clicked
                    }
                }
        ) {
            layoutWidth = size.width
            layoutHeight = size.height
            
            // Draw Edges
            visibleEdges.forEach { edge ->
                val normalizedWeight = ((edge.weight - 0.55f) / 0.45f).coerceIn(0f, 1f)
                val edgeAlpha = 0.08f + normalizedWeight * 0.35f
                val edgeWidth = 1f + normalizedWeight * 3f
                
                val avgMood = (edge.source.moodScore + edge.target.moodScore) / 2.0
                val edgeColor = when {
                    avgMood > 1.0 -> Color(0xFF64FFDA)
                    avgMood > 0.0 -> Color(0xFF82B1FF)
                    avgMood > -1.0 -> Color(0xFFFFAB40)
                    else -> Color(0xFFFF5252)
                }
                
                drawLine(
                    color = edgeColor.copy(alpha = edgeAlpha),
                    start = Offset(edge.source.x, edge.source.y),
                    end = Offset(edge.target.x, edge.target.y),
                    strokeWidth = edgeWidth,
                    cap = StrokeCap.Round
                )
            }
            
            // Draw Nodes
            visibleNodes.forEach { node ->
                val isSelected = node == selectedNode
                val nodeColor = getMoodNodeColor(node.moodScore)
                
                val connectionCount = visibleEdges.count { 
                    it.source.entryId == node.entryId || it.target.entryId == node.entryId 
                }
                val baseRadius = 6f + min(connectionCount * 1.5f, 10f)
                val glowRadius = baseRadius * 2.5f
                
                // Outer glow
                drawCircle(
                    color = nodeColor.copy(alpha = if (isSelected) 0.5f else glowPulse * 0.3f),
                    radius = if (isSelected) glowRadius * 1.5f else glowRadius,
                    center = Offset(node.x, node.y)
                )
                
                // Mid glow
                drawCircle(
                    color = nodeColor.copy(alpha = if (isSelected) 0.4f else 0.15f),
                    radius = if (isSelected) baseRadius * 1.8f else baseRadius * 1.4f,
                    center = Offset(node.x, node.y)
                )
                
                // Core
                drawCircle(
                    color = if (isSelected) Color.White else nodeColor,
                    radius = if (isSelected) baseRadius * 1.2f else baseRadius,
                    center = Offset(node.x, node.y)
                )
                
                // Bright center
                drawCircle(
                    color = Color.White.copy(alpha = if (isSelected) 1f else 0.7f),
                    radius = if (isSelected) 4f else 2.5f,
                    center = Offset(node.x, node.y)
                )
                
                // Selection ring
                if (isSelected) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.6f),
                        radius = baseRadius * 2f,
                        center = Offset(node.x, node.y),
                        style = Stroke(width = 1.5f)
                    )
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
        if (selectedNode != null) {
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
                            Text(
                                text = selectedNode!!.theme,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        IconButton(onClick = { selectedNode = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.6f))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(moodLabel, style = MaterialTheme.typography.bodySmall, color = nodeColor)
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    val nodeConnections = visibleEdges.count {
                        it.source.entryId == selectedNode!!.entryId || it.target.entryId == selectedNode!!.entryId
                    }
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
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        onClick = { isSettling = true },
                        containerColor = Color.White.copy(alpha = 0.1f),
                        contentColor = Color.White.copy(alpha = 0.8f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Re-settle")
                    }
                }
            }
            
            // Legend
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
        moodScore > 1.0 -> Color(0xFF64FFDA)
        moodScore > 0.0 -> Color(0xFF82B1FF)
        moodScore > -1.0 -> Color(0xFFFFAB40)
        else -> Color(0xFFFF5252)
    }
}
