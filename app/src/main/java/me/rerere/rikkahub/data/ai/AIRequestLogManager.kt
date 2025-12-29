package me.rerere.rikkahub.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.db.dao.AIRequestLogDao
import me.rerere.rikkahub.data.db.entity.AIRequestLogEntity
import me.rerere.rikkahub.utils.JsonInstant

private const val REQUEST_LOG_KEEP_LATEST = 200
private const val REQUEST_LOG_MAX_JSON_CHARS = 120_000
private const val REQUEST_LOG_MAX_PREVIEW_CHARS = 240

class AIRequestLogManager(
    private val dao: AIRequestLogDao,
) {
    fun observeRecent(limit: Int = REQUEST_LOG_KEEP_LATEST) = dao.observeRecent(limit)

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        runCatching { dao.clearAll() }
    }

    suspend fun logTextGeneration(
        source: AIRequestSource,
        providerSetting: ProviderSetting,
        params: TextGenerationParams,
        requestMessages: List<UIMessage>,
        responseText: String,
        stream: Boolean,
        durationMs: Long?,
        error: Throwable? = null,
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val safeParams = sanitizeTextGenerationParams(params)
            val paramsJson = JsonInstant.encodeToString(TextGenerationParams.serializer(), safeParams)
                .truncateTo(REQUEST_LOG_MAX_JSON_CHARS)
            val requestMessagesJson = JsonInstant.encodeToString(
                serializer = kotlinx.serialization.builtins.ListSerializer(UIMessage.serializer()),
                value = requestMessages
            ).truncateTo(REQUEST_LOG_MAX_JSON_CHARS)

            val requestPreview = buildRequestPreview(requestMessages)
            val responsePreview = responseText.trim().replace("\r", "").replace("\n", " ")
                .take(REQUEST_LOG_MAX_PREVIEW_CHARS)

            val providerType = providerSetting::class.simpleName ?: "Provider"

            dao.insert(
                AIRequestLogEntity(
                    createdAt = System.currentTimeMillis(),
                    durationMs = durationMs,
                    source = source.name,
                    providerName = providerSetting.name,
                    providerType = providerType,
                    modelId = params.model.modelId,
                    modelDisplayName = params.model.displayName,
                    stream = stream,
                    paramsJson = paramsJson,
                    requestMessagesJson = requestMessagesJson,
                    requestPreview = requestPreview,
                    responsePreview = responsePreview,
                    error = error?.let { "[${it.javaClass.simpleName}] ${it.message}".take(800) },
                )
            )
            dao.pruneKeepLatest(REQUEST_LOG_KEEP_LATEST)
        }
    }
}

private fun sanitizeTextGenerationParams(params: TextGenerationParams): TextGenerationParams {
    val sensitiveHeaderNames = setOf(
        "authorization",
        "x-api-key",
        "api-key",
        "apikey",
        "x-auth-token",
        "cookie",
    )
    val filteredHeaders = params.customHeaders.filterNot { header ->
        header.name.trim().lowercase() in sensitiveHeaderNames
    }
    return params.copy(customHeaders = filteredHeaders)
}

private fun buildRequestPreview(messages: List<UIMessage>): String {
    val text = messages.asSequence()
        .flatMap { it.parts.asSequence() }
        .filterIsInstance<me.rerere.ai.ui.UIMessagePart.Text>()
        .joinToString("\n") { it.text }
        .trim()
    return text.replace("\r", "").replace("\n", " ").take(REQUEST_LOG_MAX_PREVIEW_CHARS)
}

private fun String.truncateTo(maxChars: Int): String {
    if (length <= maxChars) return this
    return take(maxChars) + "\n...(truncated)"
}
