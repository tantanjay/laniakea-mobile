package com.laniakea.ui.components.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.laniakea.engine.GraphNode
import com.laniakea.util.*
import androidx.compose.ui.platform.LocalLocale
import com.laniakea.data.DiaryEntry

@Composable
fun MapNodeDetailPanel(
    nodeToShow: GraphNode,
    rawEntry: DiaryEntry?,
    decryptedContent: String,
    nodeColor: Color,
    moodLabel: String,
    nodeConnections: Int,
    isIsolateMode: Boolean,
    onClose: () -> Unit,
    onViewAll: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .padding(16.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1E1E2E).copy(alpha = 0.95f),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(12.dp)) {
                        drawCircle(color = nodeColor, radius = size.minDimension / 2f)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    val displayTheme = if (nodeToShow.theme != "Unknown" && nodeToShow.theme.isNotBlank()) {
                        nodeToShow.theme
                    } else {
                        extractTopicFromText(decryptedContent)
                    }
                    
                    Text(
                        text = displayTheme,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.6f))
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(moodLabel, style = MaterialTheme.typography.bodySmall, color = nodeColor)
                    val dateString = java.text.SimpleDateFormat("MMM dd, yyyy • hh:mm a", LocalLocale.current.platformLocale).format(java.util.Date(nodeToShow.date))
                    Text(dateString, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                }
                
                Row {
                    if (nodeConnections > 0) {
                        TextButton(
                            onClick = onViewAll,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("View All", style = MaterialTheme.typography.labelSmall, color = Color(0xFF82B1FF))
                        }
                    }
                    if (!isIsolateMode) {
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = onFocus,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("Focus", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64FFDA))
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "$nodeConnections similar thoughts connected",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF64FFDA).copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.05f)
            ) {
                if (nodeToShow.entryType == "QUESTIONNAIRE" && rawEntry != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .heightIn(max = 150.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text("Energy: ${QuestionnaireUtils.mapFloatToEnergy(rawEntry.energyLevel)}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                        Text("Mental Pace: ${QuestionnaireUtils.mapFloatToMentalPace(rawEntry.mentalPace)}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                        Text("Social State: ${QuestionnaireUtils.mapFloatToSocialState(rawEntry.connectionLevel)}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                        Text("Thinking Style: ${rawEntry.thinkingStyle}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                        Text("Temporal Focus: ${rawEntry.timeFocus}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                        Text("Intensity: ${QuestionnaireUtils.mapFloatToIntensity(rawEntry.intensityLevel)}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                    }
                } else {
                    Text(
                        text = decryptedContent,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .padding(12.dp)
                            .heightIn(max = 150.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}

