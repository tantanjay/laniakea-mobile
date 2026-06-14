package com.laniakea.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.laniakea.BuildConfig
import com.laniakea.ui.components.profile.ThemeOption
import com.laniakea.ui.components.shared.BulletPoint
import com.laniakea.viewmodel.LaniakeaViewModel

@Composable
fun ProfileScreen(padding: PaddingValues, vm: LaniakeaViewModel) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showXlsxValidationDialog by remember { mutableStateOf(false) }
    var showConfirmOverwriteDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showCompletionDialog by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    
    var editingUserName by remember { mutableStateOf(vm.userName) }
    var editingProfilePicture by remember { mutableStateOf(vm.profilePicture) }
    
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

    val xlsxPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            vm.importXlsxStream(it) { success ->
                showCompletionDialog = if (success) {
                    true to "The spreadsheet data has been successfully integrated into your vault."
                } else {
                    false to "Spreadsheet import failed. Please verify the file format and data integrity."
                }
            }
        }
    }

    val dynamicAvatars = remember(context) {
        val map = mutableMapOf<String, Int>()
        try {
            val fields = com.laniakea.R.drawable::class.java.fields
            for (field in fields) {
                if (field.name.startsWith("ic_profile_")) {
                    val name = field.name.removePrefix("ic_profile_").replaceFirstChar { it.uppercase() }
                    map[name] = field.getInt(null)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        map
    }

    val allAvatarKeys = remember(dynamicAvatars) {
        val keys = dynamicAvatars.keys.toMutableSet()
        keys.remove("Person")
        keys.sorted().toList()
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
                .verticalScroll(scrollState)
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
                        val currentKey = vm.profilePicture
                        val dynamicId = dynamicAvatars[currentKey]
                        if (dynamicId != null) {
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(id = dynamicId),
                                contentDescription = null,
                                modifier = Modifier.size(50.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(50.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                
                // Decorative AI ring
                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape)
                )

                // Edit pencil icon
                Box(
                    modifier = Modifier
                        .size(116.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    IconButton(
                        onClick = { 
                            editingUserName = vm.userName
                            editingProfilePicture = vm.profilePicture
                            showEditProfileDialog = true 
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Profile",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
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
                    
                    OutlinedButton(
                        onClick = { showXlsxValidationDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import from Spreadsheet (.xlsx)")
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    ListItem(
                        headlineContent = { Text("Core Activation") },
                        supportingContent = {
                            if (!vm.isEngineActive) Text("Core must be active to sync memories")
                            else Text("Neural engine is currently operational")
                        },
                        trailingContent = {
                            if (!vm.isEngineActive) {
                                FilledTonalButton(
                                    onClick = { vm.initializeEngine() },
                                    enabled = !vm.isEngineLoading,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(if (vm.isEngineLoading) "Wait..." else "Initialize")
                                }
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Laniakea Engine v${BuildConfig.VERSION_NAME}",
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
        
        // XLSX Import Overlay
        if (vm.isXlsxImporting) {
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
                            Icons.Default.TableChart,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Processing Spreadsheet", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Validating and importing memory entries...", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(24.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Row ${vm.vaultProgress.first} of ${vm.vaultProgress.second}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    // Completion Dialog
    if (showCompletionDialog != null) {
        AlertDialog(
            onDismissRequest = { showCompletionDialog = null },
            icon = { 
                Icon(
                    if (showCompletionDialog!!.first) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (showCompletionDialog!!.first) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                )
            },
            title = { Text(if (showCompletionDialog!!.first) "Done Process" else "Failed") },
            text = { Text(showCompletionDialog!!.second) },
            confirmButton = {
                Button(onClick = { showCompletionDialog = null }) {
                    Text("OK")
                }
            }
        )
    }

    // Overwrite Confirmation Dialog
    if (showConfirmOverwriteDialog) {
        AlertDialog(
            onDismissRequest = { selectedUri = null },
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
                        showConfirmOverwriteDialog = false
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
                    showConfirmOverwriteDialog = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Export Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false; password = "" },
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
                            showExportDialog = false
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
                    showExportDialog = false
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
            onDismissRequest = { showImportDialog = false; password = "" },
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
                            showImportDialog = false
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
                    showImportDialog = false
                    password = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // XLSX Validation Dialog
    if (showXlsxValidationDialog) {
        AlertDialog(
            onDismissRequest = { showXlsxValidationDialog = false },
            title = { Text("Spreadsheet Import Guide") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Ensure your .xlsx file follows this exact column order:", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("1. Date (yyyy-MM-dd)\n2. Time (HH:mm:ss)\n3. Mood\n4. Category\n5. Weather\n6. Activity\n7. Content")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Validation Rules:", fontWeight = FontWeight.Bold)
                    BulletPoint("Rows with missing Date, Time, or Mood will be skipped.")
                    BulletPoint("Content must contain at least 5 words.")
                    BulletPoint("Invalid Date/Time formats will be ignored.")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showXlsxValidationDialog = false
                        xlsxPickerLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    }
                ) {
                    Text("Select File")
                }
            },
            dismissButton = {
                TextButton(onClick = { showXlsxValidationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Profile Dialog
    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Edit Profile") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = editingUserName,
                        onValueChange = { editingUserName = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Text("Select Profile Picture", style = MaterialTheme.typography.titleSmall)
                    
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (i in allAvatarKeys.indices step 4) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                for (j in 0 until 4) {
                                    if (i + j < allAvatarKeys.size) {
                                        val key = allAvatarKeys[i + j]
                                        val dynamicId = dynamicAvatars[key]
                                        val isSelected = editingProfilePicture == key
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                                    else MaterialTheme.colorScheme.surfaceVariant,
                                                    CircleShape
                                                )
                                                .border(
                                                    if (isSelected) 2.dp else 0.dp,
                                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                    CircleShape
                                                )
                                                .clickable {
                                                    editingProfilePicture = if (editingProfilePicture == key) {
                                                        "Person"
                                                    } else {
                                                        key
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (dynamicId != null) {
                                                Icon(
                                                    painter = androidx.compose.ui.res.painterResource(id = dynamicId),
                                                    contentDescription = null,
                                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(48.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.updateProfile(editingUserName, editingProfilePicture)
                        showEditProfileDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditProfileDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}