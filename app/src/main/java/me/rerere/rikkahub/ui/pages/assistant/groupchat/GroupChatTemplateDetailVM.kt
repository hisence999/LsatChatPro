package me.rerere.rikkahub.ui.pages.assistant.groupchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.GroupChatSeat
import me.rerere.rikkahub.data.model.GroupChatSeatOverrides
import me.rerere.rikkahub.data.model.GroupChatTemplate
import me.rerere.rikkahub.data.model.ensureSeatInstanceNumbers
import kotlin.uuid.Uuid

class GroupChatTemplateDetailVM(
    id: String,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val templateId: Uuid? = runCatching { Uuid.parse(id) }.getOrNull()

    val settings: StateFlow<Settings> = settingsStore.settingsFlow

    val template: StateFlow<GroupChatTemplate?> = settingsStore.settingsFlow
        .map { settings ->
            templateId?.let { id -> settings.groupChatTemplates.find { it.id == id } }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        val id = templateId
        if (id != null) {
            viewModelScope.launch(Dispatchers.IO) {
                settingsStore.update { settings ->
                    if (settings.groupChatTemplates.any { it.id == id }) return@update settings
                    settings.copy(groupChatTemplates = settings.groupChatTemplates + GroupChatTemplate(id = id))
                }
            }
        }
    }

    fun updateName(name: String) {
        updateTemplate { it.copy(name = name) }
    }

    fun updateHostModel(modelId: Uuid?) {
        updateTemplate { it.copy(hostModelId = modelId) }
    }

    fun updateIntegrationModel(modelId: Uuid?) {
        updateTemplate { it.copy(integrationModelId = modelId) }
    }

    fun updateConsolidationDelayMinutes(minutes: Int) {
        updateTemplate { it.copy(consolidationDelayMinutes = minutes.coerceAtLeast(0)) }
    }

    fun addSeat(assistantId: Uuid) {
        updateTemplate { template ->
            val nextInstanceNumber = (template.seats.asSequence()
                .filter { seat -> seat.assistantId == assistantId }
                .map { seat -> seat.instanceNumber }
                .filter { number -> number >= 1 }
                .maxOrNull() ?: 0) + 1

            template.copy(
                seats = template.seats + GroupChatSeat(
                    assistantId = assistantId,
                    instanceNumber = nextInstanceNumber,
                )
            ).ensureSeatInstanceNumbers()
        }
    }

    fun removeSeat(seatId: Uuid) {
        updateTemplate { template ->
            template.copy(seats = template.seats.filterNot { it.id == seatId })
        }
    }

    fun setSeatEnabled(seatId: Uuid, enabled: Boolean) {
        updateSeat(seatId) { seat -> seat.copy(defaultEnabled = enabled) }
    }

    fun updateSeatOverrides(seatId: Uuid, transform: (GroupChatSeatOverrides) -> GroupChatSeatOverrides) {
        updateSeat(seatId) { seat -> seat.copy(overrides = transform(seat.overrides)) }
    }

    fun deleteTemplate() {
        val id = templateId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.update { settings ->
                settings.copy(groupChatTemplates = settings.groupChatTemplates.filterNot { it.id == id })
            }
        }
    }

    private fun updateSeat(seatId: Uuid, transform: (GroupChatSeat) -> GroupChatSeat) {
        updateTemplate { template ->
            template.copy(seats = template.seats.map { seat ->
                if (seat.id == seatId) transform(seat) else seat
            })
        }
    }

    private fun updateTemplate(transform: (GroupChatTemplate) -> GroupChatTemplate) {
        val id = templateId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.update { settings ->
                val index = settings.groupChatTemplates.indexOfFirst { it.id == id }
                if (index < 0) return@update settings

                val current = settings.groupChatTemplates[index]
                val updated = transform(current).ensureSeatInstanceNumbers()
                val newTemplates = settings.groupChatTemplates.toMutableList().apply {
                    this[index] = updated
                }
                settings.copy(groupChatTemplates = newTemplates)
            }
        }
    }
}
