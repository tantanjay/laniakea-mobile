package com.laniakea.ui.components.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BuildCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laniakea.viewmodel.LaniakeaViewModel
import com.laniakea.viewmodel.MoodOption

@Composable
fun SelectionSection(
    title: String,
    options: List<SelectOption>,
    selectedName: String?,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            options.forEach { option ->
                SelectableEmoji(
                    option = option,
                    isSelected = selectedName == option.name,
                    onSelect = { onSelect(option.name) }
                )
            }
        }
    }
}

data class SelectOption(val name: String, val emoji: String, val color: Color)
fun MoodOption.toSelectOption() = SelectOption(name, emoji, color)

@Composable
fun SelectableEmoji(
    option: SelectOption,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onSelect() }
            .background(
                if (isSelected) option.color.copy(alpha = 0.15f)
                else Color.Transparent
            )
            .border(
                width = 1.dp,
                color = if (isSelected) option.color.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(8.dp)
    ) {
        Text(
            text = option.emoji,
            fontSize = 24.sp,
            modifier = Modifier.graphicsLayer(
                scaleX = if (isSelected) 1.2f else 1.0f,
                scaleY = if (isSelected) 1.2f else 1.0f,
                alpha = if (isSelected) 1f else 0.7f
            )
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = option.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) option.color else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EngineStatusCard(vm: LaniakeaViewModel) {
    val statusColor by animateColorAsState(
        targetValue = when {
            vm.isEngineLoading -> MaterialTheme.colorScheme.secondary
            vm.isEngineActive -> Color(0xFF00E676)
            else -> MaterialTheme.colorScheme.error
        }, label = "statusColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                        .shadow(4.dp, CircleShape)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Laniakea Core",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (vm.isEngineActive) "Active & Processing" else "Core Offline",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (vm.isEngineLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            } else if (!vm.isEngineActive) {
                FilledTonalButton(
                    onClick = { vm.initializeEngine() },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.BuildCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Initialize")
                }
            }
        }
    }
}

@Composable
fun AnalysisStatusCard(vm: LaniakeaViewModel) {
    AnimatedVisibility(visible = true) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Journal Analysis",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (vm.unprocessedCount == 0 && !vm.isProcessing) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00E676))
                    }
                }

                if (vm.unprocessedCount > 0 || vm.isProcessing) {
                    val progress = if (vm.totalEntries > 0) {
                        (vm.totalEntries - vm.unprocessedCount).toFloat() / vm.totalEntries.toFloat()
                    } else 1f

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${vm.unprocessedCount} fragments pending sync",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = { vm.processMissingEntries() },
                            enabled = vm.isEngineActive && vm.isThemesInitialized && vm.isAxesInitialized && !vm.isProcessing,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            if (vm.isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else if (vm.isEngineLoading) {
                                Text("Waking AI...", fontSize = 12.sp)
                            } else if (!vm.isEngineActive) {
                                Text("Core Offline", fontSize = 12.sp)
                            } else if (!vm.isThemesInitialized) {
                                Text("Loading Themes...", fontSize = 12.sp)
                            } else if (!vm.isAxesInitialized) {
                                Text("Loading Axis...", fontSize = 12.sp)
                            } else {
                                Text("Process", fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    Text(
                        "All thought fragments have been processed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}