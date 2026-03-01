package com.laniakea.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.laniakea.viewmodel.LaniakeaViewModel

@Composable
fun ProfileScreen(padding: PaddingValues, vm: LaniakeaViewModel) {
    val context = LocalContext.current
    
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showConfirmOverwriteDialog by remember { mutableStateOf(false) }
    var showCompletionDialog by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    
    var password by remember { mutableStateOf("") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            vm.exportDataStream(uri, password) { success ->
                showCompletionDialog = if (success) {
                    true to "Your memory vault has been securely exported and saved."
                } else {
                    false to "The export process failed. Please ensure you have enough storage space."
                }
                password = ""
            }
        } else {
            password = ""
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            selectedUri = it
            if (vm.totalEntries > 0) {
                showConfirmOverwriteDialog = true
            } else {
                showImportDialog = true
            }
        }
    }

    // Wrap the entire screen in a Box to allow overlays to be drawn on top
    Box(modifier = Modifier.fillMaxSize()) {
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
                        headlineContent = { Text("Vault Export / Import", fontWeight = FontWeight.SemiBold) },
                        leadingContent = { Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showExportDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Export")
                        }
                        OutlinedButton(
                            onClick = { openDocumentLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Import")
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

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

        // Vault Restoration Overlay
        if (vm.isVaultRestoring) {
            Dialog(
                onDismissRequest = { },
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Restoring Memory Vault", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Reconstructing your cognitive landscape...", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(24.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "${vm.vaultProgress.first} fragments restored",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Vault Backup Overlay
        if (vm.isVaultBackingUp) {
            Dialog(
                onDismissRequest = { },
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Securing Vault", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Encrypting memory fragments for export...", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(24.dp))
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        if (vm.vaultProgress.second > 0) {
                            Text(
                                "${vm.vaultProgress.first} / ${vm.vaultProgress.second}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                "Processing ${vm.vaultProgress.first} memories...",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    // Completion Dialog
    if (showCompletionDialog != null) {
        AlertDialog(
            onDismissRequest = { },
            icon = { 
                Icon(
                    if (showCompletionDialog!!.first) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (showCompletionDialog!!.first) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                )
            },
            title = { Text(if (showCompletionDialog!!.first) "Success" else "Failed") },
            text = { Text(showCompletionDialog!!.second) },
            confirmButton = {
                Button(onClick = { }) {
                    Text("OK")
                }
            }
        )
    }

    // Overwrite Confirmation Dialog
    if (showConfirmOverwriteDialog) {
        AlertDialog(
            onDismissRequest = { 
                selectedUri = null
            },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Overwrite Existing Vault?") },
            text = {
                Text(
                    "This will completely erase your current memories and settings, replacing them with the data from the backup file. This action cannot be undone.",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showImportDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Yes, Replace Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    selectedUri = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Export Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { 
                password = ""
            },
            title = { Text("Secure Export") },
            text = {
                Column {
                    Text("Enter a password to encrypt your vault file. You will need this password to import it later.")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Vault Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (password.length >= 4) {
                            createDocumentLauncher.launch("laniakea_backup.bin")
                        } else {
                            Toast.makeText(context, "Password too short", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Choose Location")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    password = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Import Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { 
                password = ""
            },
            title = { Text("Secure Import") },
            text = {
                Column {
                    Text("Enter the password for the selected vault file.")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Vault Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedUri?.let { uri ->
                            vm.importDataStream(uri, password) { success ->
                                showCompletionDialog = if (success) {
                                    true to "Your memory vault has been successfully restored."
                                } else {
                                    false to "Import failed. Please check your password and file."
                                }
                                password = ""
                                selectedUri = null
                            }
                        }
                    }
                ) {
                    Text("Start Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    password = ""
                }) {
                    Text("Cancel")
                }
            }
        )
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
