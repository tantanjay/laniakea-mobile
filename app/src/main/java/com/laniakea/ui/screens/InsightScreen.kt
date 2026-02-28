package com.laniakea.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laniakea.viewmodel.LaniakeaViewModel
import com.laniakea.ui.components.MomentumGauge
import com.laniakea.ui.components.InsightBox

@Composable
fun InsightScreen(padding: PaddingValues, vm: LaniakeaViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Daily Insights",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )
        )

        // Sentiment Trajectory Section
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Momentum Analysis",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                tonalElevation = 2.dp,
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    MomentumGauge(
                        manualScore = vm.manualMomentum.first,
                        manualStatus = vm.manualMomentum.second,
                        aiScore = vm.aiMomentum.first,
                        aiStatus = vm.aiMomentum.second
                    )
                }
            }
        }

        // Deep Dive Insight
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "The Narrative Gap",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            InsightBox(
                manualScore = vm.manualMomentum.first,
                manualStatus = vm.manualMomentum.second,
                aiScore = vm.aiMomentum.first,
                aiStatus = vm.aiMomentum.second
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
