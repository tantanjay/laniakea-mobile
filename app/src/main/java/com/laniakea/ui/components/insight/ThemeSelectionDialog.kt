package com.laniakea.ui.components.insight

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.laniakea.manager.SemanticManager
import com.laniakea.viewmodel.LaniakeaViewModel

@Composable
fun ThemeSelectionDialog(vm: LaniakeaViewModel, onDismiss: () -> Unit, onSave: () -> Unit) {
    val allThemes = SemanticManager.richThemes.keys.toList()
    var selectedThemes by remember { mutableStateOf(vm.selectedThemes.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize Themes") },
        text = {
            Column {
                Text("Select the themes you want to track in your insights.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(allThemes.size) { index ->
                        val theme = allThemes[index]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                val newSet = selectedThemes.toMutableSet()
                                if (newSet.contains(theme)) newSet.remove(theme) else newSet.add(theme)
                                selectedThemes = newSet
                            }.padding(vertical = 4.dp)
                        ) {
                            Checkbox(checked = selectedThemes.contains(theme), onCheckedChange = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(theme)
                        }
                    }
                }
            }
        },
        confirmButton = { 
            TextButton(onClick = { 
                vm.updateSelectedThemes(selectedThemes.toList())
                onSave()
                onDismiss() 
            }) { Text("Save") } 
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
