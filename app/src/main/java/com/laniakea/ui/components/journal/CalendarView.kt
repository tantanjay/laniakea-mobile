package com.laniakea.ui.components.journal

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.laniakea.data.DiaryEntry
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarView(
    currentMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    entries: List<DiaryEntry>,
    selectedRange: Pair<Long, Long>?,
    onRangeSelected: (Long?, Long?) -> Unit
) {
    val daysInMonth = currentMonth.lengthOfMonth()
    val firstDayOffset = currentMonth.atDay(1).dayOfWeek.value % 7 // Sunday = 0
    val days = (1..daysInMonth).toList()

    val moodMap = remember(entries) {
        entries.groupBy {
            Instant.ofEpochMilli(it.dateTime)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }.mapValues { (_, entries) ->
            entries.map { it.numericMood }.average()
        }
    }

    var dragStartDay by remember { mutableStateOf<LocalDate?>(null) }
    var dragCurrentDay by remember { mutableStateOf<LocalDate?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            /* ───────── Month Header ───────── */

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onMonthChange(currentMonth.minusMonths(1)) }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous")
                }

                Text(
                    text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = { onMonthChange(currentMonth.plusMonths(1)) }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next")
                }
            }

            /* ───────── Weekday Header ───────── */

            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                    Text(
                        text = it,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            /* ───────── Calendar Grid (FIXED) ───────── */

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val cellSizePx = with(LocalDensity.current) {
                    (maxWidth / 7).toPx()
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(currentMonth) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    val row = (offset.y / cellSizePx).toInt()
                                    val col = (offset.x / cellSizePx).toInt()
                                    val index = row * 7 + col - firstDayOffset
                                    if (index in 0 until daysInMonth) {
                                        dragStartDay = currentMonth.atDay(index + 1)
                                        dragCurrentDay = dragStartDay
                                    }
                                },
                                onDrag = { change, _ ->
                                    val offset = change.position
                                    val row = (offset.y / cellSizePx).toInt()
                                    val col = (offset.x / cellSizePx).toInt()
                                    val index = row * 7 + col - firstDayOffset
                                    if (index in 0 until daysInMonth) {
                                        dragCurrentDay = currentMonth.atDay(index + 1)
                                    }
                                },
                                onDragEnd = {
                                    if (dragStartDay != null && dragCurrentDay != null) {
                                        val start = minOf(dragStartDay!!, dragCurrentDay!!)
                                        val end = maxOf(dragStartDay!!, dragCurrentDay!!)
                                        onRangeSelected(
                                            start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                                            end.atTime(23, 59, 59)
                                                .atZone(ZoneId.systemDefault())
                                                .toInstant()
                                                .toEpochMilli()
                                        )
                                    }
                                    dragStartDay = null
                                    dragCurrentDay = null
                                },
                                onDragCancel = {
                                    dragStartDay = null
                                    dragCurrentDay = null
                                }
                            )
                        },
                    userScrollEnabled = false
                ) {

                    /* Empty leading cells */
                    items(firstDayOffset) {
                        Spacer(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .padding(2.dp)
                        )
                    }

                    /* Actual days */
                    items(days) { day ->
                        val date = currentMonth.atDay(day)
                        val avgMood = moodMap[date]

                        val isSelected =
                            if (dragStartDay != null && dragCurrentDay != null) {
                                val start = minOf(dragStartDay!!, dragCurrentDay!!)
                                val end = maxOf(dragStartDay!!, dragCurrentDay!!)
                                date in start..end
                            } else if (selectedRange != null) {
                                val start = Instant.ofEpochMilli(selectedRange.first)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                val end = Instant.ofEpochMilli(selectedRange.second)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                date in start..end
                            } else false

                        DayCell(
                            day = day,
                            avgMood = avgMood,
                            isSelected = isSelected,
                            onClick = {
                                val start = date
                                    .atStartOfDay(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli()
                                onRangeSelected(start, start + 86_399_999)
                            }
                        )
                    }
                }
            }
        }
    }
}