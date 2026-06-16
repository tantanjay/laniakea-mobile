package com.laniakea.engine

data class CognitiveMetrics(
    val syntacticPacing: Float,
    val agencyScore: Float,
    val epistemicModality: Float,
    val processingMarkers: Int,
    val temporalHorizon: Float
)

class CognitiveTracker {
    
    fun analyze(
        text: String, 
        vector: FloatArray? = null, 
        getAxisScore: ((String, FloatArray) -> Float?)? = null
    ): CognitiveMetrics {
        val lowerText = text.lowercase()
        val words = lowerText.split(Regex("[^a-z]+")).filter { it.isNotBlank() }
        val sentences = text.split(Regex("[.!?]+")).filter { it.isNotBlank() }
        val sentenceCount = sentences.size.coerceAtLeast(1)
        
        // 1. Syntactic Pacing: Ratio of conjunctions to sentences (complexity)
        val conjunctions = setOf("and", "but", "or", "so", "because", "although", "while", "unless", "since", "if")
        val conjunctionCount = words.count { it in conjunctions }
        val syntacticPacing = conjunctionCount.toFloat() / sentenceCount.toFloat()
        
        // 2. Processing Markers: Words indicating cognitive processing or causal reasoning
        val processingWords = setOf("because", "therefore", "realize", "realized", "understand", "understood", "sense", "meaning", "figure", "learn", "learned")
        val processingCount = words.count { it in processingWords }
        
        // Geometric Cognitive Tracking
        var agencyScore = 0f
        var epistemicModality = 0f
        var temporalHorizon = 0f
        
        if (vector != null && getAxisScore != null) {
            agencyScore = getAxisScore("Agency vs Helplessness", vector) ?: 0f
            epistemicModality = getAxisScore("Certainty vs Rumination", vector) ?: 0f
            temporalHorizon = getAxisScore("Concrete vs Abstract", vector) ?: 0f
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
