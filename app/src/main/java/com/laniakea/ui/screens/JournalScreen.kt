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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
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
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
import com.laniakea.data.DiaryEntry

@Composable
fun JournalScreen(padding: PaddingValues, vm: LaniakeaViewModel) {
    val allEntries by vm.allEntries.collectAsState()
    val filteredEntries by vm.filteredEntries.collectAsState()
    val selectedRange by vm.selectedDateRange.collectAsState()
    val currentMonth by vm.viewingMonth.collectAsState()

    var isCalendarExpanded by remember { mutableStateOf(true) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<DiaryEntry>>(emptyList()) }
    
    var showSimilarDialogForEntry by remember { mutableStateOf<DiaryEntry?>(null) }
    var similarEntriesList by remember { mutableStateOf<List<DiaryEntry>>(emptyList()) }
    var isLoadingSimilar by remember { mutableStateOf(false) }
    var showSimilarInfo by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            isSearching = true
            try {
                kotlinx.coroutines.delay(500.milliseconds)
                searchResults = vm.semanticSearch(searchQuery)
            } finally {
                isSearching = false
            }
        } else {
            searchResults = emptyList()
        }
    }

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

        // Semantic Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search entries...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (searchQuery.isNotEmpty()) {
            // SHOW SEARCH RESULTS
            if (isSearching) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (searchResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No matches found.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        Text(
                            if (vm.isEngineActive) "Semantic Matches" else "Text Matches",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    items(searchResults, key = { it.id }) { entry ->
                        DailyJournalCard(listOf(entry), onFindSimilar = { e ->
                            similarEntriesList = emptyList()
                            isLoadingSimilar = true
                            showSimilarDialogForEntry = e
                        })
                    }
                }
            }
        } else {
            // SHOW STANDARD TIMELINE
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
                        },
                        onSwipeUp = { isCalendarExpanded = false }
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
                        "Entries in ${currentMonth.month.getDisplayName(TextStyle.FULL, LocalLocale.current.platformLocale)}"
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
                    DailyJournalCard(entries, onFindSimilar = { e ->
                        similarEntriesList = emptyList()
                        isLoadingSimilar = true
                        showSimilarDialogForEntry = e
                    })
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

    // ANOMALY ALERT DIALOG
    val anomalyAlert = vm.currentAnomalyAlert
    if (anomalyAlert != null) {
        val distance = anomalyAlert.second
        AlertDialog(
            onDismissRequest = { vm.dismissAnomalyAlert() },
            confirmButton = {
                TextButton(onClick = { vm.dismissAnomalyAlert() }) {
                    Text("Reflect on this")
                }
            },
            title = {
                Text(
                    text = "A Unique Day Detected",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Your latest entry differs significantly from your recent baseline (Shift: %.2f).".format(distance),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "This is a structural language anomaly. It suggests a major shift in topic, cognitive pacing, or emotional processing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    // SIMILAR ENTRIES DIALOG
    if (showSimilarDialogForEntry != null) {
        AlertDialog(
            onDismissRequest = { showSimilarDialogForEntry = null },
            confirmButton = {
                TextButton(onClick = { showSimilarDialogForEntry = null }) {
                    Text("Close")
                }
            },
            title = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Similar Entries",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showSimilarInfo = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Information",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    showSimilarDialogForEntry?.let { sourceEntry ->
                        val sourceDate = Instant.ofEpochMilli(sourceEntry.dateTime)
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                        val preview = sourceEntry.content.take(50) + if (sourceEntry.content.length > 50) "..." else ""
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${sourceDate.dayOfMonth} ${sourceDate.month.getDisplayName(TextStyle.SHORT, LocalLocale.current.platformLocale)} — $preview",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            text = {
                if (isLoadingSimilar) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (similarEntriesList.isEmpty()) {
                    Text("No similar entries found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(similarEntriesList, key = { it.id }) { similarEntry ->
                            DailyJournalCard(listOf(similarEntry), onFindSimilar = null)
                        }
                    }
                }
            }
        )
    }

    LaunchedEffect(showSimilarDialogForEntry) {
        val entry = showSimilarDialogForEntry
        if (entry != null) {
            isLoadingSimilar = true
            similarEntriesList = emptyList()
            try {
                similarEntriesList = vm.findSimilarEntries(entry.id)
            } finally {
                isLoadingSimilar = false
            }
        }
    }

    if (showSimilarInfo) {
        AlertDialog(
            onDismissRequest = { showSimilarInfo = false },
            confirmButton = {
                TextButton(onClick = { showSimilarInfo = false }) {
                    Text("Got it")
                }
            },
            title = {
                Text(
                    text = "How Semantic Search Works",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Laniakea doesn't just match exact keywords. Instead, the AI analyzes the deep emotional context and meaning behind your thoughts.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Visual Example Block Container
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "If you look up an abstract entry like:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "“Lingering a little too long in the doorway.”",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                fontStyle = FontStyle.Italic
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                            )
                            Text(
                                text = "The AI maps the feeling of hesitation, dread, or being stuck, pulling up matching moments like:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "• “Watching the shadows move across the wall...”\n• “A strange sense of impending deadlines.”",
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 20.sp
                            )
                        }
                    }

                    Text(
                        text = "Even with zero identical words, Laniakea connects entries that share the exact same state of mind.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )
    }
}
