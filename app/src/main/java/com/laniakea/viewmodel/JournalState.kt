package com.laniakea.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.laniakea.data.DiaryDatabase
import com.laniakea.data.DiaryEntry
import com.laniakea.manager.SecurityManager
import com.laniakea.manager.SemanticManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId
import kotlin.time.Duration.Companion.milliseconds

class JournalState(
    private val db: DiaryDatabase,
    private val semanticManager: SemanticManager,
    private val securityManager: SecurityManager,
    private val coroutineScope: CoroutineScope
) {
    val allEntries = db.diaryDao().getAllEntriesFlow()
        .stateIn(coroutineScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedDateRange = MutableStateFlow<Pair<Long, Long>?>(null)
    val selectedDateRange: StateFlow<Pair<Long, Long>?> = _selectedDateRange.asStateFlow()

    private val _viewingMonth = MutableStateFlow(YearMonth.now())
    val viewingMonth: StateFlow<YearMonth> = _viewingMonth.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredEntries = combine(_selectedDateRange, _viewingMonth) { range, month ->
        range ?: run {
            val start = month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val end = month.atEndOfMonth().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            start to end
        }
    }.flatMapLatest { (start, end) ->
        db.diaryDao().getEntriesInRange(start, end)
    }.map { entries ->
        entries.map { securityManager.decryptEntry(it) }
    }.stateIn(coroutineScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var isCalendarExpanded by mutableStateOf(true)

    var searchQuery by mutableStateOf("")
    var isSearching by mutableStateOf(false)
    var searchResults by mutableStateOf<List<DiaryEntry>>(emptyList())

    var showSimilarDialogForEntry by mutableStateOf<DiaryEntry?>(null)
    var similarEntriesList by mutableStateOf<List<DiaryEntry>>(emptyList())
    var isLoadingSimilar by mutableStateOf(false)
    var showSimilarInfo by mutableStateOf(false)

    fun setSelectedDateRange(start: Long?, end: Long?) {
        if (start == null || end == null) {
            _selectedDateRange.value = null
        } else {
            _selectedDateRange.value = minOf(start, end) to maxOf(start, end)
        }
    }

    fun setViewingMonth(month: YearMonth) {
        _viewingMonth.value = month
        _selectedDateRange.value = null
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery = query
        if (query.isNotBlank()) {
            isSearching = true
            coroutineScope.launch {
                delay(500.milliseconds)
                if (searchQuery == query) {
                    searchResults = semanticManager.semanticSearch(query, 5)
                    isSearching = false
                }
            }
        } else {
            searchResults = emptyList()
            isSearching = false
        }
    }

    fun findSimilarEntriesFor(entry: DiaryEntry) {
        showSimilarDialogForEntry = entry
        isLoadingSimilar = true
        similarEntriesList = emptyList()
        coroutineScope.launch {
            try {
                similarEntriesList = semanticManager.findSimilarEntries(entry.id, 5)
            } finally {
                isLoadingSimilar = false
            }
        }
    }

    fun closeSimilarDialog() {
        showSimilarDialogForEntry = null
    }
}
