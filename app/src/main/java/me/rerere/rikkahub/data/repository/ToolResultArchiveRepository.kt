package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.AIRequestSource
import me.rerere.rikkahub.data.ai.rag.EmbeddingService
import me.rerere.rikkahub.data.ai.rag.VectorEngine
import me.rerere.rikkahub.data.db.dao.EmbeddingCacheDAO
import me.rerere.rikkahub.data.db.dao.ToolResultArchiveDao
import me.rerere.rikkahub.data.db.entity.EmbeddingCacheEntity
import me.rerere.rikkahub.data.db.entity.MemoryType
import me.rerere.rikkahub.data.db.entity.ToolResultArchiveEntity
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull

class ToolResultArchiveRepository(
    private val dao: ToolResultArchiveDao,
    private val embeddingService: EmbeddingService,
    private val embeddingCacheDAO: EmbeddingCacheDAO,
) {
    private val internalToolNames = setOf("create_memory", "edit_memory", "delete_memory")

    suspend fun backfillFromMessages(
        conversationId: String,
        assistantId: String,
        messages: List<UIMessage>,
    ) = withContext(Dispatchers.IO) {
        if (conversationId.isBlank()) return@withContext
        if (assistantId.isBlank()) return@withContext
        if (messages.isEmpty()) return@withContext

        var userTurnIndex = 0
        val entities = buildList {
            messages.forEach { message ->
                if (message.role == MessageRole.USER) userTurnIndex++
                if (message.role != MessageRole.TOOL) return@forEach
                message.getToolResults().forEach { result ->
                    if (result.toolName in internalToolNames) return@forEach
                    add(
                        ToolResultArchiveEntity(
                            conversationId = conversationId,
                            assistantId = assistantId,
                            toolCallId = result.toolCallId,
                            toolName = result.toolName,
                            argumentsJson = result.arguments.toString(),
                            contentJson = result.content.toString(),
                            metadataJson = result.metadata?.toString(),
                            extractText = buildExtractText(result.toolName, result.arguments, result.content),
                            userTurnIndex = userTurnIndex,
                        )
                    )
                }
            }
        }

        if (entities.isNotEmpty()) {
            dao.insertAll(entities)
        }
    }

    suspend fun archiveToolResults(
        conversationId: String,
        assistantId: String,
        userTurnIndex: Int,
        results: List<UIMessagePart.ToolResult>,
    ) = withContext(Dispatchers.IO) {
        if (conversationId.isBlank()) return@withContext
        if (assistantId.isBlank()) return@withContext
        if (results.isEmpty()) return@withContext

        val entities = results
            .asSequence()
            .filter { it.toolName !in internalToolNames }
            .map { result ->
                ToolResultArchiveEntity(
                    conversationId = conversationId,
                    assistantId = assistantId,
                    toolCallId = result.toolCallId,
                    toolName = result.toolName,
                    argumentsJson = result.arguments.toString(),
                    contentJson = result.content.toString(),
                    metadataJson = result.metadata?.toString(),
                    extractText = buildExtractText(result.toolName, result.arguments, result.content),
                    userTurnIndex = userTurnIndex.coerceAtLeast(0),
                )
            }
            .toList()

        if (entities.isNotEmpty()) {
            dao.insertAll(entities)
        }
    }

    suspend fun retrieveRelevantToolResultsWithScores(
        conversationId: String,
        assistantId: String,
        query: String,
        maxUserTurnIndexExclusive: Int,
        limit: Int = 6,
        similarityThreshold: Float = 0.35f,
    ): List<Pair<ToolResultArchiveEntity, Float>> = withContext(Dispatchers.IO) {
        if (conversationId.isBlank()) return@withContext emptyList()
        if (assistantId.isBlank()) return@withContext emptyList()
        if (query.isBlank()) return@withContext emptyList()
        if (maxUserTurnIndexExclusive <= 0) return@withContext emptyList()

        val candidates = dao.getByConversationBeforeTurn(
            conversationId = conversationId,
            maxUserTurnIndexExclusive = maxUserTurnIndexExclusive,
        )
        if (candidates.isEmpty()) return@withContext emptyList()

        val queryEmbedding = runCatching {
            embeddingService.embed(
                text = query,
                assistantId = assistantId,
                source = AIRequestSource.TOOL_RESULT_RAG,
            )
        }.getOrNull() ?: return@withContext emptyList()

        val scored = candidates.mapNotNull { entity ->
            val embedding = getOrCreateEmbedding(entity, assistantId) ?: return@mapNotNull null
            val similarity = VectorEngine.cosineSimilarity(queryEmbedding, embedding)
            if (similarity >= similarityThreshold) entity to similarity else null
        }.sortedByDescending { it.second }
            .take(limit)

        if (scored.isNotEmpty()) {
            dao.touch(scored.map { it.first.id }, System.currentTimeMillis())
        }

        scored
    }

    private suspend fun getOrCreateEmbedding(
        entity: ToolResultArchiveEntity,
        assistantId: String,
    ): List<Float>? {
        val modelId = embeddingService.getEmbeddingModelId(assistantId)

        // Check cache first
        val cached = embeddingCacheDAO.getEmbedding(entity.id, MemoryType.TOOL_RESULT, modelId)
        if (cached != null) {
            return runCatching {
                JsonInstant.decodeFromString<List<Float>>(cached.embedding)
            }.getOrNull()
        }

        // Check existing embedding in entity (fallback)
        if (!entity.embedding.isNullOrBlank() && entity.embeddingModelId == modelId) {
            val decoded = runCatching {
                JsonInstant.decodeFromString<List<Float>>(entity.embedding)
            }.getOrNull()
            if (decoded != null) {
                embeddingCacheDAO.insertEmbedding(
                    EmbeddingCacheEntity(
                        memoryId = entity.id,
                        memoryType = MemoryType.TOOL_RESULT,
                        modelId = modelId,
                        embedding = entity.embedding,
                    )
                )
                return decoded
            }
        }

        // Generate new embedding
        val embedding = runCatching {
            embeddingService.embed(
                text = entity.extractText,
                assistantId = assistantId,
                source = AIRequestSource.TOOL_RESULT_EMBEDDING,
            )
        }.getOrNull() ?: return null

        val embeddingJson = JsonInstant.encodeToString(embedding)
        dao.updateEmbedding(entity.id, embeddingJson, modelId)
        embeddingCacheDAO.insertEmbedding(
            EmbeddingCacheEntity(
                memoryId = entity.id,
                memoryType = MemoryType.TOOL_RESULT,
                modelId = modelId,
                embedding = embeddingJson,
            )
        )
        return embedding
    }

    private fun buildExtractText(
        toolName: String,
        arguments: JsonElement,
        content: JsonElement,
    ): String {
        val raw = when (toolName) {
            "search_web" -> buildSearchWebExtract(arguments, content)
            "scrape_web" -> buildScrapeWebExtract(arguments, content)
            else -> buildGenericExtract(toolName, arguments, content)
        }
        return raw.trim().take(12_000)
    }

    private fun buildSearchWebExtract(arguments: JsonElement, content: JsonElement): String {
        val argsObject = arguments as? JsonObject
        val query = argsObject?.get("query")?.jsonPrimitiveOrNull?.contentOrNull

        val root = content as? JsonObject
        val items = root?.get("items") as? JsonArray

        return buildString {
            appendLine("tool: search_web")
            if (!query.isNullOrBlank()) appendLine("query: $query")
            if (items != null) {
                appendLine("results:")
                items.jsonArray.take(10).forEach { item ->
                    val obj = item as? JsonObject ?: return@forEach
                    val id = obj["id"]?.jsonPrimitiveOrNull?.contentOrNull
                    val title = obj["title"]?.jsonPrimitiveOrNull?.contentOrNull
                    val url = obj["url"]?.jsonPrimitiveOrNull?.contentOrNull
                    val text = obj["text"]?.jsonPrimitiveOrNull?.contentOrNull
                    append("- ")
                    if (!id.isNullOrBlank()) append("id=$id ")
                    if (!title.isNullOrBlank()) append("title=${title.take(160)} ")
                    if (!url.isNullOrBlank()) append("url=$url ")
                    if (!text.isNullOrBlank()) append("snippet=${text.take(280)}")
                    appendLine()
                }
            }
        }
    }

    private fun buildScrapeWebExtract(arguments: JsonElement, content: JsonElement): String {
        val argsObject = arguments as? JsonObject
        val url = argsObject?.get("url")?.jsonPrimitiveOrNull?.contentOrNull

        val root = content as? JsonObject
        val title = root?.get("title")?.jsonPrimitiveOrNull?.contentOrNull
        val text = root?.get("text")?.jsonPrimitiveOrNull?.contentOrNull

        return buildString {
            appendLine("tool: scrape_web")
            if (!url.isNullOrBlank()) appendLine("url: $url")
            if (!title.isNullOrBlank()) appendLine("title: ${title.take(200)}")
            if (!text.isNullOrBlank()) {
                appendLine("content:")
                appendLine(text.take(4000))
            }
        }
    }

    private fun buildGenericExtract(toolName: String, arguments: JsonElement, content: JsonElement): String {
        return buildString {
            appendLine("tool: $toolName")
            appendLine("arguments:")
            appendLine(arguments.toString().take(2000))
            appendLine("result:")
            appendLine(content.toString().take(8000))
        }
    }
}
