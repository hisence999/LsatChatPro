package me.rerere.rikkahub.ui.pages.developer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.ai.AILoggingManager

class DeveloperVM(
    private val aiLoggingManager: AILoggingManager,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    val logs = aiLoggingManager.getLogs()
    val settings = settingsStore.settingsFlow

    fun updateSettings(update: (Settings) -> Settings) {
        viewModelScope.launch {
            settingsStore.update(update)
        }
    }
}
