package com.laniakea.ui.components.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laniakea.engine.LayoutMode
import com.laniakea.ui.screens.ColorMode

@Composable
fun MapControls(
    layoutMode: LayoutMode,
    onLayoutModeChange: (LayoutMode) -> Unit,
    colorMode: ColorMode,
    onColorModeChange: (ColorMode) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Layout Mode Dropdown
        var layoutDropdownExpanded by remember { mutableStateOf(false) }
        Box {
            TextButton(
                onClick = { layoutDropdownExpanded = true },
                colors = ButtonDefaults.textButtonColors(containerColor = Color.White.copy(alpha=0.1f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when (layoutMode) {
                            LayoutMode.CLUSTERS -> "Clusters"
                            LayoutMode.GALAXY -> "Galaxy"
                            LayoutMode.TIME_WARP -> "Time Warp"
                        }, 
                        color = Color.White, fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select layout", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
            DropdownMenu(
                expanded = layoutDropdownExpanded,
                onDismissRequest = { layoutDropdownExpanded = false },
                containerColor = Color(0xFF1E1E2C)
            ) {
                DropdownMenuItem(
                    text = { Text("Clusters", color = Color.White) },
                    onClick = { onLayoutModeChange(LayoutMode.CLUSTERS); layoutDropdownExpanded = false }
                )
                DropdownMenuItem(
                    text = { Text("Galaxy", color = Color.White) },
                    onClick = { onLayoutModeChange(LayoutMode.GALAXY); layoutDropdownExpanded = false }
                )
                DropdownMenuItem(
                    text = { Text("Time Warp", color = Color.White) },
                    onClick = { onLayoutModeChange(LayoutMode.TIME_WARP); layoutDropdownExpanded = false }
                )
            }
        }
        
        // Color Mode Toggle (Mood | Themes)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                .padding(4.dp)
        ) {
            TextButton(
                onClick = { onColorModeChange(ColorMode.MOOD) },
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (colorMode == ColorMode.MOOD) Color.White.copy(alpha=0.2f) else Color.Transparent
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Mood", color = Color.White, fontSize = 12.sp)
            }
            TextButton(
                onClick = { onColorModeChange(ColorMode.COMMUNITY) },
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (colorMode == ColorMode.COMMUNITY) Color.White.copy(alpha=0.2f) else Color.Transparent
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Themes", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}
