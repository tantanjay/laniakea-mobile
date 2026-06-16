package com.laniakea.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.laniakea.data.ObjectBoxSentenceVector
import com.laniakea.engine.GraphEdge
import com.laniakea.engine.GraphEngine
import com.laniakea.engine.GraphNode
import com.laniakea.engine.LayoutMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.graphics.Color
import android.content.SharedPreferences
import androidx.core.content.edit
import com.laniakea.data.DiaryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ColorMode { MOOD, COMMUNITY }

data class GalaxyStar(val x: Float, val y: Float, val z: Float, val size: Float, val color: Color)

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
    
    fun resetForMode(mode: LayoutMode): CameraState {
        return if (mode == LayoutMode.TIME_WARP) {
            CameraState(pitch = 0f, cameraZ = 800f)
        } else {
            CameraState()
        }
    }
}

class MapScreenState(
    private val scope: CoroutineScope,
    private val prefs: SharedPreferences? = null
) {
    val graphEngine = GraphEngine()
    
    var allNodes by mutableStateOf<List<GraphNode>>(emptyList())
    var allEdges by mutableStateOf<List<GraphEdge>>(emptyList())
    var nodeMap by mutableStateOf<Map<Long, GraphNode>>(emptyMap())
    
    var isReplaying by mutableStateOf(false)
    var replayProgress by mutableIntStateOf(0)
    var isSettling by mutableStateOf(false)

    var layoutWidth by mutableFloatStateOf(0f)
    var layoutHeight by mutableFloatStateOf(0f)

    var vectors by mutableStateOf<List<ObjectBoxSentenceVector>>(emptyList())

    var colorMode by mutableStateOf(ColorMode.valueOf(prefs?.getString("color_mode", ColorMode.MOOD.name) ?: ColorMode.MOOD.name))
    var layoutMode by mutableStateOf(LayoutMode.valueOf(prefs?.getString("layout_mode", LayoutMode.CLUSTERS.name) ?: LayoutMode.CLUSTERS.name))
    var showDecorations by mutableStateOf(prefs?.getBoolean("show_decorations", true) ?: true)
    
    var camera by mutableStateOf(CameraState())
    var selectedNode by mutableStateOf<GraphNode?>(null)
    var isIsolateMode by mutableStateOf(false)
    var lockedFocusNode by mutableStateOf<GraphNode?>(null)
    var showConnectionsFor by mutableStateOf<GraphNode?>(null)
    var showDetailPanelInFocusMode by mutableStateOf(false)
    var showInfoDialog by mutableStateOf(false)

    var vectorsFetched by mutableStateOf(false)
    var hasBuiltGraph by mutableStateOf(false)
    var isBuildingGraph by mutableStateOf(false)
    var dismissEmptyState by mutableStateOf(false)
    var entryLimit by mutableIntStateOf(1000)

    var backgroundStars by mutableStateOf<List<GalaxyStar>>(emptyList())

    private var settleJob: Job? = null
    private var replayJob: Job? = null

    init {
        generateBackgroundStars()
    }

    private fun generateBackgroundStars() {
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
        backgroundStars = list
    }

    fun updateColorMode(newMode: ColorMode) {
        colorMode = newMode
        prefs?.edit { putString("color_mode", newMode.name) }
    }

    fun updateLayoutMode(newMode: LayoutMode) {
        layoutMode = newMode
        prefs?.edit { putString("layout_mode", newMode.name) }
        if (hasBuiltGraph && !isReplaying) {
            startSettling(newMode)
            camera = camera.resetForMode(newMode)
        }
    }

    fun toggleDecorations() {
        showDecorations = !showDecorations
        prefs?.edit { putBoolean("show_decorations", showDecorations) }
    }

    fun checkAndBuildGraph(entries: List<DiaryEntry>, contentDecryptor: (String) -> String) {
        if (!vectorsFetched || entries.isEmpty() || layoutWidth <= 0 || layoutHeight <= 0) return
        
        val entriesToProcess = entries.sortedByDescending { it.dateTime }.take(entryLimit)
        if (hasBuiltGraph && allNodes.size == entriesToProcess.size) return
        
        isBuildingGraph = true
        
        scope.launch {
            val entriesIds = entriesToProcess.map { it.id }.toSet()
            val vectorsToProcess = vectors.filter { it.entryId in entriesIds }
            
            val (initialNodes, initialEdges) = withContext(Dispatchers.Default) {
                graphEngine.layoutMode = layoutMode
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
            nodeMap = allNodes.associateBy { it.entryId }
            replayProgress = allNodes.size
            hasBuiltGraph = true
            isBuildingGraph = false
        }
    }

    fun startSettling(layoutMode: LayoutMode) {
        isSettling = true
        settleJob?.cancel()
        settleJob = scope.launch {
            graphEngine.layoutMode = layoutMode
            graphEngine.startLiveSimulation()
            var steps = 0
            while (isSettling && steps < 150) {
                delay(16L.milliseconds)
                graphEngine.applyLiveStep(allNodes, allEdges, nodeMap, layoutWidth, layoutHeight, 150)
                allNodes = allNodes.toList() // trigger recomposition
                steps++
            }
            isSettling = false
        }
    }

    fun startReplay(layoutMode: LayoutMode, onCameraSpin: (Float) -> Unit) {
        isReplaying = true
        replayProgress = 0
        replayJob?.cancel()
        replayJob = scope.launch {
            delay(300L.milliseconds)

            // Progressive reveal
            val revealJob = launch {
                while (replayProgress < allNodes.size && isReplaying) {
                    replayProgress++
                    val delayMs = if (allNodes.size > 30) 60L else 200L
                    delay(delayMs.milliseconds)
                }
            }

            // Post-replay continuous animation
            var animTime = 0f
            while (isReplaying) {
                delay(16L.milliseconds)
                animTime += 0.016f

                if (layoutMode == LayoutMode.CLUSTERS) {
                    onCameraSpin(0.003f)
                } else if (layoutMode == LayoutMode.TIME_WARP) {
                    graphEngine.state.warpTimeOffset += 0.0015f
                    graphEngine.applyLiveStep(allNodes, allEdges, nodeMap, layoutWidth, layoutHeight)
                    allNodes = allNodes.toList() // trigger recomposition
                }
            }
            revealJob.cancel()
        }
    }

    fun stopReplay(layoutMode: LayoutMode) {
        isReplaying = false
        replayJob?.cancel()
        if (layoutMode == LayoutMode.TIME_WARP && graphEngine.state.warpTimeOffset > 0f) {
            graphEngine.state.warpTimeOffset = 0f
            startSettling(layoutMode)
        }
    }
}
