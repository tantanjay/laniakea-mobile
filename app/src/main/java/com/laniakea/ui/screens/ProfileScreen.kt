package com.laniakea.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.laniakea.viewmodel.LaniakeaViewModel

@Composable
fun ProfileScreen(padding: PaddingValues, vm: LaniakeaViewModel) {
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
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        // Profile Avatar with Glow
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier
                    .size(100.dp)
                    .shadow(20.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(50.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            // Decorative AI ring
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = vm.userName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = vm.tagline,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Actions Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                ListItem(
                    headlineContent = { Text("Visual Persona", fontWeight = FontWeight.SemiBold) },
                    leadingContent = { Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ThemeOption(
                        color = Color(0xFF6750A4), // Purple
                        selected = vm.theme == "PURPLE",
                        onClick = { vm.updateTheme("PURPLE") }
                    )
                    ThemeOption(
                        color = Color(0xFF386A20), // Green
                        selected = vm.theme == "GREEN",
                        onClick = { vm.updateTheme("GREEN") }
                    )
                    ThemeOption(
                        color = Color(0xFF8B5000), // Orange
                        selected = vm.theme == "ORANGE",
                        onClick = { vm.updateTheme("ORANGE") }
                    )
                    ThemeOption(
                        color = Color(0xFF0061A4), // Blue
                        selected = vm.theme == "BLUE",
                        onClick = { vm.updateTheme("BLUE") }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                ListItem(
                    headlineContent = { Text("Laniakea Core Settings", fontWeight = FontWeight.SemiBold) },
                    leadingContent = { Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                )
                
                HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                
                ListItem(
                    headlineContent = { Text("Neural Auto-Initialize") },
                    supportingContent = { Text("Starts the core engine automatically on launch", style = MaterialTheme.typography.labelMedium) },
                    trailingContent = {
                        Switch(
                            checked = vm.autoLoadEnabled,
                            onCheckedChange = { vm.toggleAutoLoad(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                )
                
                HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                ListItem(
                    headlineContent = { Text("Memory Synchronization") },
                    supportingContent = {
                        when {
                            vm.isImporting -> Text("Syncing fragments: ${vm.importProgress.first}/${vm.importProgress.second}")
                            !vm.isEngineActive -> Text("Core must be active to sync memories")
                            else -> Text("Reload thought fragments from source")
                        }
                    },
                    trailingContent = {
                        if (!vm.isEngineActive && !vm.isImporting) {
                            FilledTonalButton(
                                onClick = { vm.initializeEngine() },
                                enabled = !vm.isEngineLoading,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(if (vm.isEngineLoading) "Wait..." else "Init Core")
                            }
                        } else {
                            Button(
                                onClick = { vm.importDummyData() },
                                enabled = !vm.isImporting && vm.isEngineActive,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (vm.isImporting) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Sync")
                                }
                            }
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text(
                text = "Laniakea Engine v1.0.4",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ThemeOption(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
