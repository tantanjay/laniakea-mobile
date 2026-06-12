package com.laniakea.engine

import com.laniakea.data.DiaryEntry
import com.laniakea.data.ObjectBoxSentenceVector
import kotlin.math.acos
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.exp

class GraphTestEngine {
    
    private var minDate: Long = 0L
    private var maxDate: Long = 0L
    
    fun buildGraph(
        entries: List<DiaryEntry>,
        vectors: List<ObjectBoxSentenceVector>,
        similarityThreshold: Float = 0.55f,
        width: Float,
        height: Float,
        securityManager: com.laniakea.manager.SecurityManager? = null
    ): Pair<List<GraphNode>, List<GraphEdge>> {
        val vectorMap = vectors.associateBy { it.entryId }
        val validEntries = entries.filter { vectorMap.containsKey(it.id) }
        
        val centerX = width / 2f
        val centerY = height / 2f
        val centerZ = 0f
        val initRadius = min(width, height) * 0.18f
        
        val nodes = validEntries.mapIndexed { _, entry ->
            val v = vectorMap[entry.id]!!
            
            // Distribute randomly but deterministically based on entry.id
            val rand = java.util.Random(entry.id)
            val u = rand.nextFloat()
            val costheta = rand.nextFloat() * 2f - 1f
            val theta = acos(costheta.toDouble())
            val phi = rand.nextFloat() * 2.0 * Math.PI
            
            val r = initRadius * cbrt(u.toDouble()).toFloat()
            val nx = r * sin(theta) * cos(phi)
            val ny = r * sin(theta) * sin(phi)
            val nz = r * cos(theta)
            
            GraphNode(
                entryId = entry.id,
                x = centerX + nx.toFloat(),
                y = centerY + ny.toFloat(),
                z = centerZ + nz.toFloat(),
                date = entry.dateTime,
                theme = v.semanticTheme ?: "Unknown",
                moodScore = entry.numericMood,
                content = entry.content
            )
        }
        
        val nodeMap = nodes.associateBy { it.entryId }
        val edgeCandidates = mutableListOf<GraphEdge>()
        
        for (i in validEntries.indices) {
            for (j in i + 1 until validEntries.size) {
                val e1 = validEntries[i]
                val e2 = validEntries[j]
                val v1 = vectorMap[e1.id]!!.vector
                val v2 = vectorMap[e2.id]!!.vector
                
                if (v1 != null && v2 != null) {
                    val rawSim = cosineSimilarity(v1, v2)
                    
                    // Filter by raw semantic similarity first to drop unrelated thoughts
                    if (rawSim >= similarityThreshold) {
                        val daysApart = abs(e1.dateTime - e2.dateTime).toDouble() / (24L * 60 * 60 * 1000).toDouble()
                        val temporalFactor = exp(-daysApart / 90.0).toFloat()
                        
                        // Hybrid Score: Topic + Era
                        val hybridScore = rawSim * temporalFactor
                        
                        edgeCandidates.add(GraphEdge(nodeMap[e1.id]!!, nodeMap[e2.id]!!, hybridScore))
                    }
                }
            }
        }
        
        // Limit edges to top K connections per node to keep graph sparse and physics extremely fast
        val maxEdgesPerNode = 4
        val edgesByNode = mutableMapOf<Long, MutableList<GraphEdge>>()
        for (node in nodes) { edgesByNode[node.entryId] = mutableListOf() }
        
        for (edge in edgeCandidates) {
            edgesByNode[edge.source.entryId]!!.add(edge)
            edgesByNode[edge.target.entryId]!!.add(edge)
        }
        
        val edgesSet = mutableSetOf<GraphEdge>()
        for (node in nodes) {
            val nodeEdges = edgesByNode[node.entryId]!!
            nodeEdges.sortByDescending { it.weight }
            nodeEdges.take(maxEdgesPerNode).forEach { edgesSet.add(it) }
        }
        val edges = edgesSet.toList()
        
        if (nodes.isNotEmpty()) {
            minDate = nodes.minOf { it.date }
            maxDate = nodes.maxOf { it.date }
        }
        
        // --- Calculate Importance Score ---
        val now = System.currentTimeMillis()
        var maxRawImportance = 0.001f
        for (node in nodes) {
            val daysAgo = (now - node.date) / (1000f * 60f * 60f * 24f)
            val recencyBoost = exp(-daysAgo / 90.0f) // 90-day half-life for recency
            
            val connectionCount = edgesByNode[node.entryId]?.size ?: 0
            val moodIntensity = abs(node.moodScore.toFloat())
            val lengthFactor = min(node.content.length / 500f, 1f)
            
            val rawImportance = moodIntensity + (connectionCount * 0.15f) + recencyBoost + lengthFactor
            node.importance = rawImportance
            if (rawImportance > maxRawImportance) {
                maxRawImportance = rawImportance
            }
        }
        
        // Normalize importance to 0.0 - 1.0 range
        for (node in nodes) {
            node.importance /= maxRawImportance
        }
        
        // Run Community Detection BEFORE settling layout so physics can use clusters
        detectCommunities(nodes, edges, securityManager)
        
        // Pre-settle the layout so it appears stable immediately
        settleLayout(nodes, edges, width, height)
        
        return Pair(nodes, edges)
    }
    
