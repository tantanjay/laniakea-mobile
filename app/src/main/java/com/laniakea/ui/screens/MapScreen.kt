package com.laniakea.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.laniakea.data.ObjectBoxSentenceVector
import com.laniakea.engine.GraphEdge
import com.laniakea.engine.GraphEngine
import com.laniakea.engine.GraphNode
import com.laniakea.engine.LayoutMode
import com.laniakea.util.*
import com.laniakea.manager.SecurityManager
import com.laniakea.viewmodel.LaniakeaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import com.laniakea.ui.components.map.ConstellationCanvas
import com.laniakea.ui.components.map.MapControls
import com.laniakea.ui.components.map.MapLegend
import com.laniakea.ui.components.map.MapNodeDetailPanel
import com.laniakea.ui.components.map.MapStatsBadge
import com.laniakea.ui.components.map.MapConnectionsDialog
import kotlinx.coroutines.launch

enum class ColorMode { MOOD, COMMUNITY }

data class GalaxyStar(val x: Float, val y: Float, val z: Float, val size: Float, val color: Color)

/**
 * Immutable camera state with a companion providing sensible defaults.
 * Eliminates duplicated reset values that were previously scattered across the file.
 */
data class CameraState(
    val yaw: Float = DEFAULT_YAW,
    val pitch: Float = DEFAULT_PITCH,
    val roll: Float = DEFAULT_ROLL,
    val cameraX: Float = DEFAULT_CAMERA_X,
    val cameraY: Float = DEFAULT_CAMERA_Y,
    val cameraZ: Float = DEFAULT_CAMERA_Z,
) {
    companion object {
        const val DEFAULT_YAW = 0f
        const val DEFAULT_PITCH = -0.6f
        const val DEFAULT_ROLL = 0f
        const val DEFAULT_CAMERA_X = 0f
        const val DEFAULT_CAMERA_Y = 0f
        const val DEFAULT_CAMERA_Z = 800f
    }

    fun reset(): CameraState = CameraState()
}

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
    var showInfoDialog by remember { mutableStateOf(false) }
    
    val graphEngine = remember { GraphEngine() }
    var layoutWidth by remember { mutableFloatStateOf(0f) }
    var layoutHeight by remember { mutableFloatStateOf(0f) }
    
    var camera by remember { mutableStateOf(CameraState()) }
    
    var selectedNode by remember { mutableStateOf<GraphNode?>(null) }
    var isIsolateMode by remember { mutableStateOf(false) }
    var lockedFocusNode by remember { mutableStateOf<GraphNode?>(null) }
    var showConnectionsFor by remember { mutableStateOf<GraphNode?>(null) }
    var showDetailPanelInFocusMode by remember { mutableStateOf(false) }
    
    var vectorsFetched by remember { mutableStateOf(false) }
    var vectors by remember { mutableStateOf<List<ObjectBoxSentenceVector>>(emptyList()) }
    
    var hasBuiltGraph by remember { mutableStateOf(false) }
    var isBuildingGraph by remember { mutableStateOf(false) }
    
    var entryLimit by remember { mutableIntStateOf(300) }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val securityManager = remember { SecurityManager(context) }
    
    // Build a node map for ID-based edge lookups
    val nodeMap = remember(allNodes) { allNodes.associateBy { it.entryId } }
    
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
    
    // Content decryptor lambda — passed to the engine to avoid leaking SecurityManager
    val contentDecryptor: (String) -> String = remember(securityManager) {
        { encrypted: String ->
            try { securityManager.decrypt(encrypted) } catch (_: Exception) { encrypted }
        }
    }
    
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
                    width = layoutWidth,
                    height = layoutHeight,
                    contentDecryptor = contentDecryptor
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
                graphEngine.applyLiveStep(allNodes, allEdges, nodeMap, layoutWidth, layoutHeight)
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
            launch {
                while (replayProgress < allNodes.size && isReplaying) {
                    replayProgress++
                    // Faster for large graphs, slower for small
                    val delayMs = if (allNodes.size > 30) 60L else 200L
                    delay(delayMs.milliseconds)
                }
            }
            
            // Post-replay continuous animation
            var animTime = 0f
            while (isReplaying) {
                delay(16L.milliseconds) // ~60fps
                animTime += 0.016f
                
                if (layoutMode == LayoutMode.CLUSTERS) {
                    // Spin the clusters slowly like a disk
                    camera = camera.copy(roll = camera.roll - 0.003f)
                } else if (layoutMode == LayoutMode.TIME_WARP) {
                    // Warpy time tunnel animation: physically flow the nodes along the tube
                    graphEngine.state.warpTimeOffset += 0.0015f
                    graphEngine.applyLiveStep(allNodes, allEdges, nodeMap, layoutWidth, layoutHeight)
                    allNodes = allNodes.toList() // trigger recomposition
                }
            }
        } else {
            if (layoutMode == LayoutMode.TIME_WARP && graphEngine.state.warpTimeOffset > 0f) {
                graphEngine.state.warpTimeOffset = 0f
                isSettling = true
            }
        }
    }
    
    // Continuous Background Animations
    LaunchedEffect(layoutMode) {
        var t = 0f
        while (true) {
            delay(16L.milliseconds)
            t += 0.016f
            if (selectedNode == null && !showDetailPanelInFocusMode && !isReplaying) {
                if (layoutMode == LayoutMode.GALAXY) {
                    camera = camera.copy(
                        roll = camera.roll - 0.002f, // Spin around the galaxy's center
                        yaw = kotlin.math.cos(t * 0.1f) * 0.1f
                    )
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
            // Invisible sizer to capture layout dimensions via onSizeChanged
            Box(modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    layoutWidth = size.width.toFloat()
                    layoutHeight = size.height.toFloat()
                }
            )
            return@Box
        }

        val baseVisibleNodes = allNodes.take(replayProgress)
        val focusCenter = if (isIsolateMode) (lockedFocusNode ?: selectedNode) else null
        
        val visibleNodes = if (focusCenter != null) {
            val connectedIds = allEdges.filter { it.sourceId == focusCenter.entryId || it.targetId == focusCenter.entryId }
                .flatMap { listOf(it.sourceId, it.targetId) }
                .toSet()
            baseVisibleNodes.filter { it.entryId == focusCenter.entryId || it.entryId in connectedIds }
        } else {
            baseVisibleNodes
        }

        val visibleIds = visibleNodes.map { it.entryId }.toSet()
        val visibleEdges = if (focusCenter != null) {
            allEdges.filter { (it.sourceId == focusCenter.entryId || it.targetId == focusCenter.entryId) && 
                              it.sourceId in visibleIds && it.targetId in visibleIds }
        } else {
            allEdges.filter { it.sourceId in visibleIds && it.targetId in visibleIds }
        }

        ConstellationCanvas(
            visibleNodes = visibleNodes,
            visibleEdges = visibleEdges,
            allNodes = allNodes,
            nodeMap = nodeMap,
            backgroundStars = backgroundStars,
            layoutMode = layoutMode,
            colorMode = colorMode,
            camera = camera,
            onCameraChange = { camera = it },
            graphEngine = graphEngine,
            isIsolateMode = isIsolateMode,
            selectedNode = selectedNode,
            onNodeTap = { selectedNode = it },
            onNodeDoubleTap = { clicked ->
                selectedNode = clicked
                showDetailPanelInFocusMode = true
            },
            onLayoutSize = { w, h ->
                layoutWidth = w
                layoutHeight = h
            },
            glowPulse = glowPulse
        )

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
            val moodLabel = getMoodLabel(nodeToShow.moodScore)
            
            MapNodeDetailPanel(
                nodeToShow = nodeToShow,
                decryptedContent = decryptedContent,
                nodeColor = nodeColor,
                moodLabel = moodLabel,
                nodeConnections = visibleEdges.count { it.sourceId == nodeToShow.entryId || it.targetId == nodeToShow.entryId },
                isIsolateMode = isIsolateMode,
                onClose = {
                    if (isIsolateMode) showDetailPanelInFocusMode = false else selectedNode = null
                },
                onViewAll = { showConnectionsFor = nodeToShow },
                onFocus = {
                    isIsolateMode = true
                    lockedFocusNode = nodeToShow
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
        
        // Helper Label (Bottom Left)
        if (isIsolateMode && !showDetailPanelInFocusMode) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                color = Color(0xFF64FFDA).copy(alpha = 0.15f)
            ) {
                Text(
                    "Double-tap a node to view details",
                    color = Color(0xFF64FFDA),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        
        // Floating Controls (Bottom Right)
        if (isIsolateMode) {
            val isLocked = lockedFocusNode != null
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        lockedFocusNode = if (isLocked) {
                            null
                        } else {
                            selectedNode
                        }
                    },
                    containerColor = Color.White.copy(alpha = 0.1f),
                    contentColor = if (isLocked) Color(0xFFFF5252) else Color(0xFF64FFDA)
                ) {
                    Icon(
                        if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Lock Focus"
                    )
                }
                FloatingActionButton(
                    onClick = { 
                        isIsolateMode = false 
                        lockedFocusNode = null
                        showDetailPanelInFocusMode = false
                    },
                    containerColor = Color.White.copy(alpha = 0.1f),
                    contentColor = Color(0xFF64FFDA)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Exit Focus Mode")
                }
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
                            camera = camera.reset()
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
                
                FloatingActionButton(
                    onClick = { showInfoDialog = true },
                    containerColor = Color.White.copy(alpha = 0.1f),
                    contentColor = Color.White.copy(alpha = 0.8f)
                ) {
                    Icon(Icons.Default.Info, contentDescription = "Layout Info")
                }
                
                if (!isSettling && !isReplaying) {
                    FloatingActionButton(
                        onClick = { camera = camera.reset() },
                        containerColor = Color.White.copy(alpha = 0.1f),
                        contentColor = Color.White.copy(alpha = 0.8f)
                    ) {
                        Icon(Icons.Default.Home, contentDescription = "Reset View")
                    }
                    FloatingActionButton(
                        onClick = { 
                            isSettling = true 
                            camera = camera.copy(cameraX = 0f, cameraY = 0f)
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
                allEdges = allEdges,
                nodeMap = nodeMap,
                securityManager = securityManager,
                onDismiss = { showConnectionsFor = null }
            )
        }
        
        if (showInfoDialog) {
            com.laniakea.ui.components.map.MapInfoDialog(
                layoutMode = layoutMode,
                onDismiss = { showInfoDialog = false }
            )
        }
    }
}