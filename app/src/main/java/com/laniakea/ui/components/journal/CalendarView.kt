package com.laniakea.ui.components.journal

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.platform.LocalLocale
import kotlin.math.abs

@Composable
fun CalendarView(
    currentMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    entries: List<DiaryEntry>,
    selectedRange: Pair<Long, Long>?,
    onRangeSelected: (Long?, Long?) -> Unit,
    onSwipeUp: () -> Unit = {}
) {
    val moodMap = remember(entries) {
        entries.groupBy {
            Instant.ofEpochMilli(it.dateTime).atZone(ZoneId.systemDefault()).toLocalDate()
        }.mapValues { (_, entries) -> entries.map { it.numericMood }.average() }
    }

    var dragStartDay by remember { mutableStateOf<LocalDate?>(null) }
    var dragCurrentDay by remember { mutableStateOf<LocalDate?>(null) }

    Surface(
        modifier = Modifier.fillMaxWidth()
            .pointerInput(currentMonth) {
                var dragAmountX = 0f
                var dragAmountY = 0f
                detectDragGestures(
                    onDragStart = { 
                        dragAmountX = 0f
                        dragAmountY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragAmountX += dragAmount.x
                        dragAmountY += dragAmount.y
                    },
                    onDragEnd = {
                        if (abs(dragAmountX) > abs(dragAmountY)) {
                            if (dragAmountX > 50) {
                                onMonthChange(currentMonth.minusMonths(1))
                            } else if (dragAmountX < -50) {
                                onMonthChange(currentMonth.plusMonths(1))
                            }
                        } else {
                            if (dragAmountY < -50) {
                                onSwipeUp()
                            }
                        }
                    }
                )
            },
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
                    text = "${currentMonth.month.getDisplayName(TextStyle.FULL, LocalLocale.current.platformLocale)} ${currentMonth.year}",
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

                AnimatedContent(
                    targetState = currentMonth,
                    transitionSpec = {
                        if (targetState.isAfter(initialState)) {
                            (slideInHorizontally(animationSpec = tween(300), initialOffsetX = { it }) + fadeIn(animationSpec = tween(300))) togetherWith
                                    (slideOutHorizontally(animationSpec = tween(300), targetOffsetX = { -it }) + fadeOut(animationSpec = tween(300)))
                        } else {
                            (slideInHorizontally(animationSpec = tween(300), initialOffsetX = { -it }) + fadeIn(animationSpec = tween(300))) togetherWith
                                    (slideOutHorizontally(animationSpec = tween(300), targetOffsetX = { it }) + fadeOut(animationSpec = tween(300)))
                        }
                    },
                    label = "month_transition"
                ) { month ->
                    val animDaysInMonth = month.lengthOfMonth()
                    val animFirstDayOffset = month.atDay(1).dayOfWeek.value % 7
                    val animDays = (1..animDaysInMonth).toList()

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(month) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        val row = (offset.y / cellSizePx).toInt()
                                        val col = (offset.x / cellSizePx).toInt()
                                        val index = row * 7 + col - animFirstDayOffset
                                        if (index in 0 until animDaysInMonth) {
                                            dragStartDay = month.atDay(index + 1)
                                            dragCurrentDay = dragStartDay
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        val offset = change.position
                                        val row = (offset.y / cellSizePx).toInt()
                                        val col = (offset.x / cellSizePx).toInt()
                                        val index = row * 7 + col - animFirstDayOffset
                                        if (index in 0 until animDaysInMonth) dragCurrentDay = month.atDay(index + 1)
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
                        items(animFirstDayOffset) { Spacer(modifier = Modifier.aspectRatio(1f)) }
                        items(animDays) { day ->
                            val date = month.atDay(day)
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
}
