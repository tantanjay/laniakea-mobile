package com.laniakea.viewmodel

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.laniakea.manager.VaultManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileScreenState(
    private val vaultManager: VaultManager,
    private val coroutineScope: CoroutineScope
) {
    var showExportDialog by mutableStateOf(false)
    var showImportDialog by mutableStateOf(false)
    var showXlsxValidationDialog by mutableStateOf(false)
    var showConfirmOverwriteDialog by mutableStateOf(false)
    var showEditProfileDialog by mutableStateOf(false)
    var showCompletionDialog by mutableStateOf<Pair<Boolean, String>?>(null)

    var editingUserName by mutableStateOf("")
    var editingProfilePicture by mutableStateOf("")
    var password by mutableStateOf("")
    var selectedUri by mutableStateOf<Uri?>(null)

    var isVaultRestoring by mutableStateOf(false)
    var isVaultBackingUp by mutableStateOf(false)
    var isXlsxImporting by mutableStateOf(false)
    var vaultProgress by mutableFloatStateOf(0f)

    fun exportDataStream(uri: Uri, pass: String, onComplete: (Boolean) -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isVaultBackingUp = true
            }
            try {
                vaultManager.exportDataStream(uri, pass) { success ->
                    coroutineScope.launch(Dispatchers.Main) { 
                        isVaultBackingUp = false
                        onComplete(success) 
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isVaultBackingUp = false
                    onComplete(false)
                }
            }
        }
    }

    fun importDataStream(uri: Uri, pass: String, onComplete: (Boolean) -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isVaultRestoring = true
            }
            try {
                vaultManager.importDataStream(uri, pass) { success ->
                    coroutineScope.launch(Dispatchers.Main) { 
                        isVaultRestoring = false
                        onComplete(success) 
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isVaultRestoring = false
                    onComplete(false)
                }
            }
        }
    }

    fun importXlsxStream(uri: Uri, onComplete: (Boolean) -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isXlsxImporting = true
            }
            try {
                vaultManager.importXlsxStream(uri) { success ->
                    coroutineScope.launch(Dispatchers.Main) { 
                        isXlsxImporting = false
                        onComplete(success) 
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isXlsxImporting = false
                    onComplete(false)
                }
            }
        }
    }
}
