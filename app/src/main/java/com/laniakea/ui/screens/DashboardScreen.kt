package com.laniakea.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.laniakea.viewmodel.LaniakeaViewModel
import com.laniakea.ui.components.MomentumGauge
import com.laniakea.ui.components.InsightBox

@Composable
fun DashboardScreen(padding: PaddingValues, vm: LaniakeaViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding) // Respects System Bars
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            "Dashboard",
            style = MaterialTheme.typography.headlineMedium
        )

        // 1. Momentum Visualization
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Sentiment Trajectory", style = MaterialTheme.typography.titleMedium)
                MomentumGauge(
                    manualScore = vm.manualMomentum.first,
                    manualStatus = vm.manualMomentum.second,
                    aiScore = vm.aiMomentum.first,
                    aiStatus = vm.aiMomentum.second
                )
            }
        }

        // 2. Insight Section
        InsightBox(
            manualScore = vm.manualMomentum.first,
            manualStatus = vm.manualMomentum.second,
            aiScore = vm.aiMomentum.first,
            aiStatus = vm.aiMomentum.second
        )

        // 3. Technical Stats (Example of lean dashboard content)
        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("Database Status") },
                supportingContent = { Text("System ready for new entries") },
                trailingContent = { Text("OK", color = MaterialTheme.colorScheme.primary) }
            )
        }
    }
}