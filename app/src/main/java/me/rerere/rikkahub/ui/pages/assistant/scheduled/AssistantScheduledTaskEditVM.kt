package me.rerere.rikkahub.ui.pages.assistant.scheduled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.dao.ScheduledTaskDao
import me.rerere.rikkahub.data.db.entity.ScheduledTaskEntity
import me.rerere.rikkahub.service.scheduledtask.ScheduledTaskAccuracyMode
import me.rerere.rikkahub.service.scheduledtask.ScheduledTaskIntervalUnit
import me.rerere.rikkahub.service.scheduledtask.ScheduledTaskNextRunCalculator
import me.rerere.rikkahub.service.scheduledtask.ScheduledTaskOverrideType
import me.rerere.rikkahub.service.scheduledtask.ScheduledTaskRepeatType
import me.rerere.rikkahub.service.scheduledtask.ScheduledTaskScheduler
import kotlin.uuid.Uuid

data class ScheduledTaskDraft(
    val id: String,
    val assistantId: String,
    val name: String,
    val enabled: Boolean,
    val promptTemplate: String,
    val timeOfDayMinutes: Int,
    val repeatType: Int,
    val weeklyMask: Int,
    val monthlyDay: Int,
    val intervalValue: Int,
    val intervalUnit: Int,
    val overrideModelId: String?,
    val searchOverrideType: Int,
    val searchProviderIndex: Int,
    val mcpOverrideType: Int,
    val mcpServerId: String?,
    val accuracyMode: Int,
    val notifyOnDone: Boolean,
) {
    fun toEntity(
        lastRunAt: Long? = null,
        lastScheduledFor: Long? = null,
        nextRunAt: Long? = null,
    ): ScheduledTaskEntity {
        return ScheduledTaskEntity(
            id = id,
            assistantId = assistantId,
            name = name,
            enabled = enabled,
            promptTemplate = promptTemplate,
            timeOfDayMinutes = timeOfDayMinutes,
            repeatType = repeatType,
            weeklyMask = weeklyMask,
            monthlyDay = monthlyDay,
            intervalValue = intervalValue,
            intervalUnit = intervalUnit,
            overrideModelId = overrideModelId?.takeIf { it.isNotBlank() },
            searchOverrideType = searchOverrideType,
            searchProviderIndex = searchProviderIndex,
            mcpOverrideType = mcpOverrideType,
            mcpServerId = mcpServerId?.takeIf { it.isNotBlank() },
            accuracyMode = accuracyMode,
            notifyOnDone = notifyOnDone,
            lastRunAt = lastRunAt,
            lastScheduledFor = lastScheduledFor,
            nextRunAt = nextRunAt,
            lastErrorCode = null,
            lastErrorAt = null,
        )
    }
}

class AssistantScheduledTaskEditVM(
    assistantId: String,
    taskId: String?,
    private val taskDao: ScheduledTaskDao,
    private val scheduler: ScheduledTaskScheduler,
) : ViewModel() {

    private val _existingTask = MutableStateFlow<ScheduledTaskEntity?>(null)
    val existingTask: StateFlow<ScheduledTaskEntity?> = _existingTask.asStateFlow()

    private val _draft = MutableStateFlow(
        ScheduledTaskDraft(
            id = taskId ?: Uuid.random().toString(),
            assistantId = assistantId,
            name = "",
            enabled = true,
            promptTemplate = "",
            timeOfDayMinutes = 9 * 60,
            repeatType = ScheduledTaskRepeatType.DAILY,
            weeklyMask = 0,
            monthlyDay = 1,
            intervalValue = 1,
            intervalUnit = ScheduledTaskIntervalUnit.DAYS,
            overrideModelId = null,
            searchOverrideType = ScheduledTaskOverrideType.INHERIT,
            searchProviderIndex = -1,
            mcpOverrideType = ScheduledTaskOverrideType.INHERIT,
            mcpServerId = null,
            accuracyMode = ScheduledTaskAccuracyMode.ECO,
            notifyOnDone = true,
        )
    )
    val draft: StateFlow<ScheduledTaskDraft> = _draft.asStateFlow()

    init {
        if (taskId != null) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    val task = taskDao.getById(taskId)
                    _existingTask.value = task
                    if (task != null) {
                        _draft.value = ScheduledTaskDraft(
                            id = task.id,
                            assistantId = task.assistantId,
                            name = task.name,
                            enabled = task.enabled,
                            promptTemplate = task.promptTemplate,
                            timeOfDayMinutes = task.timeOfDayMinutes,
                            repeatType = task.repeatType,
                            weeklyMask = task.weeklyMask,
                            monthlyDay = task.monthlyDay,
                            intervalValue = task.intervalValue,
                            intervalUnit = task.intervalUnit,
                            overrideModelId = task.overrideModelId,
                            searchOverrideType = task.searchOverrideType,
                            searchProviderIndex = task.searchProviderIndex,
                            mcpOverrideType = task.mcpOverrideType,
                            mcpServerId = task.mcpServerId,
                            accuracyMode = task.accuracyMode,
                            notifyOnDone = task.notifyOnDone,
                        )
                    }
                }
            }
        }
    }

    fun update(block: (ScheduledTaskDraft) -> ScheduledTaskDraft) {
        _draft.update(block)
    }

    suspend fun save(): Boolean = withContext(Dispatchers.IO) {
        val old = _existingTask.value
        val draft = _draft.value

        if (draft.promptTemplate.isBlank()) return@withContext false
        if (draft.timeOfDayMinutes !in 0..(24 * 60 - 1)) return@withContext false

        val entityToValidate = draft.toEntity(
            lastRunAt = old?.lastRunAt,
            lastScheduledFor = old?.lastScheduledFor,
            nextRunAt = old?.nextRunAt,
        )
        val nextRunAt = ScheduledTaskNextRunCalculator.computeNextRunAtMillis(
            task = entityToValidate,
            nowMillis = System.currentTimeMillis(),
        )
        if (nextRunAt == null) return@withContext false

        val toSave = draft.toEntity(
            lastRunAt = old?.lastRunAt,
            lastScheduledFor = old?.lastScheduledFor,
            nextRunAt = nextRunAt,
        )

        taskDao.upsert(toSave)
        scheduler.schedule(toSave.id)
        true
    }

    suspend fun delete(): Boolean = withContext(Dispatchers.IO) {
        val existing = _existingTask.value ?: return@withContext false
        taskDao.deleteById(existing.id)
        scheduler.cancel(existing.id)
        true
    }
}
