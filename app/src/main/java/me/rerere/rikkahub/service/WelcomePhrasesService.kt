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
import me.rerere.rikkahub.utils.applyPlaceholders
import java.time.LocalDate
import java.util.Locale
import kotlin.uuid.Uuid

private const val TAG = "WelcomePhrasesService"

class WelcomePhrasesService(
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
) {
    private val mutex = Mutex()

    suspend fun refreshForCurrentAssistantIfNeeded() {
        val settingsSnapshot = settingsStore.settingsFlow.value
        if (settingsSnapshot.init) return
        refreshForAssistantIfNeeded(settingsSnapshot.assistantId)
    }

    suspend fun refreshForAssistantIfNeeded(assistantId: Uuid) {
        val pending = mutex.withLock {
            val settingsSnapshot = settingsStore.settingsFlow.value
            if (settingsSnapshot.init) return@withLock null

            val todayEpochDay = LocalDate.now().toEpochDay()
            if (settingsSnapshot.lastWelcomePhrasesRequestEpochDay == todayEpochDay) return@withLock null

            val assistant = settingsSnapshot.getAssistantById(assistantId) ?: return@withLock null
            if (!assistant.enableWelcomePhrases) return@withLock null
            if (assistant.presetMessages.isNotEmpty()) return@withLock null

            val model = settingsSnapshot.findModelById(settingsSnapshot.suggestionModelId) ?: return@withLock null
            val provider = model.findProvider(settingsSnapshot.providers) ?: return@withLock null

            settingsStore.update { current ->
                if (current.lastWelcomePhrasesRequestEpochDay == todayEpochDay) current
                else current.copy(lastWelcomePhrasesRequestEpochDay = todayEpochDay)
            }

            PendingRequest(
                assistantId = assistantId,
                assistantSystemPrompt = assistant.systemPrompt,
                model = model,
                provider = provider,
                locale = Locale.getDefault(),
            )
        } ?: return

        val phrases = fetchWithRetry(pending, maxAttempts = 3) ?: return

        settingsStore.update { current ->
            val updatedAssistants = current.assistants.map { assistant ->
                if (assistant.id == pending.assistantId) {
                    assistant.copy(welcomePhrases = phrases)
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

        val messages = buildList {
            pending.assistantSystemPrompt
                .takeIf { it.isNotBlank() }
                ?.let { add(UIMessage.system(it)) }
            add(
                UIMessage.user(
                    DEFAULT_WELCOME_PHRASES_PROMPT.applyPlaceholders(
                        "locale" to localeHint,
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
        require(phrases.size >= 10) {
            "Expected >= 10 welcome phrases, got ${phrases.size}. raw=$raw"
        }
        return phrases.take(10)
    }

    private fun parseWelcomePhrases(raw: String): List<String> {
        return raw
            .replace("\r", "")
            .replace("\n", "")
            .split('#')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private data class PendingRequest(
        val assistantId: Uuid,
        val assistantSystemPrompt: String,
        val model: Model,
        val provider: ProviderSetting,
        val locale: Locale,
    )
}
