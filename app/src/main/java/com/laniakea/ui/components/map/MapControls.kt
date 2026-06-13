package com.laniakea.ui.components.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.List
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
        
        val layoutIcon = when (layoutMode) {
            LayoutMode.CLUSTERS -> Icons.Default.Share
            LayoutMode.GALAXY -> Icons.Default.Star
            LayoutMode.TIME_WARP -> Icons.Default.DateRange
        }
        
        Box {
            TextButton(
                onClick = { layoutDropdownExpanded = true },
                colors = ButtonDefaults.textButtonColors(containerColor = Color.White.copy(alpha=0.1f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = layoutIcon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
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
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) },
                    text = { Text("Clusters", color = Color.White) },
                    onClick = { onLayoutModeChange(LayoutMode.CLUSTERS); layoutDropdownExpanded = false }
                )
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) },
                    text = { Text("Galaxy", color = Color.White) },
                    onClick = { onLayoutModeChange(LayoutMode.GALAXY); layoutDropdownExpanded = false }
                )
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) },
                    text = { Text("Time Warp", color = Color.White) },
                    onClick = { onLayoutModeChange(LayoutMode.TIME_WARP); layoutDropdownExpanded = false }
                )
            }
        }
        
        // Color Mode Toggle (Mood / Themes)
        val colorModeIcon = if (colorMode == ColorMode.MOOD) Icons.Default.Face else Icons.Default.List
        val colorModeText = if (colorMode == ColorMode.MOOD) "Mood" else "Themes"
        
        TextButton(
            onClick = { 
                onColorModeChange(if (colorMode == ColorMode.MOOD) ColorMode.COMMUNITY else ColorMode.MOOD) 
            },
            colors = ButtonDefaults.textButtonColors(containerColor = Color.White.copy(alpha=0.1f)),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = colorModeIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Color: $colorModeText",
                    color = Color.White, 
                    fontSize = 12.sp
                )
            }
        }
    }
}
