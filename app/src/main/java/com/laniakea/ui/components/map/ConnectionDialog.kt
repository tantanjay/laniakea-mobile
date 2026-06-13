package com.laniakea.ui.components.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.laniakea.engine.GraphEdge
import com.laniakea.engine.GraphNode
import com.laniakea.manager.SecurityManager
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun MapConnectionsDialog(
    targetNode: GraphNode,
    visibleEdges: List<GraphEdge>,
    securityManager: SecurityManager,
    onDismiss: () -> Unit
) {
    val connectedEdges = visibleEdges.filter {
        it.source.entryId == targetNode.entryId || it.target.entryId == targetNode.entryId
    }.sortedByDescending { it.weight }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Connected Thoughts",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(connectedEdges) { edge ->
                    val relatedNode = if (edge.source.entryId == targetNode.entryId) edge.target else edge.source
                    val decryptedNodeContent = try {
                        securityManager.decrypt(relatedNode.content)
                    } catch (_: Exception) {
                        relatedNode.content
                    }
                    
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    if (relatedNode.theme != "Unknown") {
                                        Text(
                                            relatedNode.theme,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = getMoodNodeColor(relatedNode.moodScore),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    val listDateString = SimpleDateFormat("MMM dd, yyyy", LocalLocale.current.platformLocale).format(Date(relatedNode.date))
                                    Text(
                                        listDateString,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                                Text(
                                    "${(edge.weight * 100).toInt()}% match",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                decryptedNodeContent.take(150) + if (decryptedNodeContent.length > 150) "..." else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF64FFDA))
            }
        },
        containerColor = Color(0xFF1E1E2E),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}
