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

class MapScreenState(
    private val scope: CoroutineScope
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

    private var settleJob: Job? = null
    private var replayJob: Job? = null

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
