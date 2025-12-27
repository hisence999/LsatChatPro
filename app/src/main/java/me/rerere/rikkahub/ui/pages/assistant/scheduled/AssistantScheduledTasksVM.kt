package me.rerere.rikkahub.ui.pages.assistant.scheduled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.dao.ScheduledTaskDao
import me.rerere.rikkahub.data.db.entity.ScheduledTaskEntity
import me.rerere.rikkahub.service.scheduledtask.ScheduledTaskScheduler

class AssistantScheduledTasksVM(
    assistantId: String,
    settingsStore: SettingsStore,
    private val taskDao: ScheduledTaskDao,
    private val scheduler: ScheduledTaskScheduler,
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsStore.settingsFlow

    val tasks: StateFlow<List<ScheduledTaskEntity>> = taskDao
        .observeTasksOfAssistant(assistantId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setEnabled(taskId: String, enabled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                taskDao.updateEnabled(taskId, enabled)
                scheduler.schedule(taskId)
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                taskDao.deleteById(taskId)
                scheduler.cancel(taskId)
            }
        }
    }
}