    /**
     * Label Propagation Algorithm (LPA) for unsupervised community detection.
     * Iteratively groups nodes based on edge weights and dynamically names clusters.
     */
    private fun detectCommunities(
        nodes: List<GraphNode>, 
        edges: List<GraphEdge>,
        securityManager: com.laniakea.manager.SecurityManager?
    ) {
        if (nodes.isEmpty()) return
        
        // 1. Initialize labels
        nodes.forEachIndexed { index, node -> node.clusterId = index }
        
        val adj = mutableMapOf<Long, MutableList<GraphEdge>>()
        nodes.forEach { adj[it.entryId] = mutableListOf() }
        edges.forEach {
            adj[it.source.entryId]!!.add(it)
            adj[it.target.entryId]!!.add(it)
        }
        
        // 2. Propagate labels
        var changed = true
        var iterations = 0
        while (changed && iterations < 15) {
            changed = false
            val shuffledNodes = nodes.shuffled()
            for (node in shuffledNodes) {
                val neighborEdges = adj[node.entryId]!!
                if (neighborEdges.isEmpty()) continue
                
                val labelWeights = mutableMapOf<Int, Float>()
                for (edge in neighborEdges) {
                    val neighbor = if (edge.source.entryId == node.entryId) edge.target else edge.source
                    labelWeights[neighbor.clusterId] = (labelWeights[neighbor.clusterId] ?: 0f) + edge.weight
                }
                
                val bestLabel = labelWeights.maxByOrNull { it.value }?.key ?: node.clusterId
                if (bestLabel != node.clusterId) {
                    node.clusterId = bestLabel
                    changed = true
                }
            }
            iterations++
        }
        
        // 3. Dynamic Cluster Naming
        val clusters = nodes.groupBy { it.clusterId }
        for ((_, clusterNodes) in clusters) {
            // Find most frequent theme, ignoring Unknown
            val validThemes = clusterNodes.map { it.theme }.filter { it != "Unknown" && it.isNotBlank() }
            val dominantTheme = validThemes.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            
            val clusterName = if (dominantTheme != null) {
                dominantTheme
            } else {
                // Fallback: extract most common word > 4 chars if no theme
                val words = clusterNodes.flatMap { node ->
                    val text = try {
                        securityManager?.decrypt(node.content) ?: node.content
                    } catch (e: Exception) {
                        node.content
                    }
                    text.lowercase().split(Regex("[^a-z]+"))
                }.filter { it.length > 4 }
                val topWord = words.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                if (topWord != null) topWord.replaceFirstChar { it.uppercase() } + " Thoughts" else "Uncharted Thoughts"
            }
            
            clusterNodes.forEach { it.clusterName = clusterName }
        }
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
        maxIterations: Int = 250
    ) {
        if (nodes.size < 2) return
        
        for (step in 0 until maxIterations) {
            applyLayoutStep(nodes, edges, width, height, step, maxIterations)
        }
        // Zero out residual velocity
        nodes.forEach { it.vx = 0f; it.vy = 0f; it.vz = 0f }
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
        val centerZ = 0f
        val maxRadius = min(width, height) * 0.32f
        
        // Desired node spacing, capped for small graphs
        val idealSpacing = min(120f, maxRadius * 0.7f / sqrt(n.toFloat()))
        val spacingSq = idealSpacing * idealSpacing
        
        // Cooling: start hot, end cold
        val progress = step.toFloat() / totalSteps.toFloat()
        val temperature = maxOf(0.3f, 30f * (1f - progress * progress))
        
        // --- Anchors ---
        val clusterCounts = mutableMapOf<Int, Int>()
        nodes.forEach { clusterCounts[it.clusterId] = (clusterCounts[it.clusterId] ?: 0) + 1 }
        
        val clustersBySize = clusterCounts.entries.sortedByDescending { it.value }
        val numClusters = clustersBySize.size.coerceAtLeast(1)
        val maxRingRadius = 1200f // Hardcoded to match the procedural stars distance
        
        val clusterAnchors = mutableMapOf<Int, Triple<Float, Float, Float>>()
        
        // --- Pairwise Physics: Repulsion & Theme Gravity ---
        val repulsionCutoffSq = (idealSpacing * 3f) * (idealSpacing * 3f)
        for (i in nodes.indices) {
            for (j in i + 1 until n) {
                val a = nodes[i]
                val b = nodes[j]
                val dx = a.x - b.x
                val dy = a.y - b.y
                val dz = a.z - b.z
                val distSq = dx * dx + dy * dy + dz * dz
                
                if (distSq < 0.01f) continue
                
                val dist = sqrt(distSq).coerceAtLeast(1f)
                val ux = dx / dist
                val uy = dy / dist
                val uz = dz / dist
                
                // 1. Repulsion: push apart when within 3x ideal spacing
                if (distSq < repulsionCutoffSq) {
                    val force = min(spacingSq / dist, temperature) * 0.5f
                    a.vx += ux * force
                    a.vy += uy * force
                    a.vz += uz * force
                    b.vx -= ux * force
                    b.vy -= uy * force
                    b.vz -= uz * force
                }
            }
        }
        
        // --- Edge springs: pull connected nodes to ~idealSpacing apart ---
        for (edge in edges) {
            val a = edge.source
            val b = edge.target
            val dx = a.x - b.x
            val dy = a.y - b.y
            val dz = a.z - b.z
            val dist = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1f)
            
            // Spring: pulls when far, pushes when too close
            val displacement = dist - idealSpacing * 0.7f
            val force = displacement * 0.04f * edge.weight
            val cappedForce = force.coerceIn(-temperature, temperature)
            val ux = dx / dist
            val uy = dy / dist
            val uz = dz / dist
            
            a.vx -= ux * cappedForce
            a.vy -= uy * cappedForce
            a.vz -= uz * cappedForce
            b.vx += ux * cappedForce
            b.vy += uy * cappedForce
            b.vz += uz * cappedForce
        }
        
