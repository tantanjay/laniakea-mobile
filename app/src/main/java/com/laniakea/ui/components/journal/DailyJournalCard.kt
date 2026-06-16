package com.laniakea.ui.components.journal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.laniakea.data.DiaryEntry
import com.laniakea.data.getMoodColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import androidx.compose.ui.platform.LocalLocale
import com.laniakea.util.QuestionnaireUtils

@Composable
fun DailyJournalCard(entries: List<DiaryEntry>, onFindSimilar: ((DiaryEntry) -> Unit)? = null) {
    if (entries.isEmpty()) return

    val firstEntry = entries.first()
    val dateTime = Instant.ofEpochMilli(firstEntry.dateTime)
        .atZone(ZoneId.systemDefault())

    val date = dateTime.toLocalDate()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 0.5.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Date display, same for all entries in the list
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text(
                        text = "${date.dayOfMonth} ${
                            date.month.getDisplayName(TextStyle.SHORT, LocalLocale.current.platformLocale)
                        }",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Now, list the individual entries for that day
            entries.sortedBy { it.dateTime }.forEachIndexed { index, entry ->
                val entryDateTime = Instant.ofEpochMilli(entry.dateTime)
                    .atZone(ZoneId.systemDefault())
                val time = entryDateTime.toLocalTime()
                val moodColor = getMoodColor(entry.numericMood)

                /* Time + Content */
                Row(verticalAlignment = Alignment.Top) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text(
                            text = String.format(LocalLocale.current.platformLocale, "%02d:%02d", time.hour, time.minute),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        val displayText = if (entry.entryType == "QUESTIONNAIRE") {
                            QuestionnaireUtils.generateCompactDisplay(entry)
                        } else {
                            entry.content
                        }
                        
                        Text(
                            text = displayText,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Mood Badge or Questionnaire Indicator
                            if (entry.entryType == "QUESTIONNAIRE") {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                ) {
                                    Text(
                                        text = "⚡ Quick Reflection",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = moodColor,
                                    contentColor = Color.White
                                ) {
                                    Text(
                                        text = entry.mood.ifEmpty { "Neutral" },
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            if (entry.activities.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                ) {
                                    Text(
                                        text = entry.activities,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        if (onFindSimilar != null && entry.isVectorized && entry.entryType != "QUESTIONNAIRE") {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = { onFindSimilar.invoke(entry) },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text(
                                    "Find Similar",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (index < entries.size - 1) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
