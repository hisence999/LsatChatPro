package me.rerere.rikkahub.ui.pages.developer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.ai.AILoggingManager
import me.rerere.rikkahub.data.ai.AIRequestLogManager

class DeveloperVM(
    private val aiLoggingManager: AILoggingManager,
    private val requestLogManager: AIRequestLogManager,
) : ViewModel() {
    val logs = aiLoggingManager.getLogs()
    val requestLogs = requestLogManager.observeRecent()

    fun clearRequestLogs() {
        viewModelScope.launch {
            requestLogManager.clearAll()
        }
    }
}
