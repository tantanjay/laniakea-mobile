package com.laniakea.engine

data class CognitiveMetrics(
    val syntacticPacing: Float,
    val agencyScore: Float,
    val epistemicModality: Float,
    val processingMarkers: Int,
    val temporalHorizon: Float
)

class CognitiveTracker(private val embedder: SentenceEmbedder) {
    
    // Lazy initialized anchors for Concrete vs Abstract
    private var concreteAnchor: FloatArray? = null
    private var abstractAnchor: FloatArray? = null

    suspend fun analyze(text: String, vector: FloatArray? = null): CognitiveMetrics {
        val lowerText = text.lowercase()
        val words = lowerText.split(Regex("[^a-z]+")).filter { it.isNotBlank() }
        val sentences = text.split(Regex("[.!?]+")).filter { it.isNotBlank() }
        val sentenceCount = sentences.size.coerceAtLeast(1)
        
        // 1. Syntactic Pacing: Ratio of conjunctions to sentences (complexity)
        val conjunctions = setOf("and", "but", "or", "so", "because", "although", "while", "unless", "since", "if")
        val conjunctionCount = words.count { it in conjunctions }
        val syntacticPacing = conjunctionCount.toFloat() / sentenceCount.toFloat()
        
        // 2. Agency Tracking: Active "I" statements vs Passive voice heuristics
        var passiveCount = 0
        var activeI = 0
        val toBe = setOf("am", "is", "are", "was", "were", "be", "been", "being")
        for (i in 0 until words.size - 1) {
            if (words[i] in toBe && words[i+1].endsWith("ed")) {
                passiveCount++
            }
            if (words[i] == "i" && words[i+1] !in toBe) {
                activeI++
            }
        }
        val agencyScore = (activeI - passiveCount).toFloat() / (activeI + passiveCount + 1).toFloat()
        
        // 3. Epistemic Modality: Absolutes vs Hedging
        val absolutes = setOf("always", "never", "definitely", "absolutely", "certainly", "must", "impossible", "clearly", "obviously")
        val hedges = setOf("maybe", "perhaps", "might", "could", "seems", "probably", "possibly", "guess", "assume", "think")
        val absoluteCount = words.count { it in absolutes }
        val hedgeCount = words.count { it in hedges }
        val epistemicModality = (absoluteCount - hedgeCount).toFloat() / (absoluteCount + hedgeCount + 1).toFloat()
        
        // 4. Processing Markers: Words indicating cognitive processing or causal reasoning
        val processingWords = setOf("because", "therefore", "realize", "realized", "understand", "understood", "sense", "meaning", "figure", "learn", "learned")
        val processingCount = words.count { it in processingWords }
        
        // 5. Temporal Horizon: Semantic projection of Concrete vs Abstract
        var temporalHorizon = 0f
        if (vector != null) {
            if (concreteAnchor == null) {
                concreteAnchor = embedder.embedRaw("I woke up, drank coffee, walked the dog, and sent some emails.")
                abstractAnchor = embedder.embedRaw("The nature of existence and meaning is complicated and deeply philosophical.")
            }
            
            val conc = concreteAnchor
            val abs = abstractAnchor
            
            if (conc != null && abs != null && conc.size == vector.size && abs.size == vector.size) {
                // Project vector onto the axis from concrete -> abstract
                // Vector subtraction: axis = abs - conc
                val axis = FloatArray(vector.size)
                var axisMagSq = 0f
                for (i in axis.indices) {
                    axis[i] = abs[i] - conc[i]
                    axisMagSq += axis[i] * axis[i]
                }
                
                if (axisMagSq > 0f) {
                    // v_shifted = vector - conc
                    // projection = dot(v_shifted, axis) / axisMagSq
                    var dot = 0f
                    for (i in axis.indices) {
                        dot += (vector[i] - conc[i]) * axis[i]
                    }
                    temporalHorizon = dot / axisMagSq
                }
            }
        }
        
        return CognitiveMetrics(
            syntacticPacing = syntacticPacing,
            agencyScore = agencyScore,
            epistemicModality = epistemicModality,
            processingMarkers = processingCount,
            temporalHorizon = temporalHorizon
        )
    }
}
