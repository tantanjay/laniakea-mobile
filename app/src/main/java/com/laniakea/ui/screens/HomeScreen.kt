package com.laniakea.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laniakea.viewmodel.LaniakeaViewModel
import com.laniakea.ui.theme.*

@Composable
fun HomeScreen(padding: PaddingValues, vm: LaniakeaViewModel) {
    var journalText by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf<MoodOption?>(null) }

    val moods = listOf(
        MoodOption("Terrible", "😫", -2.0, MoodTerrible),
        MoodOption("Bad", "🙁", -1.0, MoodBad),
        MoodOption("Fine", "😐", 0.0, MoodFine),
        MoodOption("Good", "🙂", 1.0, MoodGood),
        MoodOption("Awesome", "🤩", 2.0, MoodAwesome)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
            )
            .padding(padding)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        
        HeaderSection()

        // Journal Input Card with AI Vibe
        MainInputCard(
            journalText = journalText,
            onTextChange = { journalText = it },
            moods = moods,
            selectedMood = selectedMood,
            onMoodSelect = { selectedMood = it },
            onSave = {
                if (journalText.isNotBlank() && selectedMood != null) {
                    vm.addDiaryEntry(
                        content = journalText,
                        mood = selectedMood!!.name,
                        numericMood = selectedMood!!.value
                    )
                    journalText = ""
                    selectedMood = null
                }
            }
        )

        // AI Engine Status
        EngineStatusCard(vm)

        // Analysis Progress
        AnalysisStatusCard(vm)
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun HeaderSection() {
    Column {
        Text(
            "Hello, Traveller",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        )
        Text(
            "Document your journey through the Laniakea.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun MainInputCard(
    journalText: String,
    onTextChange: (String) -> Unit,
    moods: List<MoodOption>,
    selectedMood: MoodOption?,
    onMoodSelect: (MoodOption) -> Unit,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Current Thought",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedTextField(
                value = journalText,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp),
                placeholder = { Text("What's on your mind? Safe to write.", color = Color.Gray) },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            Text(
                "How does it feel?",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                moods.forEach { mood ->
                    MoodEmoji(
                        option = mood,
                        isSelected = selectedMood == mood,
                        onSelect = { onMoodSelect(mood) }
                    )
                }
            }

            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = journalText.isNotBlank() && selectedMood != null,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Transmit to Archive", fontWeight = FontWeight.Bold)
            }
        }
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${vm.unprocessedCount} fragments pending sync",
                            style = MaterialTheme.typography.labelMedium
                        )
                        
                        Button(
                            onClick = { vm.processMissingEntries() },
                            enabled = vm.isEngineActive && !vm.isProcessing,
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

data class MoodOption(val name: String, val emoji: String, val value: Double, val color: Color)

@Composable
fun MoodEmoji(
    option: MoodOption,
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
            .padding(10.dp)
    ) {
        Text(
            text = option.emoji,
            fontSize = 32.sp,
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