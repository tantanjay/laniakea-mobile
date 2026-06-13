package com.laniakea.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

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