        // --- Gravity & Anchoring ---
        val dateSpan = (maxDate - minDate).coerceAtLeast(1L).toFloat()
        
        val numArms = 2
        for (node in nodes) {
            // Theme determines the arm
            val armIndex = Math.abs(node.clusterId) % numArms
            
            // Importance determines distance from center
            // 1.0 (High impact) -> distProgress 0.0 (center)
            // 0.0 (Normal) -> distProgress 1.0 (tails)
            val rawDistProgress = 1f - node.importance.coerceIn(0f, 1f)
            // Square it so that even medium importance nodes are pulled closer to the center gravity well
            val distProgress = rawDistProgress * rawDistProgress * rawDistProgress
            val distFromCenter = maxRingRadius * (0.05f + 0.95f * distProgress)
            
            val baseAngle = distFromCenter * 0.004f + (armIndex * (2 * Math.PI / numArms))
            val angle = baseAngle + (distProgress * 0.2f)
            
            val ax = centerX + (cos(angle) * distFromCenter).toFloat()
            val ay = centerY + (sin(angle) * distFromCenter).toFloat()
            
            val cx = ax - centerX
            val cy = ay - centerY
            val distC = sqrt(cx * cx + cy * cy).coerceAtLeast(1f)
            
            // Tangent vector
            val tx = (-cy - cx * 0.2f) / distC
            val ty = (cx - cy * 0.2f) / distC
            
            // Deterministic spread into the arm based on node ID
            val hash = (node.entryId * 2654435761L % 1000) / 1000f
            val spread = hash - 0.5f
            val armThickness = distFromCenter * 0.25f // Tighter arm thickness
            
            val targetX = ax + tx * spread * armThickness
            val targetY = ay + ty * spread * armThickness
            
            val hashZ = (node.entryId * 12345L % 1000) / 1000f
            val targetZ = centerZ + ((hashZ * 2 - 1) * distFromCenter * 0.05f)
            
            // Stronger pull to keep them in the arm against repulsion
            node.vx += (targetX - node.x) * 0.06f
            node.vy += (targetY - node.y) * 0.06f
            node.vz += (targetZ - node.z) * 0.1f
            
            // Weak pull to absolute center to keep the galaxy cohesive
            val gdx = centerX - node.x
            val gdy = centerY - node.y
            val gdz = centerZ - node.z
            node.vx += gdx * 0.005f
            node.vy += gdy * 0.005f
            node.vz += gdz * 0.005f
        }
        
        // --- Apply velocities with damping ---
        val damping = 0.5f
        for (node in nodes) {
            node.vx *= damping
            node.vy *= damping
            node.vz *= damping
            
            // Cap speed
            val speed = sqrt(node.vx * node.vx + node.vy * node.vy + node.vz * node.vz)
            if (speed > temperature) {
                node.vx = (node.vx / speed) * temperature
                node.vy = (node.vy / speed) * temperature
                node.vz = (node.vz / speed) * temperature
            }
            
            node.x += node.vx
            node.y += node.vy
            node.z += node.vz
            
            // Soft radial clamp for Galaxy mode
            val dxc = node.x - centerX
            val dyc = node.y - centerY
            val dzc = node.z - centerZ
            val distc = sqrt(dxc * dxc + dyc * dyc + dzc * dzc)
            if (distc > maxRadius) {
                val excess = distc - maxRadius
                node.vx -= (dxc / distc) * excess * 0.15f
                node.vy -= (dyc / distc) * excess * 0.15f
                node.vz -= (dzc / distc) * excess * 0.15f
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
