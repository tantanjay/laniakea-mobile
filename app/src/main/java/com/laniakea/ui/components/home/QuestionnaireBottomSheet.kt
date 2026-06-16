package com.laniakea.ui.components.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.laniakea.util.QuestionnaireUtils
import com.laniakea.viewmodel.HomeScreenState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionnaireBottomSheet(
    state: HomeScreenState,
    isEngineActive: Boolean
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { state.showQuestionnaire = false },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                "Quick Reflection",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            QuestionnaireSection("Energy", QuestionnaireUtils.energyOptions, state.qEnergy) { state.qEnergy = it }
            QuestionnaireSection("Primary Theme", QuestionnaireUtils.themeOptions, state.qTheme) { state.qTheme = it }
            QuestionnaireSection("Mental Pace", QuestionnaireUtils.mentalPaceOptions, state.qMentalPace) { state.qMentalPace = it }
            QuestionnaireSection("Social State", QuestionnaireUtils.socialStateOptions, state.qSocialState) { state.qSocialState = it }
            QuestionnaireSection("Thinking Style", QuestionnaireUtils.thinkingStyleOptions, state.qThinkingStyle) { state.qThinkingStyle = it }
            QuestionnaireSection("Temporal Focus", QuestionnaireUtils.temporalFocusOptions, state.qTemporalFocus) { state.qTemporalFocus = it }
            QuestionnaireSection("Intensity", QuestionnaireUtils.intensityOptions, state.qIntensity) { state.qIntensity = it }

            val canSubmit = state.qEnergy != null && state.qTheme != null && state.qMentalPace != null &&
                    state.qSocialState != null && state.qThinkingStyle != null &&
                    state.qTemporalFocus != null && state.qIntensity != null

            Button(
                onClick = { state.submitQuestionnaire(isEngineActive) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = canSubmit,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Save Reflection", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestionnaireSection(
    title: String,
    options: List<String>,
    selectedOption: String?,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                val isSelected = option == selectedOption
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(option) },
                    label = { Text(option) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    }
}
