package com.laniakea.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.laniakea.data.DiaryEntry
import com.laniakea.data.PeriodDigest
import com.laniakea.manager.AnalyticsManager
import com.laniakea.manager.PeriodDigestManager
import com.laniakea.manager.SemanticManager
import com.laniakea.manager.WritingMetrics

import android.content.SharedPreferences
import androidx.core.content.edit

class InsightState(
    private val analyticsManager: AnalyticsManager,
    private val digestManager: PeriodDigestManager,
    private val semanticManager: SemanticManager,
    private val prefs: SharedPreferences? = null
) {
    var writingMetrics by mutableStateOf<WritingMetrics?>(null)
        private set
    var isMetricsLoading by mutableStateOf(false)
        internal set

    val insightRanges = listOf("7D", "14D", "1M", "3M", "6M", "1Y")
    var insightSelectedRange by mutableStateOf(prefs?.getString("selected_range", "1M") ?: "1M")
        private set

    var insightTimeOffset by mutableIntStateOf(0)

    var periodDigest by mutableStateOf<PeriodDigest?>(null)
        private set
    var isDigestLoading by mutableStateOf(false)
        private set

    var themeClusters by mutableStateOf<Map<String, List<DiaryEntry>>?>(null)
        private set
    var isThemesLoading by mutableStateOf(false)
        private set

    var showInfo by mutableStateOf(false)
    var showThemeSelection by mutableStateOf(false)
        
    fun updateSelectedRange(newRange: String) {
        insightSelectedRange = newRange
        insightTimeOffset = 0
        prefs?.edit {
            putString("selected_range", newRange)
        }
    }

    fun getInsightDateRange(): Pair<Long, Long> {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)

        when (insightSelectedRange) {
            "7D" -> {
                calendar.add(java.util.Calendar.WEEK_OF_YEAR, -insightTimeOffset)
                calendar.set(java.util.Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
            }
            "14D" -> {
                calendar.add(java.util.Calendar.WEEK_OF_YEAR, -(insightTimeOffset * 2))
                calendar.set(java.util.Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.add(java.util.Calendar.WEEK_OF_YEAR, -1)
            }
            "1M" -> {
                calendar.add(java.util.Calendar.MONTH, -insightTimeOffset)
                calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            "3M" -> {
                calendar.add(java.util.Calendar.MONTH, -(insightTimeOffset * 3))
                val currentMonth = calendar.get(java.util.Calendar.MONTH)
                val quarterStartMonth = (currentMonth / 3) * 3
                calendar.set(java.util.Calendar.MONTH, quarterStartMonth)
                calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            "6M" -> {
                calendar.add(java.util.Calendar.MONTH, -(insightTimeOffset * 6))
                val currentMonth = calendar.get(java.util.Calendar.MONTH)
                val halfStartMonth = (currentMonth / 6) * 6
                calendar.set(java.util.Calendar.MONTH, halfStartMonth)
                calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            "1Y" -> {
                calendar.add(java.util.Calendar.YEAR, -insightTimeOffset)
                calendar.set(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            else -> {
                calendar.add(java.util.Calendar.MONTH, -insightTimeOffset)
                calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
            }
        }

        val startDate = calendar.timeInMillis

        when (insightSelectedRange) {
            "7D" -> calendar.add(java.util.Calendar.DAY_OF_YEAR, 6)
            "14D" -> calendar.add(java.util.Calendar.DAY_OF_YEAR, 13)
            "1M" -> {
                calendar.add(java.util.Calendar.MONTH, 1)
                calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
            }
            "3M" -> {
                calendar.add(java.util.Calendar.MONTH, 3)
                calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
            }
            "6M" -> {
                calendar.add(java.util.Calendar.MONTH, 6)
                calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
            }
            "1Y" -> {
                calendar.add(java.util.Calendar.YEAR, 1)
                calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
            }
            else -> {
                calendar.add(java.util.Calendar.MONTH, 1)
                calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
            }
        }

        calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
        calendar.set(java.util.Calendar.MINUTE, 59)
        calendar.set(java.util.Calendar.SECOND, 59)
        calendar.set(java.util.Calendar.MILLISECOND, 999)
        val endDate = calendar.timeInMillis

        return Pair(startDate, endDate)
    }

    fun getInsightDisplayDates(): String {
        val (start, end) = getInsightDateRange()
        val formatter = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
        return "${formatter.format(java.util.Date(start))} - ${formatter.format(java.util.Date(end))}"
    }

    suspend fun refreshInsights(selectedThemes: List<String>) {
        if (semanticManager.checkNeedsReclassification()) {
            isThemesLoading = true
            try {
                semanticManager.reclassifyNeededEntries()
            } finally {
                // Keep loading true, as loadThemeClusters will manage it next
            }
        }
        loadThemeClusters(selectedThemes)
        loadPeriodDigest(selectedThemes)
    }

    suspend fun loadThemeClusters(selectedThemes: List<String>) {
        isThemesLoading = true
        try {
            val (start, end) = getInsightDateRange()
            themeClusters = semanticManager.getThemeClusters(selectedThemes, start, end)
        } finally {
            isThemesLoading = false
        }
    }

    suspend fun analyzeWritingTrends(): WritingMetrics {
        val (start, end) = getInsightDateRange()
        val metrics = analyticsManager.analyzeWritingTrends(start, end)
        writingMetrics = metrics
        return metrics
    }

    suspend fun loadPeriodDigest(selectedThemes: List<String>) {
        isDigestLoading = true
        try {
            val (start, end) = getInsightDateRange()
            periodDigest = digestManager.generateDigest(start, end, selectedThemes)
        } finally {
            isDigestLoading = false
        }
    }
}
