package me.rerere.rikkahub.ui.pages.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.ai.AIRequestLogManager
import me.rerere.rikkahub.data.ai.AIRequestSource
import me.rerere.rikkahub.data.db.entity.AIRequestLogEntity

class RequestLogsVM(
    private val requestLogManager: AIRequestLogManager,
) : ViewModel() {
    private val rawLogs = requestLogManager.observeRecent()

    private val _sourceFilter = MutableStateFlow<AIRequestSource?>(null)
    val sourceFilter: StateFlow<AIRequestSource?> = _sourceFilter.asStateFlow()

    val availableSources = rawLogs
        .map { logs ->
            logs.mapNotNull { log ->
                runCatching { AIRequestSource.valueOf(log.source) }.getOrNull()
            }.distinct().sortedBy { it.ordinal }
        }
        .distinctUntilChanged()

    val logs = combine(rawLogs, _sourceFilter) { logs, filter ->
        if (filter == null) logs else logs.filter { it.source == filter.name }
    }

    fun setSourceFilter(source: AIRequestSource?) {
        _sourceFilter.value = source
    }

    fun clearAll() {
        viewModelScope.launch {
            requestLogManager.clearAll()
            _sourceFilter.value = null
        }
    }
}
