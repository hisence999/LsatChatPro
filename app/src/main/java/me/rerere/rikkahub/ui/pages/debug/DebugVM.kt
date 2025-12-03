package me.rerere.rikkahub.ui.pages.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.ai.ui.UIMessage
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class DebugVM(
    private val settingsStore: SettingsStore,
    private val generationHandler: GenerationHandler,
    private val memoryRepository: me.rerere.rikkahub.data.repository.MemoryRepository
) : ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings.dummy())

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            settingsStore.update(settings)
        }
    }

    suspend fun debugPrompt(message: String): String = withContext(Dispatchers.IO) {
        val settings = settings.value
        val assistant = settings.getCurrentAssistant()
        val model = settings.getCurrentChatModel() ?: return@withContext "No model selected"
        
        val memories = if (assistant.enableMemory && assistant.useRagMemoryRetrieval) {
            memoryRepository.retrieveRelevantMemories(
                assistantId = assistant.id.toString(),
                query = message,
                limit = assistant.ragLimit,
                similarityThreshold = assistant.ragSimilarityThreshold,
                includeCore = assistant.ragIncludeCore,
                includeEpisodes = assistant.ragIncludeEpisodes
            )
        } else {
            emptyList()
        }

        val messages = listOf(UIMessage.user(message))
        
        val builtMessages = generationHandler.buildMessages(
            assistant = assistant,
            settings = settings,
            messages = messages,
            model = model,
            tools = emptyList(),
            memories = memories,
            truncateIndex = -1
        )
        
        builtMessages.joinToString("\n\n") { 
            "## [${it.role}]\n${it.toText()}" 
        }
    }
}
