package com.laniakea.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.laniakea.ui.components.journal.CalendarView
import com.laniakea.ui.components.journal.DailyJournalCard
import com.laniakea.viewmodel.LaniakeaViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.*

@Composable
fun JournalScreen(padding: PaddingValues, vm: LaniakeaViewModel) {
    val allEntries by vm.allEntries.collectAsState()
    val filteredEntries by vm.filteredEntries.collectAsState()
    val selectedRange by vm.selectedDateRange.collectAsState()
    val currentMonth by vm.viewingMonth.collectAsState()

    var isCalendarExpanded by remember { mutableStateOf(true) }

    val groupedEntries = remember(filteredEntries) {
        filteredEntries.groupBy {
            Instant.ofEpochMilli(it.dateTime)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }.toList().sortedByDescending { it.first }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Journal History",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            IconButton(onClick = { isCalendarExpanded = !isCalendarExpanded }) {
                Icon(
                    imageVector = if (isCalendarExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isCalendarExpanded) "Collapse Calendar" else "Expand Calendar",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        AnimatedVisibility(
            visible = isCalendarExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                CalendarView(
                    currentMonth = currentMonth,
                    onMonthChange = { vm.setViewingMonth(it) },
                    entries = allEntries,
                    selectedRange = selectedRange,
                    onRangeSelected = { start, end ->
                        vm.setSelectedDateRange(start, end)
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (selectedRange == null) {
                    "Entries in ${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())}"
                } else {
                    "Filtered Selection"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            if (selectedRange != null) {
                TextButton(
                    onClick = { vm.setSelectedDateRange(null, null) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Clear Filter")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(groupedEntries, key = { it.first.toString() }) { (_, entries) ->
                DailyJournalCard(entries)
            }
            
            if (filteredEntries.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No entries found for this selection.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
