package com.laniakea.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun InsightBox(
    manualScore: Float,
    manualStatus: String,
    aiScore: Float,
    aiStatus: String
) {
    val insight = remember(manualScore, aiScore) {
        getDivergenceInsight(manualScore, manualStatus, aiScore, aiStatus)
    }

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("💡", fontSize = 26.sp)
                }
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = insight,
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

private fun getDivergenceInsight(
    manualScore: Float,
    manualStatus: String,
    aiScore: Float,
    aiStatus: String
): String {
    return when {
        manualStatus == aiStatus -> "Your self-perception and writing style are perfectly in sync today."
        manualScore > 10 && aiScore < 5 ->
            "Insight: You're pushing for a positive outlook, but your words suggest a more cautious, stable pace."
        aiScore > manualScore + 15 ->
            "Insight: Your writing carries more optimism than you're giving yourself credit for!"
        manualScore > 0 && aiScore < -15 ->
            "Alert: There's a notable gap between your reported mood and your writing context. Consider a self-care break."
        else -> "You are maintaining a steady balance between your reported feelings and your inner context."
    }
}