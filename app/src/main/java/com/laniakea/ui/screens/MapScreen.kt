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
import com.laniakea.viewmodel.MapScreenState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import com.laniakea.ui.components.map.ConstellationCanvas
import com.laniakea.ui.components.map.ConstellationLoader
import com.laniakea.ui.components.map.MapControls
import com.laniakea.ui.components.map.MapLegend
import com.laniakea.ui.components.map.MapNodeDetailPanel
import com.laniakea.ui.components.map.MapStatsBadge
import com.laniakea.ui.components.map.MapConnectionsDialog
import com.laniakea.ui.components.map.MapEmptyState
import androidx.core.content.edit
import com.laniakea.data.ObjectBoxManager

import com.laniakea.viewmodel.ColorMode
import com.laniakea.viewmodel.GalaxyStar
import com.laniakea.viewmodel.CameraState

@Composable
fun MapScreen(padding: PaddingValues, vm: LaniakeaViewModel) {
    val allEntries by vm.allEntries.collectAsState()
    val isEngineActive = vm.isEngineActive
    val isEngineLoading = vm.isEngineLoading

    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("map_prefs", android.content.Context.MODE_PRIVATE) }
    val state = remember { MapScreenState(coroutineScope, prefs) }

    val securityManager = remember { SecurityManager(context) }
    
    // Content decryptor lambda — passed to the engine to avoid leaking SecurityManager
    val contentDecryptor: (String) -> String = remember(securityManager) {
        { encrypted: String ->
            try { securityManager.decrypt(encrypted) } catch (_: Exception) { encrypted }
        }
    }

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
            state.vectors = ObjectBoxManager.vectorBox.all
            state.vectorsFetched = true
        }
    }
    
    LaunchedEffect(state.vectorsFetched, allEntries.size, state.layoutWidth, state.layoutHeight, state.entryLimit) {
        state.checkAndBuildGraph(allEntries, contentDecryptor)
    }

    // Continuous Background Animations
    LaunchedEffect(state.layoutMode) {
        var t = 0f
        while (true) {
            delay(16L.milliseconds)
            t += 0.016f
            if (state.selectedNode == null && !state.showDetailPanelInFocusMode && !state.isReplaying) {
                if (state.layoutMode == LayoutMode.GALAXY) {
                    state.camera = state.camera.copy(
                        roll = state.camera.roll - 0.002f, // Spin around the galaxy's center
                        yaw = kotlin.math.cos(t * 0.1f) * 0.1f
                    )
                }
            }
        }
    }
    
    val backgroundGradient = when (vm.theme) {
        "GREEN" -> listOf(Color(0xFF0B1B0F), Color(0xFF163820), Color(0xFF122C19))
        "ORANGE" -> listOf(Color(0xFF261304), Color(0xFF4A2508), Color(0xFF381C06))
        "BLUE" -> listOf(Color(0xFF061524), Color(0xFF0D2A4A), Color(0xFF0A2038))
        else -> listOf(Color(0xFF0A0E21), Color(0xFF1A1A2E), Color(0xFF16213E)) // PURPLE/Default
    }
    
    val accentColor = when (vm.theme) {
        "GREEN" -> Color(0xFF69F0AE) // Light Green accent
        "ORANGE" -> Color(0xFFFFAB40) // Light Orange accent
        "BLUE" -> Color(0xFF40C4FF) // Light Blue accent
        else -> Color(0xFF64FFDA) // Teal accent for Purple theme
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(Brush.verticalGradient(backgroundGradient))
    ) {
        if (!isEngineActive || state.isBuildingGraph) {
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
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.Black)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Initialize Engine")
                        }
                    } else {
                        ConstellationLoader(isEngineActive = isEngineActive, accentColor = accentColor)
                    }
                }
            }
            // Invisible sizer to capture layout dimensions via onSizeChanged
            Box(modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    state.layoutWidth = size.width.toFloat()
                    state.layoutHeight = size.height.toFloat()
                }
            )
            return@Box
        }

        val baseVisibleNodes = state.allNodes.take(state.replayProgress)
        val focusCenter = if (state.isIsolateMode) (state.lockedFocusNode ?: state.selectedNode) else null
        
        val visibleNodes = if (focusCenter != null) {
            val connectedIds = state.allEdges.filter { it.sourceId == focusCenter.entryId || it.targetId == focusCenter.entryId }
                .flatMap { listOf(it.sourceId, it.targetId) }
                .toSet()
            baseVisibleNodes.filter { it.entryId == focusCenter.entryId || it.entryId in connectedIds }
        } else {
            baseVisibleNodes
        }

        val visibleIds = visibleNodes.map { it.entryId }.toSet()
        val visibleEdges = if (focusCenter != null) {
            state.allEdges.filter { (it.sourceId == focusCenter.entryId || it.targetId == focusCenter.entryId) && 
                              it.sourceId in visibleIds && it.targetId in visibleIds }
        } else {
            state.allEdges.filter { it.sourceId in visibleIds && it.targetId in visibleIds }
        }

        ConstellationCanvas(
            visibleNodes = visibleNodes,
            visibleEdges = visibleEdges,
            allNodes = state.allNodes,
            backgroundStars = state.backgroundStars,
            layoutMode = state.layoutMode,
            colorMode = state.colorMode,
            camera = state.camera,
            onCameraChange = { state.camera = it },
            graphEngine = state.graphEngine,
            isIsolateMode = state.isIsolateMode,
            selectedNode = state.selectedNode,
            onNodeTap = { state.selectedNode = it },
            onNodeDoubleTap = { clicked ->
                state.selectedNode = clicked
                state.showDetailPanelInFocusMode = true
            },
            onLayoutSize = { w, h ->
                state.layoutWidth = w
                state.layoutHeight = h
            },
            glowPulse = glowPulse,
            showDecorations = state.showDecorations
        )

        // Stats badge
        MapStatsBadge(
            nodeCount = visibleNodes.size,
            edgeCount = visibleEdges.size,
            modifier = Modifier.align(Alignment.TopEnd)
        )
        
        // Empty state / low node count visual
        if (state.allNodes.size < 10 && !state.isBuildingGraph && state.vectorsFetched && (!state.dismissEmptyState || state.allNodes.isEmpty())) {
            MapEmptyState(
                isEmpty = state.allNodes.isEmpty(),
                accentColor = accentColor,
                glowPulse = glowPulse,
                onClose = { state.dismissEmptyState = true }
            )
        }
        
        // Node detail panel
        val showPanel = (!state.isIsolateMode && state.selectedNode != null) || (state.isIsolateMode && state.showDetailPanelInFocusMode && state.selectedNode != null)
        if (showPanel) {
            val nodeToShow = state.selectedNode!!
            val decryptedContent = remember(nodeToShow) {
                try {
                    securityManager.decrypt(nodeToShow.content)
                } catch (_: Exception) {
                    nodeToShow.content
                }
            }
            val nodeColor = if (state.colorMode == ColorMode.MOOD) getMoodNodeColor(nodeToShow.moodScore) else getCommunityColor(nodeToShow.clusterName)
            val moodLabel = getMoodLabel(nodeToShow.moodScore)
            
            MapNodeDetailPanel(
                nodeToShow = nodeToShow,
                decryptedContent = decryptedContent,
                nodeColor = nodeColor,
                moodLabel = moodLabel,
                nodeConnections = visibleEdges.count { it.sourceId == nodeToShow.entryId || it.targetId == nodeToShow.entryId },
                isIsolateMode = state.isIsolateMode,
                onClose = {
                    if (state.isIsolateMode) state.showDetailPanelInFocusMode = false else state.selectedNode = null
                },
                onViewAll = { state.showConnectionsFor = nodeToShow },
                onFocus = {
                    state.isIsolateMode = true
                    state.lockedFocusNode = nodeToShow
                    state.showDetailPanelInFocusMode = false
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
                layoutMode = state.layoutMode,
                onLayoutModeChange = { state.updateLayoutMode(it) },
                colorMode = state.colorMode,
                onColorModeChange = { state.updateColorMode(it) },
                showDecorations = state.showDecorations,
                onToggleDecorations = { state.toggleDecorations() },
                isSettling = state.isSettling
            )
            
            // Legend
            if (state.colorMode == ColorMode.MOOD) {
                Spacer(modifier = Modifier.height(12.dp))
                MapLegend()
            }
        }
        
        // Helper Label (Bottom Left)
        if (state.isIsolateMode && !state.showDetailPanelInFocusMode) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                color = accentColor.copy(alpha = 0.15f)
            ) {
                Text(
                    "Double-tap a node to view details",
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        
        // Floating Controls (Bottom Right)
        if (state.isIsolateMode) {
            val isLocked = state.lockedFocusNode != null
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        state.lockedFocusNode = if (isLocked) {
                            null
                        } else {
                            state.selectedNode
                        }
                    },
                    containerColor = Color.White.copy(alpha = 0.1f),
                    contentColor = if (isLocked) Color(0xFFFF5252) else accentColor
                ) {
                    Icon(
                        if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Lock Focus"
                    )
                }
                FloatingActionButton(
                    onClick = { 
                        state.isIsolateMode = false 
                        state.lockedFocusNode = null
                        state.showDetailPanelInFocusMode = false
                    },
                    containerColor = Color.White.copy(alpha = 0.1f),
                    contentColor = accentColor
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Exit Focus Mode")
                }
            }
        } else {
            val fabBottomOffset = if (state.selectedNode != null) 220.dp else 16.dp
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = fabBottomOffset)
            ) {
                if (state.isReplaying) {
                    FloatingActionButton(
                        onClick = {
                            state.stopReplay(state.layoutMode)
                            state.camera = state.camera.reset()
                        },
                        containerColor = Color(0xFF2D1B1B),
                        contentColor = Color(0xFFFF5252)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop Replay")
                    }
                } else {
                    FloatingActionButton(
                        onClick = { 
                            state.startReplay(state.layoutMode) { rollDelta -> state.camera = state.camera.copy(roll = state.camera.roll - rollDelta) } 
                        },
                        containerColor = accentColor.copy(alpha = 0.15f),
                        contentColor = accentColor
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Replay Timeline")
                    }
                }
                
                FloatingActionButton(
                    onClick = { state.showInfoDialog = true },
                    containerColor = Color.White.copy(alpha = 0.1f),
                    contentColor = Color.White.copy(alpha = 0.8f)
                ) {
                    Icon(Icons.Default.Info, contentDescription = "Layout Info")
                }
                
                if (!state.isSettling && !state.isReplaying) {
                    FloatingActionButton(
                        onClick = { state.camera = state.camera.reset() },
                        containerColor = Color.White.copy(alpha = 0.1f),
                        contentColor = Color.White.copy(alpha = 0.8f)
                    ) {
                        Icon(Icons.Default.Home, contentDescription = "Reset View")
                    }
                    FloatingActionButton(
                        onClick = { 
                            state.startSettling(state.layoutMode)
                            state.camera = state.camera.copy(cameraX = 0f, cameraY = 0f)
                        },
                        containerColor = Color.White.copy(alpha = 0.1f),
                        contentColor = Color.White.copy(alpha = 0.8f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Re-settle")
                    }
                    if (allEntries.size > state.entryLimit) {
                        FloatingActionButton(
                            onClick = { state.entryLimit += 1000 },
                            containerColor = Color.White.copy(alpha = 0.1f),
                            contentColor = Color.White.copy(alpha = 0.8f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Load More")
                        }
                    }
                }
            }
        }
        
        if (state.showConnectionsFor != null) {
            MapConnectionsDialog(
                targetNode = state.showConnectionsFor!!,
                allEdges = state.allEdges,
                nodeMap = state.nodeMap,
                securityManager = securityManager,
                onDismiss = { state.showConnectionsFor = null }
            )
        }
        
        if (state.showInfoDialog) {
            com.laniakea.ui.components.map.MapInfoDialog(
                layoutMode = state.layoutMode,
                onDismiss = { state.showInfoDialog = false }
            )
        }
    }
}