package com.laniakea.viewmodel

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
    var vaultProgressCurrent by mutableIntStateOf(0)
    var vaultProgressTotal by mutableIntStateOf(0)

    init {
        vaultManager.onProgress = { current, total ->
            vaultProgressCurrent = current
            vaultProgressTotal = total
            vaultProgress = if (total > 0) current.toFloat() / total.toFloat() else 0f
        }
        vaultManager.onStateChange = { backingUp, restoring, importing ->
            isVaultBackingUp = backingUp
            isVaultRestoring = restoring
            isXlsxImporting = importing
        }
    }


    fun exportDataStream(uri: Uri, pass: String, onComplete: (Boolean) -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            
            try {
                vaultManager.exportDataStream(uri, pass) { success ->
                    coroutineScope.launch(Dispatchers.Main) { 
                        onComplete(success) 
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }

    fun importDataStream(uri: Uri, pass: String, onComplete: (Boolean) -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            
            try {
                vaultManager.importDataStream(uri, pass) { success ->
                    coroutineScope.launch(Dispatchers.Main) { 
                        onComplete(success) 
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }

    fun importXlsxStream(uri: Uri, onComplete: (Boolean) -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            
            try {
                vaultManager.importXlsxStream(uri) { success ->
                    coroutineScope.launch(Dispatchers.Main) { 
                        onComplete(success) 
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }
}
