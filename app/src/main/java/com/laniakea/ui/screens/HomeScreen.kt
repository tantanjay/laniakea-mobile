package com.laniakea.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laniakea.viewmodel.LaniakeaViewModel

@Composable
fun HomeScreen(padding: PaddingValues, vm: LaniakeaViewModel) {
    var journalText by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf<MoodOption?>(null) }

    val moods = listOf(
        MoodOption("Terrible", "😫", -2.0),
        MoodOption("Bad", "🙁", -1.0),
        MoodOption("Fine", "😐", 0.0),
        MoodOption("Good", "🙂", 1.0),
        MoodOption("Awesome", "🤩", 2.0)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            "How's your day?",
            style = MaterialTheme.typography.headlineMedium
        )

        // Journal Input Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = journalText,
                    onValueChange = { journalText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp),
                    placeholder = { Text("Write your thoughts here...") },
                    shape = RoundedCornerShape(12.dp)
                )

                Text("How are you feeling?", style = MaterialTheme.typography.titleMedium)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    moods.forEach { mood ->
                        MoodEmoji(
                            option = mood,
                            isSelected = selectedMood == mood,
                            onSelect = { selectedMood = mood }
                        )
                    }
                }

                Button(
                    onClick = {
                        if (journalText.isNotBlank() && selectedMood != null) {
                            vm.addDiaryEntry(
                                content = journalText,
                                mood = selectedMood!!.name,
                                numericMood = selectedMood!!.value
                            )
                            journalText = ""
                            selectedMood = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = journalText.isNotBlank() && selectedMood != null,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save Entry")
                }
            }
        }

        // Model Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Model Status", style = MaterialTheme.typography.titleMedium)
                        val statusText = when {
                            vm.isEngineLoading -> "Loading..."
                            vm.isEngineActive -> "Active"
                            else -> "Inactive"
                        }
                        val statusColor = when {
                            vm.isEngineLoading -> MaterialTheme.colorScheme.secondary
                            vm.isEngineActive -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.error
                        }
                        Text(
                            statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = statusColor
                        )
                    }
                    
                    if (vm.isEngineLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else if (!vm.isEngineActive) {
                        Button(onClick = { vm.initializeEngine() }) {
                            Text("Enable")
                        }
                    }
                }
            }
        }

        // Entry Processing Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Entry Analysis", style = MaterialTheme.typography.titleMedium)
                
                if (vm.unprocessedCount > 0) {
                    Text(
                        "${vm.unprocessedCount} / ${vm.totalEntries} entries are not yet analyzed.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { vm.processMissingEntries() },
                            enabled = vm.isEngineActive && !vm.isProcessing,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (vm.isProcessing) {
                                Text("Analyzing...")
                            } else {
                                Text("Analyze Now")
                            }
                        }
                        
                        if (vm.isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                    
                    if (!vm.isEngineActive && !vm.isProcessing) {
                        Text(
                            "Enable the model above to start analysis.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Text(
                        "All ${vm.totalEntries} entries are fully analyzed and ready!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

data class MoodOption(val name: String, val emoji: String, val value: Double)

@Composable
fun MoodEmoji(
    option: MoodOption,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onSelect() }
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .padding(8.dp)
    ) {
        Text(
            text = option.emoji,
            fontSize = 32.sp
        )
        Text(
            text = option.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}