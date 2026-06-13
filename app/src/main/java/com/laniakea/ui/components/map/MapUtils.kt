package com.laniakea.ui.components.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

val STOP_WORDS = setOf(
    "i", "me", "my", "myself", "we", "our", "ours", "ourselves", "you", "your", "yours", "yourself", "yourselves",
    "he", "him", "his", "himself", "she", "her", "hers", "herself", "it", "its", "itself", "they", "them", "their",
    "theirs", "themselves", "what", "which", "who", "whom", "this", "that", "these", "those", "am", "is", "are",
    "was", "were", "be", "been", "being", "have", "has", "had", "having", "do", "does", "did", "doing", "a", "an",
    "the", "and", "but", "if", "or", "because", "as", "until", "while", "of", "at", "by", "for", "with", "about",
    "against", "between", "into", "through", "during", "before", "after", "above", "below", "to", "from", "up",
    "down", "in", "out", "on", "off", "over", "under", "again", "further", "then", "once", "here", "there", "when",
    "where", "why", "how", "all", "any", "both", "each", "few", "more", "most", "other", "some", "such", "no", "nor",
    "not", "only", "own", "same", "so", "than", "too", "very", "s", "t", "can", "will", "just", "don", "should", "now",
    "really", "feel", "felt", "feeling", "today", "yesterday", "tomorrow", "day", "time", "thing", "things", "much",
    "got", "went", "like", "know", "think", "thought", "see", "make", "made", "good", "bad", "well", "get", "getting",
    "im", "ive", "ill", "id", "dont", "cant", "didnt", "doesnt", "would", "could", "didn", "doesn", "isn", "aren",
    "wasn", "weren", "hasn", "haven", "hadn", "won", "wouldn", "couldn", "shouldn", "might", "must", "may"
)

fun getCommunityColor(clusterName: String): Color {
    if (clusterName == "Unknown" || clusterName.isBlank()) return Color.Gray
    // Stable hash ensures the same theme always gets the exact same color
    val hash = kotlin.math.abs(clusterName.hashCode())
    val hue = (hash * 137.508f) % 360f
    // 85% saturation for vibrancy, 100% value for brightness
    val hsvColor = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.85f, 1.0f))
    return Color(hsvColor)
}

fun getMoodNodeColor(moodScore: Double): Color {
    return when {
        moodScore >= 0.5 -> Color(0xFF64FFDA)
        moodScore >= -0.2 -> Color(0xFF82B1FF)
        moodScore >= -0.6 -> Color(0xFFFFAB40)
        else -> Color(0xFFFF5252)
    }
}

data class ProjectedPoint(val x: Float, val y: Float, val z: Float, val scale: Float)

fun projectPoint(nodeX: Float, nodeY: Float, nodeZ: Float, canvasCenter: Offset, yaw: Float, pitch: Float, roll: Float, cameraX: Float, cameraY: Float, cameraZ: Float): ProjectedPoint {
    val relX = nodeX - canvasCenter.x - cameraX
    val relY = nodeY - canvasCenter.y - cameraY

    // 1. Roll (rotate around Z axis)
    val cosRoll = kotlin.math.cos(roll.toDouble()).toFloat()
    val sinRoll = kotlin.math.sin(roll.toDouble()).toFloat()
    val xr = relX * cosRoll - relY * sinRoll
    val yr = relX * sinRoll + relY * cosRoll

    // 2. Yaw (rotate around Y axis)
    val cosYaw = kotlin.math.cos(yaw.toDouble()).toFloat()
    val sinYaw = kotlin.math.sin(yaw.toDouble()).toFloat()
    val x1 = xr * cosYaw - nodeZ * sinYaw
    val z1 = xr * sinYaw + nodeZ * cosYaw
    
    // 3. Pitch (rotate around X axis)
    val cosPitch = kotlin.math.cos(pitch.toDouble()).toFloat()
    val sinPitch = kotlin.math.sin(pitch.toDouble()).toFloat()
    val y2 = yr * cosPitch - z1 * sinPitch
    val z2 = yr * sinPitch + z1 * cosPitch

    val focalLength = 800f
    val depth = (z2 + cameraZ).coerceAtLeast(10f)
    val scale = focalLength / depth
    
    return ProjectedPoint(
        x = canvasCenter.x + x1 * scale,
        y = canvasCenter.y + y2 * scale,
        z = z2,
        scale = scale
    )
}

fun extractTopicFromText(text: String): String {
    if (text.isBlank()) return "Unknown Thought"
    
    val words = text.lowercase()
        .split(Regex("[^a-z]+"))
        .filter { it.length > 2 && !STOP_WORDS.contains(it) }
    
    if (words.isEmpty()) {
        val fallbackWords = text.split(Regex("\\W+")).filter { it.length > 2 }
        val fallbackWord = fallbackWords.maxByOrNull { it.length }?.replaceFirstChar { it.uppercase() }
        return if (fallbackWord != null) "$fallbackWord Thought" else "Unknown Thought"
    }
    
    val frequencies = words.groupingBy { it }.eachCount()
    
    val bestWord = frequencies.maxWithOrNull(Comparator { a, b ->
        val freqDiff = a.value.compareTo(b.value)
        if (freqDiff != 0) freqDiff else a.key.length.compareTo(b.key.length)
    })?.key
    
    val topic = bestWord?.replaceFirstChar { it.uppercase() }
    return if (topic != null) "$topic Thought" else "Unknown Thought"
}
