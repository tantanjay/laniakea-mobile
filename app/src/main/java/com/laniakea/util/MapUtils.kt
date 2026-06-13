package com.laniakea.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

fun getCommunityColor(clusterName: String, themeDistances: Map<String, Float> = emptyMap()): Color {
    if (themeDistances.isNotEmpty()) {
        val topThemes = themeDistances.entries
            .map { it.key to kotlin.math.max(0f, 1.0f - it.value) }
            .sortedByDescending { it.second }
            .take(2)
        
        if (topThemes.size == 2) {
            val totalWeight = topThemes[0].second + topThemes[1].second
            if (totalWeight > 0f) {
                val ratio1 = topThemes[0].second / totalWeight
                val ratio2 = topThemes[1].second / totalWeight
                
                val c1 = getBaseThemeColor(topThemes[0].first)
                val c2 = getBaseThemeColor(topThemes[1].first)
                
                return Color(
                    red = c1.red * ratio1 + c2.red * ratio2,
                    green = c1.green * ratio1 + c2.green * ratio2,
                    blue = c1.blue * ratio1 + c2.blue * ratio2,
                    alpha = 1f
                )
            }
        } else if (topThemes.size == 1) {
            return getBaseThemeColor(topThemes[0].first)
        }
    }
    return getBaseThemeColor(clusterName)
}

private fun getBaseThemeColor(themeName: String): Color {
    if (themeName == "Unknown" || themeName.isBlank()) return Color.Gray
    // Stable hash ensures the same theme always gets the exact same color
    val hash = kotlin.math.abs(themeName.hashCode())
    val hue = (hash * 137.508f) % 360f
    // 85% saturation for vibrancy, 100% value for brightness
    val hsvColor = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.85f, 1.0f))
    return Color(hsvColor)
}

fun getMoodNodeColor(moodScore: Double): Color {
    val score = moodScore.coerceIn(-1.0, 1.0).toFloat()
    
    // Define color stops
    val veryNegative = Color(0xFFFF5252) // Red (-1.0)
    val negative = Color(0xFFFFAB40)     // Orange (-0.3)
    val neutral = Color(0xFF82B1FF)      // Blue (0.0)
    val positive = Color(0xFF64FFDA)     // Cyan (0.5)
    val veryPositive = Color(0xFF69F0AE) // Green (1.0)
    
    return when {
        score < -0.3f -> lerpColor(veryNegative, negative, (score + 1.0f) / 0.7f)
        score < 0.0f -> lerpColor(negative, neutral, (score + 0.3f) / 0.3f)
        score < 0.5f -> lerpColor(neutral, positive, score / 0.5f)
        else -> lerpColor(positive, veryPositive, (score - 0.5f) / 0.5f)
    }
}

private fun lerpColor(c1: Color, c2: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = c1.red + (c2.red - c1.red) * f,
        green = c1.green + (c2.green - c1.green) * f,
        blue = c1.blue + (c2.blue - c1.blue) * f,
        alpha = 1f
    )
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
