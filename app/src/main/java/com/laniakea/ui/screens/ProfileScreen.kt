package com.laniakea.ui.screens

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
import androidx.compose.animation.AnimatedVisibility
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
import android.app.TimePickerDialog
import com.laniakea.BuildConfig
import com.laniakea.R
import com.laniakea.ui.components.profile.ThemeOption
import com.laniakea.ui.components.profile.BulletPoint
import com.laniakea.ui.components.profile.CompletionDialog
import com.laniakea.ui.components.profile.ConfirmOverwriteDialog
import com.laniakea.ui.components.profile.ExportDialog
import com.laniakea.ui.components.profile.ImportDialog
import com.laniakea.ui.components.profile.XlsxValidationDialog
import com.laniakea.ui.components.profile.EditProfileDialog
import com.laniakea.viewmodel.ProfileState
import com.laniakea.viewmodel.LaniakeaViewModel

@Composable
fun ProfileScreen(padding: PaddingValues, vm: LaniakeaViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    val state = remember {
        ProfileState(
            vaultManager = vm.vaultManager,
            coroutineScope = coroutineScope
        )
    }

    val isVaultRestoring = state.isVaultRestoring
    val isVaultBackingUp = state.isVaultBackingUp
    val isXlsxImporting = state.isXlsxImporting
    val vaultProgress = state.vaultProgress
    val vaultProgressCurrent = state.vaultProgressCurrent
    val vaultProgressTotal = state.vaultProgressTotal

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            state.exportDataStream(uri, state.password) { success ->
                state.showCompletionDialog = if (success) {
                    true to "Your memory vault has been securely exported and saved."
                } else {
                    false to "The export process failed. Please ensure you have enough storage space."
                }
                state.password = ""
            }
        } else {
            state.password = ""
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            vm.toggleNotifications(true)
        } else {
            Toast.makeText(context, "Notification permission is required for reminders", Toast.LENGTH_SHORT).show()
            vm.toggleNotifications(false)
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            state.selectedUri = it
            if (vm.totalEntries > 0) {
                state.showConfirmOverwriteDialog = true
            } else {
                state.showImportDialog = true
            }
        }
    }

    val xlsxPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            state.importXlsxStream(uri) { success ->
                if (success) {
                    vm.refreshData()
                    state.showCompletionDialog = true to "Your spreadsheet has been successfully imported."
                } else {
                    state.showCompletionDialog = false to "Import failed. Please ensure the file format matches the template."
                }
            }
        }
    }

    val dynamicAvatars = remember(context) {
        val map = mutableMapOf<String, Int>()
        try {
            val fields = R.drawable::class.java.fields
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

    val timePicker = remember {
        TimePickerDialog(
            context,
            { _, hourOfDay, _ ->
                vm.addNotificationHour(hourOfDay)
                state.showTimePickerDialog = false
            },
            12, 0, false
        ).apply {
            setOnDismissListener { state.showTimePickerDialog = false }
        }
    }

    if (state.showTimePickerDialog) {
        LaunchedEffect(Unit) {
            timePicker.show()
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
                            state.editingUserName = vm.userName
                            state.editingProfilePicture = vm.profilePicture
                            state.showEditProfileDialog = true
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
                        headlineContent = { Text("Daily Reflection Reminders") },
                        supportingContent = { Text("Get notified to log your thoughts", style = MaterialTheme.typography.labelMedium) },
                        trailingContent = {
                            Switch(
                                checked = vm.isNotificationEnabled,
                                onCheckedChange = { 
                                    if (it) {
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                            } else {
                                                vm.toggleNotifications(true)
                                            }
                                        } else {
                                            vm.toggleNotifications(true)
                                        }
                                    } else {
                                        vm.toggleNotifications(false)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    )

                    AnimatedVisibility(visible = vm.isNotificationEnabled) {
                        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                            Text("Scheduled Times", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                vm.notificationHours.forEach { hour ->
                                    InputChip(
                                        selected = false,
                                        onClick = { },
                                        label = { Text(String.format("%02d:00", hour)) },
                                        trailingIcon = {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove",
                                                modifier = Modifier.size(16.dp).clickable { vm.removeNotificationHour(hour) }
                                            )
                                        }
                                    )
                                }
                                AssistChip(
                                    onClick = { state.showTimePickerDialog = true },
                                    label = { Text("Add Time") },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                )
                            }
                        }
                    }
                    
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
                            onClick = { state.showExportDialog = true },
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
                        onClick = { state.showXlsxValidationDialog = true },
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

        // Overlay loading spinner for Vault Operations
        if (isVaultRestoring || isVaultBackingUp || isXlsxImporting) {
            val title = when {
                isVaultRestoring -> "Restoring Vault..."
                isVaultBackingUp -> "Encrypting & Exporting..."
                else -> "Importing Spreadsheet..."
            }
            AlertDialog(
                onDismissRequest = { },
                title = { Text(title) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(progress = { vaultProgress })
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("${(vaultProgress * 100).toInt()}%", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (vaultProgressTotal > 0) {
                            Text("$vaultProgressCurrent / $vaultProgressTotal items", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (vaultProgressCurrent > 0) {
                            Text("$vaultProgressCurrent items processed", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                confirmButton = { }
            )
        }
    }

    // Completion Dialog
    if (state.showCompletionDialog != null) {
        CompletionDialog(
            show = state.showCompletionDialog!!,
            onDismiss = { state.showCompletionDialog = null }
        )
    }

    // Overwrite Confirmation Dialog
    if (state.showConfirmOverwriteDialog) {
        ConfirmOverwriteDialog(
            onConfirm = {
                state.showConfirmOverwriteDialog = false
                state.showImportDialog = true
            },
            onDismiss = {
                state.selectedUri = null
                state.showConfirmOverwriteDialog = false
            }
        )
    }

    // Export Dialog
    if (state.showExportDialog) {
        ExportDialog(
            state = state,
            context = context,
            createDocumentLauncher = createDocumentLauncher
        )
    }

    // Import Dialog
    if (state.showImportDialog) {
        ImportDialog(
            state = state,
            vm = vm
        )
    }

    // XLSX Validation Dialog
    if (state.showXlsxValidationDialog) {
        XlsxValidationDialog(
            state = state,
            xlsxPickerLauncher = xlsxPickerLauncher
        )
    }

    // Edit Profile Dialog
    if (state.showEditProfileDialog) {
        EditProfileDialog(
            state = state,
            vm = vm,
            allAvatarKeys = allAvatarKeys,
            dynamicAvatars = dynamicAvatars
        )
    }
}