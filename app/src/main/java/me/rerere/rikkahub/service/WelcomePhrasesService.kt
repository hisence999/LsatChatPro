package me.rerere.rikkahub.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_WELCOME_PHRASES_PROMPT
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.utils.applyPlaceholders
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.uuid.Uuid

private const val TAG = "WelcomePhrasesService"

class WelcomePhrasesService(
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
    private val memoryRepository: MemoryRepository,
) {
    private val mutex = Mutex()

    suspend fun refreshForCurrentAssistantIfNeeded() {
        val settingsSnapshot = settingsStore.settingsFlow.value
        if (settingsSnapshot.init) return
        refreshForAssistantIfNeeded(settingsSnapshot.assistantId)
    }

    suspend fun refreshForAssistantIfNeeded(assistantId: Uuid) {
        refreshForAssistant(assistantId, force = false)
    }

    suspend fun forceRefreshForAssistant(assistantId: Uuid) {
        refreshForAssistant(assistantId, force = true)
    }

    private suspend fun refreshForAssistant(assistantId: Uuid, force: Boolean) {
        val pending = mutex.withLock {
            val settingsSnapshot = settingsStore.settingsFlow.value
            if (settingsSnapshot.init) return@withLock null

            val todayEpochDay = LocalDate.now().toEpochDay()
            val assistant = settingsSnapshot.getAssistantById(assistantId) ?: return@withLock null
            if (!assistant.enableWelcomePhrases) return@withLock null
            if (assistant.presetMessages.isNotEmpty()) return@withLock null
            if (!force && assistant.lastWelcomePhrasesRequestEpochDay == todayEpochDay) return@withLock null

            val model = settingsSnapshot.findModelById(settingsSnapshot.suggestionModelId) ?: return@withLock null
            val provider = model.findProvider(settingsSnapshot.providers) ?: return@withLock null

            settingsStore.update { current ->
                current.copy(
                    assistants = current.assistants.map { currentAssistant ->
                        if (currentAssistant.id == assistantId) {
                            currentAssistant.copy(lastWelcomePhrasesRequestEpochDay = todayEpochDay)
                        } else {
                            currentAssistant
                        }
                    }
                )
            }

            PendingRequest(
                assistantId = assistantId,
                assistantSystemPrompt = assistant.systemPrompt,
                todayEpochDay = todayEpochDay,
                model = model,
                provider = provider,
                locale = Locale.getDefault(),
                enableMemory = assistant.enableMemory,
                ragSimilarityThreshold = assistant.ragSimilarityThreshold,
                ragIncludeCore = assistant.ragIncludeCore,
                ragIncludeEpisodes = assistant.ragIncludeEpisodes,
            )
        } ?: return

        val phrases = fetchWithRetry(pending, maxAttempts = 3) ?: return

        settingsStore.update { current ->
            val updatedAssistants = current.assistants.map { assistant ->
                if (assistant.id == pending.assistantId) {
                    assistant.copy(
                        welcomePhrases = phrases,
                        lastWelcomePhrasesRequestEpochDay = pending.todayEpochDay,
                    )
                } else {
                    assistant
                }
            }
            current.copy(assistants = updatedAssistants)
        }
    }

    private suspend fun fetchWithRetry(
        pending: PendingRequest,
        maxAttempts: Int,
    ): List<String>? {
        for (attempt in 1..maxAttempts) {
            val phrases = runCatching { fetchOnce(pending) }.getOrElse { e ->
                Log.w(TAG, "fetch attempt $attempt failed: ${e.message}", e)
                null
            }
            if (phrases != null) return phrases
            if (attempt < maxAttempts) delay(400L * attempt)
        }
        return null
    }

    private suspend fun fetchOnce(pending: PendingRequest): List<String> {
        val providerHandler = providerManager.getProviderByType(pending.provider)
        val localeHint = "${pending.locale.toLanguageTag()} (${pending.locale.displayName})"
        val today = LocalDate.ofEpochDay(pending.todayEpochDay)
        val todayHint = buildString {
            append(today)
            append(" (")
            append(today.dayOfWeek.getDisplayName(TextStyle.FULL, pending.locale))
            append(")")
        }

        val memoryContext = withContext(Dispatchers.IO) {
            val assistantIdString = pending.assistantId.toString()
            val dateQuery = buildDateQuery(today)

            val ragMemories = memoryRepository.retrieveRelevantMemories(
                assistantId = assistantIdString,
                query = dateQuery,
                limit = 10,
                similarityThreshold = pending.ragSimilarityThreshold,
                includeCore = true,
                includeEpisodes = true,
            )

            val recentMemories = memoryRepository.getRecentCombinedMemories(
                assistantId = assistantIdString,
                limit = 5,
                includeCore = true,
                includeEpisodes = true,
            )

            MemoryContext(
                ragMemoriesText = formatMemoriesForPrompt(ragMemories),
                recentMemoriesText = formatMemoriesForPrompt(recentMemories),
            )
        }

        val messages = buildList {
            pending.assistantSystemPrompt
                .takeIf { it.isNotBlank() }
                ?.let { add(UIMessage.system(it)) }
            add(
                UIMessage.user(
                    DEFAULT_WELCOME_PHRASES_PROMPT.applyPlaceholders(
                        "locale" to localeHint,
                        "date" to todayHint,
                        "rag_memories" to memoryContext.ragMemoriesText,
                        "recent_memories" to memoryContext.recentMemoriesText,
                    )
                )
            )
        }

        val result = withContext(Dispatchers.IO) {
            providerHandler.generateText(
                providerSetting = pending.provider,
                messages = messages,
                params = TextGenerationParams(
                    model = pending.model,
                    temperature = 0.9f,
                    thinkingBudget = 0,
                ),
            )
        }

        val raw = result.choices.firstOrNull()?.message?.toContentText().orEmpty()
        val phrases = parseWelcomePhrases(raw)
        require(phrases.size == WELCOME_PHRASES_TOTAL) {
            "Expected $WELCOME_PHRASES_TOTAL welcome phrases, got ${phrases.size}. raw=$raw"
        }
        return phrases
    }

    private data class PendingRequest(
        val assistantId: Uuid,
        val assistantSystemPrompt: String,
        val todayEpochDay: Long,
        val model: Model,
        val provider: ProviderSetting,
        val locale: Locale,
        val enableMemory: Boolean,
        val ragSimilarityThreshold: Float,
        val ragIncludeCore: Boolean,
        val ragIncludeEpisodes: Boolean,
    )

    private data class MemoryContext(
        val ragMemoriesText: String,
        val recentMemoriesText: String,
    ) {
        companion object {
            val EMPTY = MemoryContext(
                ragMemoriesText = "none",
                recentMemoriesText = "none",
            )
        }
    }

    private fun formatMemoriesForPrompt(
        memories: List<me.rerere.rikkahub.data.model.AssistantMemory>,
    ): String {
        if (memories.isEmpty()) return "none"
        return buildString {
            memories.forEachIndexed { index, memory ->
                if (index > 0) append('\n')
                val typeLabel = when (memory.type) {
                    me.rerere.rikkahub.data.db.entity.MemoryType.CORE -> "CORE"
                    me.rerere.rikkahub.data.db.entity.MemoryType.EPISODIC -> "EPISODIC"
                    else -> "UNKNOWN"
                }
                append("- [").append(typeLabel).append("] ")
                append(memory.content.trim().replace("\n", " ").take(120))
            }
        }
    }

    private fun buildDateQuery(today: LocalDate): String {
        val monthDayDash = "%02d-%02d".format(today.monthValue, today.dayOfMonth)
        val monthDaySlash = "${today.monthValue}/${today.dayOfMonth}"
        return buildString {
            append(today)
            append(' ')
            append(monthDayDash)
            append(' ')
            append(monthDaySlash)
            append(" today")
        }
    }
}
