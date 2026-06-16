package com.laniakea.ui.components.profile

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.laniakea.viewmodel.LaniakeaViewModel
import com.laniakea.viewmodel.ProfileState

@Composable
fun CompletionDialog(
    show: Pair<Boolean, String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (show.first) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (show.first) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
            )
        },
        title = { Text(if (show.first) "Done Process" else "Failed") },
        text = { Text(show.second) },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
fun ConfirmOverwriteDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Yes, Replace Everything")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ExportDialog(
    state: ProfileState,
    context: Context,
    createDocumentLauncher: ManagedActivityResultLauncher<String, android.net.Uri?>
) {
    AlertDialog(
        onDismissRequest = { state.showExportDialog = false; state.password = "" },
        title = { Text("Secure Export") },
        text = {
            Column {
                Text("Enter a password to encrypt your vault file. You will need this password to import it later.")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = state.password,
                    onValueChange = { state.password = it },
                    label = { Text("Vault Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (state.password.length >= 4) {
                        state.showExportDialog = false
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
                state.showExportDialog = false
                state.password = ""
            }) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ImportDialog(
    state: ProfileState,
    vm: LaniakeaViewModel
) {
    AlertDialog(
        onDismissRequest = { state.showImportDialog = false; state.password = "" },
        title = { Text("Secure Import") },
        text = {
            Column {
                Text("Enter the password for the selected vault file.")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = state.password,
                    onValueChange = { state.password = it },
                    label = { Text("Vault Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    state.selectedUri?.let { uri ->
                        state.showImportDialog = false
                        state.importDataStream(uri, state.password) { success ->
                            if (success) {
                                vm.refreshData()
                                state.showCompletionDialog = true to "Your memory vault has been successfully restored."
                            } else {
                                state.showCompletionDialog = false to "Import failed. Please check your password and file."
                            }
                            state.password = ""
                            state.selectedUri = null
                        }
                    }
                }
            ) {
                Text("Start Import")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                state.showImportDialog = false
                state.password = ""
            }) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun XlsxValidationDialog(
    state: ProfileState,
    xlsxPickerLauncher: ManagedActivityResultLauncher<Array<String>, android.net.Uri?>
) {
    AlertDialog(
        onDismissRequest = { state.showXlsxValidationDialog = false },
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
                    state.showXlsxValidationDialog = false
                    xlsxPickerLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                }
            ) {
                Text("Select File")
            }
        },
        dismissButton = {
            TextButton(onClick = { state.showXlsxValidationDialog = false }) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditProfileDialog(
    state: ProfileState,
    vm: LaniakeaViewModel,
    allAvatarKeys: List<String>,
    dynamicAvatars: Map<String, Int>
) {
    AlertDialog(
        onDismissRequest = { state.showEditProfileDialog = false },
        title = { Text("Edit Profile") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = state.editingUserName,
                    onValueChange = { state.editingUserName = it },
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
                                    val isSelected = state.editingProfilePicture == key
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
                                                state.editingProfilePicture = if (state.editingProfilePicture == key) {
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
                    vm.updateProfile(state.editingUserName, state.editingProfilePicture)
                    state.showEditProfileDialog = false
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { state.showEditProfileDialog = false }
            ) {
                Text("Cancel")
            }
        }
    )
}
