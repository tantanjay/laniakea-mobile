package com.laniakea.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.laniakea.viewmodel.LaniakeaViewModel

@Composable
fun ProfileScreen(padding: PaddingValues, vm: LaniakeaViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Profile Avatar Placeholder
        Surface(
            modifier = Modifier.size(100.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Laniakea User", style = MaterialTheme.typography.headlineSmall)
        Text(text = "Analyzing vibes since 2025", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)

        Spacer(modifier = Modifier.height(32.dp))

        // Actions Section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                ListItem(
                    headlineContent = { Text("Account Settings") },
                    leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    headlineContent = { Text("Import Data") },
                    supportingContent = {
                        if (vm.isImporting) {
                            Text("Importing: ${vm.importProgress.first}/${vm.importProgress.second}")
                        } else {
                            Text("Clear database and reload dummy CSV")
                        }
                    },
                    trailingContent = {
                        Button(
                            onClick = { vm.importDummyData() },
                            enabled = !vm.isImporting
                        ) {
                            Text(if (vm.isImporting) "Wait..." else "Import")
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Laniakea Engine v1.0.4",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}