package com.laniakea.ui.components.journal

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    val firstDayOffset = currentMonth.atDay(1).dayOfWeek.value % 7
    val days = (1..daysInMonth).toList()

    val moodMap = remember(entries) {
        entries.groupBy {
            Instant.ofEpochMilli(it.dateTime).atZone(ZoneId.systemDefault()).toLocalDate()
        }.mapValues { (_, entries) -> entries.map { it.numericMood }.average() }
    }

    var dragStartDay by remember { mutableStateOf<LocalDate?>(null) }
    var dragCurrentDay by remember { mutableStateOf<LocalDate?>(null) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            /* Month Header */
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onMonthChange(currentMonth.minusMonths(1)) }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous", tint = MaterialTheme.colorScheme.primary)
                }

                Text(
                    text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(onClick = { onMonthChange(currentMonth.plusMonths(1)) }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = MaterialTheme.colorScheme.primary)
                }
            }

            /* Weekday Header */
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                    Text(
                        text = it,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                }
            }

            /* Calendar Grid */
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val cellSizePx = with(LocalDensity.current) { (maxWidth / 7).toPx() }

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
                                    if (index in 0 until daysInMonth) dragCurrentDay = currentMonth.atDay(index + 1)
                                },
                                onDragEnd = {
                                    if (dragStartDay != null && dragCurrentDay != null) {
                                        val start = minOf(dragStartDay!!, dragCurrentDay!!)
                                        val end = maxOf(dragStartDay!!, dragCurrentDay!!)
                                        onRangeSelected(
                                            start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                                            end.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                        )
                                    }
                                    dragStartDay = null
                                    dragCurrentDay = null
                                },
                                onDragCancel = { dragStartDay = null; dragCurrentDay = null }
                            )
                        },
                    userScrollEnabled = false
                ) {
                    items(firstDayOffset) { Spacer(modifier = Modifier.aspectRatio(1f)) }
                    items(days) { day ->
                        val date = currentMonth.atDay(day)
                        val avgMood = moodMap[date]
                        val isSelected = if (dragStartDay != null && dragCurrentDay != null) {
                            date in minOf(dragStartDay!!, dragCurrentDay!!)..maxOf(dragStartDay!!, dragCurrentDay!!)
                        } else if (selectedRange != null) {
                            val start = Instant.ofEpochMilli(selectedRange.first).atZone(ZoneId.systemDefault()).toLocalDate()
                            val end = Instant.ofEpochMilli(selectedRange.second).atZone(ZoneId.systemDefault()).toLocalDate()
                            date in start..end
                        } else false

                        DayCell(day = day, avgMood = avgMood, isSelected = isSelected, onClick = {
                            val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            onRangeSelected(start, start + 86_399_999)
                        })
                    }
                }
            }
        }
    }
}
