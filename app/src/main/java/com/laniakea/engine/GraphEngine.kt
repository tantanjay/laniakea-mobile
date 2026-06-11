package com.laniakea.engine

import com.laniakea.data.DiaryEntry
import com.laniakea.data.ObjectBoxSentenceVector
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class GraphNode(
    val entryId: Long,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    val date: Long,
    val theme: String,
    val moodScore: Double,
    val content: String
)

data class GraphEdge(
    val source: GraphNode,
    val target: GraphNode,
    val weight: Float
)

class GraphEngine {
    
    fun buildInitialGraph(
        entries: List<DiaryEntry>,
        vectors: List<ObjectBoxSentenceVector>,
        similarityThreshold: Float = 0.55f,
        width: Float,
        height: Float
    ): Pair<List<GraphNode>, List<GraphEdge>> {
        val vectorMap = vectors.associateBy { it.entryId }
        val validEntries = entries.filter { vectorMap.containsKey(it.id) }
        
        val centerX = width / 2f
        val centerY = height / 2f
        val initRadius = min(width, height) * 0.18f
        
        val nodes = validEntries.mapIndexed { index, entry ->
            val v = vectorMap[entry.id]!!
            val angle = (2.0 * PI * index / validEntries.size.coerceAtLeast(1)).toFloat()
            val jitter = 15f
            GraphNode(
                entryId = entry.id,
                x = centerX + cos(angle) * initRadius + (Random.nextFloat() - 0.5f) * jitter,
                y = centerY + sin(angle) * initRadius + (Random.nextFloat() - 0.5f) * jitter,
                date = entry.dateTime,
                theme = v.semanticTheme ?: "Unknown",
                moodScore = entry.numericMood,
                content = entry.content
            )
        }
        
        val nodeMap = nodes.associateBy { it.entryId }
        val edges = mutableListOf<GraphEdge>()
        
        for (i in validEntries.indices) {
            for (j in i + 1 until validEntries.size) {
                val e1 = validEntries[i]
                val e2 = validEntries[j]
                val v1 = vectorMap[e1.id]!!.vector
                val v2 = vectorMap[e2.id]!!.vector
                
                if (v1 != null && v2 != null) {
                    val sim = cosineSimilarity(v1, v2)
                    if (sim >= similarityThreshold) {
                        edges.add(GraphEdge(nodeMap[e1.id]!!, nodeMap[e2.id]!!, sim))
                    }
                }
            }
        }
        
        // Pre-settle the layout so it appears stable immediately
        settleLayout(nodes, edges, width, height)
        
        return Pair(nodes, edges)
    }
    
    /**
     * Run the simulation off-screen until it converges.
     * Called once during graph construction — the user never sees this.
     */
    private fun settleLayout(
        nodes: List<GraphNode>,
        edges: List<GraphEdge>,
        width: Float,
        height: Float,
        maxIterations: Int = 400
    ) {
        if (nodes.size < 2) return
        for (step in 0 until maxIterations) {
            applyLayoutStep(nodes, edges, width, height, step, maxIterations)
        }
        // Zero out residual velocity
        nodes.forEach { it.vx = 0f; it.vy = 0f }
    }
    
    /**
     * A single physics step. Can be called live for the "settle" button
     * or in a batch loop for pre-settling.
     */
    private var liveStep = 0
    
    fun startLiveSimulation() { liveStep = 0 }
    
    fun applyLiveStep(
        nodes: List<GraphNode>,
        edges: List<GraphEdge>,
        width: Float,
        height: Float
    ) {
        liveStep++
        applyLayoutStep(nodes, edges, width, height, liveStep, 200)
    }
    
    private fun applyLayoutStep(
        nodes: List<GraphNode>,
        edges: List<GraphEdge>,
        width: Float,
        height: Float,
        step: Int,
        totalSteps: Int
    ) {
        val n = nodes.size
        if (n < 2) return
        
        val centerX = width / 2f
        val centerY = height / 2f
        val maxRadius = min(width, height) * 0.32f
        
        // Desired node spacing, capped for small graphs
        val idealSpacing = min(120f, maxRadius * 0.7f / sqrt(n.toFloat()))
        val spacingSq = idealSpacing * idealSpacing
        
        // Cooling: start hot, end cold
        val progress = step.toFloat() / totalSteps.toFloat()
        val temperature = maxOf(0.3f, 30f * (1f - progress * progress))
        
        // --- Repulsion: push apart when within 3× ideal spacing ---
        val repulsionCutoff = idealSpacing * 3f
        for (i in nodes.indices) {
            for (j in i + 1 until n) {
                val a = nodes[i]
                val b = nodes[j]
                val dx = a.x - b.x
                val dy = a.y - b.y
                val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                
                if (dist < repulsionCutoff) {
                    val force = min(spacingSq / dist, temperature) * 0.5f
                    val ux = dx / dist
                    val uy = dy / dist
                    a.vx += ux * force
                    a.vy += uy * force
                    b.vx -= ux * force
                    b.vy -= uy * force
                }
            }
        }
        
        // --- Edge springs: pull connected nodes to ~idealSpacing apart ---
        for (edge in edges) {
            val a = edge.source
            val b = edge.target
            val dx = a.x - b.x
            val dy = a.y - b.y
            val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            
            // Spring: pulls when far, pushes when too close
            val displacement = dist - idealSpacing * 0.7f
            val force = displacement * 0.04f * edge.weight
            val cappedForce = force.coerceIn(-temperature, temperature)
            val ux = dx / dist
            val uy = dy / dist
            
            a.vx -= ux * cappedForce
            a.vy -= uy * cappedForce
            b.vx += ux * cappedForce
            b.vy += uy * cappedForce
        }
        
        // --- Gentle center gravity ---
        for (node in nodes) {
            val dx = centerX - node.x
            val dy = centerY - node.y
            node.vx += dx * 0.03f
            node.vy += dy * 0.03f
        }
        
        // --- Apply velocities with damping ---
        val damping = 0.5f
        for (node in nodes) {
            node.vx *= damping
            node.vy *= damping
            
            // Cap speed
            val speed = sqrt(node.vx * node.vx + node.vy * node.vy)
            if (speed > temperature) {
                node.vx = (node.vx / speed) * temperature
                node.vy = (node.vy / speed) * temperature
            }
            
            node.x += node.vx
            node.y += node.vy
            
            // Hard radial clamp
            val dxc = node.x - centerX
            val dyc = node.y - centerY
            val distc = sqrt(dxc * dxc + dyc * dyc)
            if (distc > maxRadius) {
                node.x = centerX + (dxc / distc) * maxRadius
                node.y = centerY + (dyc / distc) * maxRadius
                node.vx *= 0.1f
                node.vy *= 0.1f
            }
        }
    }

    private fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        var dot = 0f
        var norm1 = 0f
        var norm2 = 0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
            norm1 += v1[i] * v1[i]
            norm2 += v2[i] * v2[i]
        }
        if (norm1 == 0f || norm2 == 0f) return 0f
        return dot / (sqrt(norm1.toDouble()) * sqrt(norm2.toDouble())).toFloat()
    }
}
