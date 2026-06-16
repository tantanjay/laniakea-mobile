package com.laniakea.ui.components.profile

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun BulletPoint(text: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text("• ", fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
