package com.laniakea.ui.components.map

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Composable
fun ConstellationLoader(
    isEngineActive: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF64FFDA)
) {
    val loadingTransition = rememberInfiniteTransition(label = "loading")
    val rotation by loadingTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val pulse by loadingTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
            // Orbiting stars
            for (i in 0 until 3) {
                val orbitOffset = i * 120f
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = 0.6f - (i * 0.15f)),
                    modifier = Modifier
                        .size(16.dp)
                        .offset {
                            IntOffset(
                                x = (24 * kotlin.math.cos((rotation + orbitOffset) * Math.PI / 180)).toFloat().dp.roundToPx(),
                                y = (24 * kotlin.math.sin((rotation + orbitOffset) * Math.PI / 180)).toFloat().dp.roundToPx()
                            )
                        }
                        .graphicsLayer(rotationZ = rotation * 2f)
                )
            }
            
            // Center pulsing star
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer(
                        scaleX = pulse,
                        scaleY = pulse,
                        rotationZ = -rotation
                    )
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            if (!isEngineActive) "Warming up the Vector Engine..." else "Building your constellation...",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
