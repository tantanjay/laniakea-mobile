package com.laniakea.ui.components.journal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.laniakea.data.DiaryEntry
import com.laniakea.data.getMoodColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.*

@Composable
fun JournalEntryCard(entry: DiaryEntry) {
    val dateTime = Instant.ofEpochMilli(entry.dateTime)
        .atZone(ZoneId.systemDefault())

    val date = dateTime.toLocalDate()
    val time = dateTime.toLocalTime()
    val moodColor = getMoodColor(entry.numericMood)

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

            /* Date + Mood Row */
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text(
                        text = "${date.dayOfMonth} ${
                            date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        }",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = moodColor.copy(alpha = 0.15f),
                    contentColor = moodColor
                ) {
                    Text(
                        text = entry.mood.ifEmpty { "Neutral" },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            /* Time + Content */
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Text(
                        text = String.format(Locale.getDefault(), "%02d:%02d", time.hour, time.minute),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = entry.content,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2
                )
            }

            if (entry.activities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
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
    }
}
