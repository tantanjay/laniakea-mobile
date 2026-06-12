package com.laniakea.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
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
import com.laniakea.engine.GraphNode
import com.laniakea.engine.GraphEdge
import com.laniakea.engine.GraphTestEngine
import com.laniakea.viewmodel.LaniakeaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

data class GalaxyStar(val x: Float, val y: Float, val z: Float, val size: Float, val color: Color)

@Composable
fun GalaxyTestScreen(padding: PaddingValues, vm: LaniakeaViewModel) {
    val graphEngine = remember { GraphTestEngine() }
    val isEngineActive = vm.isEngineActive
    val isEngineLoading = vm.isEngineLoading
    val allEntries by vm.allEntries.collectAsState()
    
    var vectorsFetched by remember { mutableStateOf(false) }
    var vectors by remember { mutableStateOf<List<com.laniakea.data.ObjectBoxSentenceVector>>(emptyList()) }
    
    LaunchedEffect(isEngineActive) {
        if (isEngineActive) {
            vectors = com.laniakea.data.ObjectBoxManager.vectorBox.all
            vectorsFetched = true
        }
    }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val securityManager = remember { com.laniakea.manager.SecurityManager(context) }

    var allNodes by remember { mutableStateOf<List<GraphNode>>(emptyList()) }
    var allEdges by remember { mutableStateOf<List<GraphEdge>>(emptyList()) }
    var hasBuiltGraph by remember { mutableStateOf(false) }
    var layoutWidth by remember { mutableFloatStateOf(1f) }
    var layoutHeight by remember { mutableFloatStateOf(1f) }

    var selectedNode by remember { mutableStateOf<GraphNode?>(null) }
    var showDetailPanel by remember { mutableStateOf(false) }

    var yaw by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(-0.6f) }
    var roll by remember { mutableFloatStateOf(0f) }
    var cameraX by remember { mutableFloatStateOf(0f) }
    var cameraY by remember { mutableFloatStateOf(0f) }
    var cameraZ by remember { mutableFloatStateOf(800f) }
    var activePointers by remember { mutableIntStateOf(0) }
    var showDeadStars by remember { mutableStateOf(false) }
    var globalTime by remember { mutableFloatStateOf(0f) }
    
    var showConnectionsFor by remember { mutableStateOf<GraphNode?>(null) }

    // Generate Galaxy once
    val backgroundStars = remember {
        val list = mutableListOf<GalaxyStar>()
        val numStars = 4000
        val numArms = 2
        val random = Random()

        for (i in 0 until numStars) {
            val isCore = i < numStars * 0.35f
            if (isCore) {
                val r = (kotlin.math.abs(random.nextGaussian()) * 150f).toFloat()
                val theta = random.nextDouble() * 2 * Math.PI
                val phi = acos(2 * random.nextDouble() - 1)
                
                val x = r * sin(phi) * cos(theta)
                val y = r * sin(phi) * sin(theta)
                val z = r * cos(phi) * 0.8
                
                val color = Color(0xFFFFFFFF)
                val size = random.nextFloat() * 1.5f + 0.5f
                list.add(GalaxyStar(x.toFloat(), y.toFloat(), z.toFloat(), size, color))
            } else {
                val armIndex = i % numArms
                val dist = (random.nextFloat() * 1000f) + 80f
                val baseAngle = dist * 0.004f + (armIndex * (2 * Math.PI / numArms))
                val spread = (random.nextGaussian() * (dist * 0.18f)).toFloat()
                val angle = baseAngle + (spread * 0.002f)
                val x = (cos(angle) * dist).toFloat() + spread * cos(angle + Math.PI/2).toFloat()
                val y = (sin(angle) * dist).toFloat() + spread * sin(angle + Math.PI/2).toFloat()
                val zThickness = (1000f - dist).coerceAtLeast(0f) * 0.03f
                val z = (random.nextGaussian() * zThickness).toFloat()
                
                // Dead stars are faint white
                val color = Color(0xFFB0B0B0)
                val size = random.nextFloat() * 2.0f + 0.5f
                list.add(GalaxyStar(x, y, z, size, color))
            }
        }
        list
    }

    // Build the live graph when active
    LaunchedEffect(isEngineActive, vectorsFetched, allEntries, layoutWidth) {
        if (isEngineActive && vectorsFetched && allEntries.isNotEmpty() && layoutWidth > 1f && !hasBuiltGraph) {
            val entriesIds = allEntries.map { it.id }.toSet()
            val vectorsToProcess = vectors.filter { it.entryId in entriesIds }
            val (initialNodes, initialEdges) = withContext(Dispatchers.Default) {
                graphEngine.buildGraph(
                    entries = allEntries,
                    vectors = vectorsToProcess,
                    similarityThreshold = 0.55f,
                    width = layoutWidth,
                    height = layoutHeight,
                    securityManager = securityManager
                )
            }
            allNodes = initialNodes
            allEdges = initialEdges
            hasBuiltGraph = true
        }
    }

    // Animation Loop
    LaunchedEffect(Unit) {
        var t = 0f
        while (true) {
            delay(16.milliseconds)
            t += 0.016f
            globalTime = t
            
            // Pause the 3D spinning while the user is reading an entry
            if (!showDetailPanel) {
                roll -= 0.002f // Spin around the galaxy's center
                yaw = cos(t * 0.1f) * 0.1f
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(Brush.verticalGradient(listOf(Color(0xFF020205), Color(0xFF0A0A14))))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        val center = Offset(layoutWidth / 2f, layoutHeight / 2f)
                        val projectedNodes = allNodes.map { node ->
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
                            showDetailPanel = true
                        }
                    }
                )
            }
    ) {
        if (!isEngineActive) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!isEngineLoading) {
                        Button(onClick = { vm.initializeEngine() }) {
                            Text("Initialize Engine")
                        }
                    } else {
                        CircularProgressIndicator()
                    }
                }
            }
        }

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
                    detectTransformGestures { centroid, pan, zoom, rotation ->
                        if (zoom != 1f) {
                            cameraZ = (cameraZ - (zoom - 1f) * 1000f).coerceIn(-1000f, 3000f)
                        }
                        if (activePointers >= 3) {
                            cameraX -= pan.x * 2f
                            cameraY -= pan.y * 2f
                        } else {
                            yaw += pan.x * 0.005f
                            pitch -= pan.y * 0.005f
                        }
                    }
                }
        ) {
            layoutWidth = size.width
            layoutHeight = size.height
            val canvasCenter = Offset(size.width / 2f, size.height / 2f)
            
            // 1. Draw Background Procedural Stars
            if (showDeadStars) {
                val projectedStars = backgroundStars.map { star ->
                    star to projectPoint(star.x + canvasCenter.x, star.y + canvasCenter.y, star.z, canvasCenter, yaw, pitch, roll, cameraX, cameraY, cameraZ)
                }.sortedByDescending { it.second.z }
                
                projectedStars.forEach { (star, p) ->
                    if (p.z + cameraZ > 0) {
                        val scale = p.scale.coerceAtMost(3f)
                        val alphaFactor = p.scale.coerceIn(0.1f, 1f)
                        drawCircle(color = star.color.copy(alpha = alphaFactor * 0.3f), radius = star.size * scale * 2.5f, center = Offset(p.x, p.y))
                        drawCircle(color = star.color.copy(alpha = alphaFactor * 0.9f), radius = star.size * scale, center = Offset(p.x, p.y))
                    }
                }
            }

            // 2. Draw Live Edges
            allEdges.forEach { edge ->
                val p1 = projectPoint(edge.source.x, edge.source.y, edge.source.z, canvasCenter, yaw, pitch, roll, cameraX, cameraY, cameraZ)
                val p2 = projectPoint(edge.target.x, edge.target.y, edge.target.z, canvasCenter, yaw, pitch, roll, cameraX, cameraY, cameraZ)
                
                if (p1.z + cameraZ > 0 && p2.z + cameraZ > 0) {
                    val normalizedWeight = ((edge.weight - 0.55f) / 0.45f).coerceIn(0f, 1f)
                    val avgScale = (p1.scale + p2.scale) / 2f
                    val drawScale = avgScale.coerceAtMost(3f)
                    val visualScale = if (drawScale < 1f) drawScale * drawScale else drawScale
                    val edgeAlpha = (0.04f + normalizedWeight * 0.25f) * drawScale.coerceIn(0.05f, 1f)
                    
                    drawLine(
                        color = Color(0xFF64FFDA).copy(alpha = edgeAlpha),
                        start = Offset(p1.x, p1.y),
                        end = Offset(p2.x, p2.y),
                        strokeWidth = (0.5f + normalizedWeight * 2f) * visualScale,
                        cap = StrokeCap.Round
                    )
                }
            }

            // 3. Draw Live Nodes
            val projectedNodes = allNodes.map { node ->
                node to projectPoint(node.x, node.y, node.z, canvasCenter, yaw, pitch, roll, cameraX, cameraY, cameraZ)
            }.sortedByDescending { it.second.z }
            
            projectedNodes.forEach { (node, p) ->
                if (p.z + cameraZ > 0) {
                    val isSelected = node == selectedNode
                    val drawScale = p.scale.coerceAtMost(3f)
                    val visualScale = if (drawScale < 1f) drawScale * drawScale else drawScale
                    val alphaFactor = p.scale.coerceIn(0.1f, 1f)
                    
                    val importanceSq = node.importance * node.importance
                    val importanceScale = 1f + (importanceSq * 1.0f)
                    val baseRadius = 3.5f * visualScale * importanceScale
                    val glowRadius = baseRadius * (1.8f + node.importance * 1.2f)
                    
                    val nodeColor = getCommunityColor(node.clusterName)
                    val glowPulse = 0.8f + 0.2f * sin(globalTime * 2f + node.entryId.toFloat())
                    
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
                    
                    if (isSelected) {
                        drawCircle(color = nodeColor, radius = 2.5f * drawScale, center = Offset(p.x, p.y), style = Stroke(width = 1.0f * drawScale))
                    }
                }
            }
        }
        
        if (hasBuiltGraph && activePointers == 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "3-Finger Pan • Pinch Zoom",
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.Center)
                )
                
                OutlinedButton(
                    onClick = { showDeadStars = !showDeadStars },
                    modifier = Modifier.align(Alignment.CenterEnd),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(if (showDeadStars) "Hide Stars" else "Show Stars", color = Color.White)
                }
            }
        }
        
        if (showDetailPanel && selectedNode != null) {
            val nodeToShow = selectedNode!!
            val decryptedContent = remember(nodeToShow) {
                try {
                    securityManager.decrypt(nodeToShow.content)
                } catch (_: Exception) {
                    nodeToShow.content
                }
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
                    val nodeConnections = allEdges.count {
                        it.source.entryId == nodeToShow.entryId || it.target.entryId == nodeToShow.entryId
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Canvas(modifier = Modifier.size(12.dp)) {
                                drawCircle(color = getCommunityColor(nodeToShow.clusterName), radius = size.minDimension / 2f)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            val moodSmiley = when {
                                nodeToShow.moodScore >= 0.5 -> "😁"
                                nodeToShow.moodScore >= 0.1 -> "🙂"
                                nodeToShow.moodScore > -0.1 -> "😐"
                                nodeToShow.moodScore > -0.5 -> "🙁"
                                else -> "😢"
                            }
                            Text(
                                text = moodSmiley,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            
                            if (nodeToShow.clusterName != "Unknown") {
                                Text(
                                    text = nodeToShow.clusterName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (nodeConnections > 0) {
                                TextButton(
                                    onClick = { showConnectionsFor = nodeToShow },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text("View All", style = MaterialTheme.typography.labelSmall, color = Color(0xFF82B1FF))
                                }
                            }
                            IconButton(onClick = { 
                                showDetailPanel = false
                                selectedNode = null 
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.6f))
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Connected to $nodeConnections other thoughts",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64FFDA).copy(alpha = 0.7f)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.05f)
                    ) {
                        Text(
                            text = decryptedContent,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier
                                .padding(12.dp)
                                .heightIn(max = 150.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }
        
        if (showConnectionsFor != null) {
            val connectedEdges = allEdges.filter {
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
                                            if (relatedNode.clusterName != "Unknown") {
                                                Text(
                                                    relatedNode.clusterName,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = Color(0xFF82B1FF),
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
