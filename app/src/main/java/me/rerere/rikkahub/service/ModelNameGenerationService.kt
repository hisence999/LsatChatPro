package me.rerere.rikkahub.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.utils.applyPlaceholders

private const val TAG = "ModelNameGenerationService"

class ModelNameGenerationService(
    private val providerManager: ProviderManager,
) {
    suspend fun generateModelName(settings: Settings, modelId: String): String? = withContext(Dispatchers.IO) {
        val modelIdTrimmed = modelId.trim()
        if (modelIdTrimmed.isBlank()) {
            return@withContext null
        }

        val model = settings.findModelById(settings.modelNameGenerationModelId) ?: return@withContext null
        val provider = model.findProvider(settings.providers) ?: return@withContext null
        val providerHandler = providerManager.getProviderByType(provider)
        val prompt = settings.modelNameGenerationPrompt
            .applyPlaceholders("model_id" to modelIdTrimmed)

        runCatching {
            providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(UIMessage.user(prompt)),
                params = TextGenerationParams(
                    model = model,
                    temperature = 0f,
                    thinkingBudget = 0
                )
            )
        }.onFailure {
            Log.w(TAG, "generateModelName failed: ${it.message}", it)
        }.getOrNull()
            ?.choices
            ?.firstOrNull()
            ?.message
            ?.toContentText()
            ?.toGeneratedName()
    }
}

private fun String.toGeneratedName(): String? {
    val firstLine = lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        ?: return null

    val normalized = firstLine
        .removePrefix("model_name:")
        .removePrefix("Model Name:")
        .removePrefix("model name:")
        .trim()
        .trim('"', '\'', '`')

    return normalized.takeIf { it.isNotBlank() }
}
