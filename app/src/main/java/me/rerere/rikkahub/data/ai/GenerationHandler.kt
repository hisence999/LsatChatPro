package me.rerere.rikkahub.data.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.core.merge
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.registry.ModelRegistry
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.handleMessageChunk
import me.rerere.ai.ui.limitContext
import me.rerere.ai.ui.truncate
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_LEARNING_MODE_PROMPT
import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.MessageTransformer
import me.rerere.rikkahub.data.ai.transformers.OutputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.onGenerationFinish
import me.rerere.rikkahub.data.ai.transformers.transforms
import me.rerere.rikkahub.data.ai.transformers.visualTransforms
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.utils.applyPlaceholders
import java.util.Locale

private const val TAG = "GenerationHandler"

@Serializable
sealed interface GenerationChunk {
    data class Messages(
        val messages: List<UIMessage>
    ) : GenerationChunk
}

class GenerationHandler(
    private val context: Context,
    private val providerManager: ProviderManager,
    private val json: Json,
    private val conversationRepo: ConversationRepository,
    private val aiLoggingManager: AILoggingManager,
) {
    fun generateText(
        settings: Settings,
        model: Model,
        messages: List<UIMessage>,
        inputTransformers: List<InputMessageTransformer> = emptyList(),
        outputTransformers: List<OutputMessageTransformer> = emptyList(),
        assistant: Assistant,
        memories: List<AssistantMemory>? = null,
        tools: List<Tool> = emptyList(),
        truncateIndex: Int = -1,
        maxSteps: Int = 256,
    ): Flow<GenerationChunk> = flow {
        val provider = model.findProvider(settings.providers) ?: error("Provider not found")
        val providerImpl = providerManager.getProviderByType(provider)

        var messages: List<UIMessage> = messages

        for (stepIndex in 0 until maxSteps) {
            Log.i(TAG, "streamText: start step #$stepIndex (${model.id})")

            val toolsInternal = tools

            generateInternal(
                assistant = assistant,
                settings = settings,
                messages = messages,
                onUpdateMessages = {
                    messages = it.transforms(
                        transformers = outputTransformers,
                        context = context,
                        model = model,
                        assistant = assistant
                    )
                    emit(
                        GenerationChunk.Messages(
                            messages.visualTransforms(
                                transformers = outputTransformers,
                                context = context,
                                model = model,
                                assistant = assistant
                            )
                        )
                    )
                },
                transformers = inputTransformers,
                model = model,
                providerImpl = providerImpl,
                provider = provider,
                tools = toolsInternal,
                memories = memories ?: emptyList(),
                truncateIndex = truncateIndex,
                stream = assistant.streamOutput
            )
            messages = messages.visualTransforms(
                transformers = outputTransformers,
                context = context,
                model = model,
                assistant = assistant
            )
            messages = messages.onGenerationFinish(
                transformers = outputTransformers,
                context = context,
                model = model,
                assistant = assistant
            )
            emit(GenerationChunk.Messages(messages))

            val toolCalls = messages.last().getToolCalls()
            if (toolCalls.isEmpty()) {
                // no tool calls, break
                break
            }
            // handle tool calls
            val results = arrayListOf<UIMessagePart.ToolResult>()
            toolCalls.forEach { toolCall ->
                runCatching {
                    val tool = toolsInternal.find { tool -> tool.name == toolCall.toolName }
                        ?: error("Tool ${toolCall.toolName} not found")
                    val args = json.parseToJsonElement(toolCall.arguments.ifBlank { "{}" })
                    Log.i(TAG, "generateText: executing tool ${tool.name} with args: $args")
                    val result = tool.execute(args)
                    results += UIMessagePart.ToolResult(
                        toolName = toolCall.toolName,
                        toolCallId = toolCall.toolCallId,
                        content = result,
                        arguments = args,
                        metadata = toolCall.metadata
                    )
                }.onFailure {
                    it.printStackTrace()
                    results += UIMessagePart.ToolResult(
                        toolName = toolCall.toolName,
                        toolCallId = toolCall.toolCallId,
                        metadata = toolCall.metadata,
                        content = buildJsonObject {
                            put(
                                "error",
                                JsonPrimitive(buildString {
                                    append("[${it.javaClass.name}] ${it.message}")
                                    append("\n${it.stackTraceToString()}")
                                })
                            )
                        },
                        arguments = runCatching {
                            json.parseToJsonElement(toolCall.arguments)
                        }.getOrElse { JsonObject(emptyMap()) }
                    )
                }
            }
            messages = messages + UIMessage(
                role = MessageRole.TOOL,
                parts = results
            )
            emit(
                GenerationChunk.Messages(
                    messages.transforms(
                        transformers = outputTransformers,
                        context = context,
                        model = model,
                        assistant = assistant
                    )
                )
            )
        }

    }.flowOn(Dispatchers.IO)

    suspend fun buildMessages(
        assistant: Assistant,
        settings: Settings,
        messages: List<UIMessage>,
        model: Model,
        tools: List<Tool>,
        memories: List<AssistantMemory>,
        truncateIndex: Int,
    ): List<UIMessage> {
        // Token estimator (rough estimate: 4 chars per token)
        fun estimateTokens(text: String) = text.length / 4
        fun estimateTokens(message: UIMessage) = estimateTokens(message.toText())

        val maxTokens = assistant.maxTokenUsage
        var currentTokens = 0

        // 1. Base System Prompt (System + Learning + Tools)
        val baseSystemPromptBuilder = StringBuilder()
        if (assistant.systemPrompt.isNotBlank()) {
            baseSystemPromptBuilder.append(assistant.systemPrompt)
        }
        if (assistant.learningMode) {
            baseSystemPromptBuilder.appendLine()
            baseSystemPromptBuilder.append(settings.learningModePrompt.ifEmpty { DEFAULT_LEARNING_MODE_PROMPT })
            baseSystemPromptBuilder.appendLine()
        }
        tools.forEach { tool ->
            baseSystemPromptBuilder.appendLine()
            baseSystemPromptBuilder.append(tool.systemPrompt(model, messages))
        }
        val baseSystemPrompt = baseSystemPromptBuilder.toString()
        currentTokens += estimateTokens(baseSystemPrompt)

        // 2. Prepare Candidates
        // Chat History (reverse order to prioritize recent)
        val chatHistoryCandidates = messages.truncate(truncateIndex).reversed()
        
        // Memories (Prepare effective memories including recent chats if enabled)
        val effectiveMemoriesCandidates = if (assistant.enableMemory) {
            val recentChatMemories = if (assistant.enableRecentChatsReference && messages.size <= 2) {
                val today = java.time.LocalDate.now()
                val recentConversations = conversationRepo.getRecentConversations(
                    assistantId = assistant.id,
                    limit = 3,
                ).filter { 
                    java.time.LocalDateTime.ofInstant(it.updateAt, java.time.ZoneId.systemDefault()).toLocalDate() == today 
                }
                recentConversations.map { conversation ->
                    AssistantMemory(
                        id = -1,
                        content = "Participated in conversation: ${conversation.title}",
                        type = 1,
                        timestamp = conversation.updateAt.toEpochMilli()
                    )
                }
            } else {
                emptyList()
            }
            (memories + recentChatMemories).distinctBy { it.content } // Avoid duplicates
        } else {
            emptyList()
        }

        // 3. Allocation Logic
        val selectedMessages = mutableListOf<UIMessage>()
        val selectedMemories = mutableListOf<AssistantMemory>()
        
        val remainingTokens = maxTokens - currentTokens
        if (remainingTokens <= 0) {
            // Edge case: System prompt too large. Just return minimums.
            Log.w(TAG, "buildMessages: System prompt exceeds max tokens!")
        }

        // Minimums
        val minChatHistory = 2.coerceAtMost(chatHistoryCandidates.size)
        val minMemories = if (assistant.enableMemory) 2.coerceAtMost(effectiveMemoriesCandidates.size) else 0

        // Add minimums first
        var usedTokens = 0
        
        // Add min chat history
        chatHistoryCandidates.take(minChatHistory).forEach {
            selectedMessages.add(it)
            usedTokens += estimateTokens(it)
        }
        
        // Add min memories
        effectiveMemoriesCandidates.take(minMemories).forEach {
            selectedMemories.add(it)
            usedTokens += estimateTokens(it.content)
        }

        // Distribute remaining tokens
        var availableTokens = remainingTokens - usedTokens
        if (availableTokens > 0) {
            val remainingChatHistory = chatHistoryCandidates.drop(minChatHistory)
            val remainingMemories = effectiveMemoriesCandidates.drop(minMemories)
            
            when (assistant.contextPriority) {
                me.rerere.rikkahub.data.model.ContextPriority.CHAT_HISTORY -> {
                    // Prioritize Chat History
                    for (msg in remainingChatHistory) {
                        val cost = estimateTokens(msg)
                        if (availableTokens >= cost) {
                            selectedMessages.add(msg)
                            availableTokens -= cost
                        } else break
                    }
                    for (mem in remainingMemories) {
                        val cost = estimateTokens(mem.content)
                        if (availableTokens >= cost) {
                            selectedMemories.add(mem)
                            availableTokens -= cost
                        }
                    }
                }
                me.rerere.rikkahub.data.model.ContextPriority.MEMORIES -> {
                    // Prioritize Memories
                    for (mem in remainingMemories) {
                        val cost = estimateTokens(mem.content)
                        if (availableTokens >= cost) {
                            selectedMemories.add(mem)
                            availableTokens -= cost
                        }
                    }
                    for (msg in remainingChatHistory) {
                        val cost = estimateTokens(msg)
                        if (availableTokens >= cost) {
                            selectedMessages.add(msg)
                            availableTokens -= cost
                        } else break
                    }
                }
                me.rerere.rikkahub.data.model.ContextPriority.BALANCED -> {
                    // Balanced (e.g. 50/50 split of remaining, or round-robin)
                    // Simple round-robin approach
                    var msgIndex = 0
                    var memIndex = 0
                    var addedSomething = true
                    while (addedSomething && availableTokens > 0) {
                        addedSomething = false
                        // Try add message
                        if (msgIndex < remainingChatHistory.size) {
                            val msg = remainingChatHistory[msgIndex]
                            val cost = estimateTokens(msg)
                            if (availableTokens >= cost) {
                                selectedMessages.add(msg)
                                availableTokens -= cost
                                msgIndex++
                                addedSomething = true
                            }
                        }
                        // Try add memory
                        if (memIndex < remainingMemories.size) {
                            val mem = remainingMemories[memIndex]
                            val cost = estimateTokens(mem.content)
                            if (availableTokens >= cost) {
                                selectedMemories.add(mem)
                                availableTokens -= cost
                                memIndex++
                                addedSomething = true
                            }
                        }
                    }
                }
            }
        }

        // 4. Construct Final List
        return buildList {
            val finalSystemPrompt = buildString {
                append(baseSystemPrompt)
                if (selectedMemories.isNotEmpty()) {
                    appendLine()
                    append(buildMemoryPrompt(model, selectedMemories))
                }
            }
            if (finalSystemPrompt.isNotBlank()) {
                add(UIMessage.system(finalSystemPrompt))
            }
            // Restore chat history order
            addAll(selectedMessages.sortedBy { messages.indexOf(it) })
        }
    }

    private suspend fun generateInternal(
        assistant: Assistant,
        settings: Settings,
        messages: List<UIMessage>,
        onUpdateMessages: suspend (List<UIMessage>) -> Unit,
        transformers: List<MessageTransformer>,
        model: Model,
        providerImpl: Provider<ProviderSetting>,
        provider: ProviderSetting,
        tools: List<Tool>,
        memories: List<AssistantMemory>,
        truncateIndex: Int,
        stream: Boolean
    ) {
        val internalMessages = buildMessages(
            assistant = assistant,
            settings = settings,
            messages = messages,
            model = model,
            tools = tools,
            memories = memories,
            truncateIndex = truncateIndex
        ).transforms(transformers, context, model, assistant)

        var messages: List<UIMessage> = messages
        val params = TextGenerationParams(
            model = model,
            temperature = assistant.temperature,
            topP = assistant.topP,
            maxTokens = assistant.maxTokens,
            tools = tools,
            thinkingBudget = assistant.thinkingBudget,
            customHeaders = buildList {
                addAll(assistant.customHeaders)
                addAll(model.customHeaders)
            },
            customBody = buildList {
                addAll(assistant.customBodies)
                addAll(model.customBodies)
            }
        )
        if (stream) {
            aiLoggingManager.addLog(AILogging.Generation(
                params = params,
                messages = messages,
                providerSetting = provider,
                stream = true
            ))
            providerImpl.streamText(
                providerSetting = provider,
                messages = internalMessages,
                params = params
            ).collect {
                messages = messages.handleMessageChunk(chunk = it, model = model)
                it.usage?.let { usage ->
                    messages = messages.mapIndexed { index, message ->
                        if (index == messages.lastIndex) {
                            message.copy(usage = message.usage.merge(usage))
                        } else {
                            message
                        }
                    }
                }
                onUpdateMessages(messages)
            }
        } else {
            aiLoggingManager.addLog(AILogging.Generation(
                params = params,
                messages = messages,
                providerSetting = provider,
                stream = false
            ))
            val chunk = providerImpl.generateText(
                providerSetting = provider,
                messages = internalMessages,
                params = params,
            )
            messages = messages.handleMessageChunk(chunk = chunk, model = model)
            chunk.usage?.let { usage ->
                messages = messages.mapIndexed { index, message ->
                    if (index == messages.lastIndex) {
                        message.copy(
                            usage = message.usage.merge(usage)
                        )
                    } else {
                        message
                    }
                }
            }
            onUpdateMessages(messages)
        }
    }



    private suspend fun buildMemoryPrompt(model: Model, memories: List<AssistantMemory>): String {
        Log.d(TAG, "buildMemoryPrompt: Injecting ${memories.size} memories into prompt")
        if (memories.isEmpty()) {
            Log.w(TAG, "buildMemoryPrompt: WARNING - No memories to inject!")
            return ""
        }

        val coreMemories = memories.filter { it.type == 0 } // CORE
        val episodicMemories = memories.filter { it.type == 1 } // EPISODIC
        
        return buildString {
            append("## Memories\n")
            append("These are memories that you can reference in the future conversations.\n")
            
            if (coreMemories.isNotEmpty()) {
                append("### Core Memories\n")
                coreMemories.forEach { memory ->
                    append("- [ID: ${memory.id}] ${memory.content}\n")
                }
            }

            if (episodicMemories.isNotEmpty()) {
                append("### Episodic Memories\n")
                
                val now = java.time.LocalDate.now()
                val yesterday = now.minusDays(1)
                val lastWeek = now.minusWeeks(1)
                
                val groupedEpisodes = episodicMemories.groupBy { memory ->
                    val date = java.time.Instant.ofEpochMilli(memory.timestamp)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                    
                    when {
                        date.isEqual(now) -> "Today"
                        date.isEqual(yesterday) -> "Yesterday"
                        date.isAfter(lastWeek) -> "This Week"
                        else -> "Older"
                    }
                }
                
                // Order: Today -> Yesterday -> This Week -> Older
                listOf("Today", "Yesterday", "This Week", "Older").forEach { group ->
                    val memoriesInGroup = groupedEpisodes[group]
                    if (!memoriesInGroup.isNullOrEmpty()) {
                        append("#### $group\n")
                        memoriesInGroup.sortedByDescending { it.timestamp }.forEach { memory ->
                            append("- ${memory.content}\n")
                        }
                    }
                }
            }
            

        }
    }

    fun translateText(
        settings: Settings,
        sourceText: String,
        targetLanguage: Locale,
        onStreamUpdate: ((String) -> Unit)? = null
    ): Flow<String> = flow {
        val model = settings.providers.findModelById(settings.translateModeId)
            ?: error("Translation model not found")
        val provider = model.findProvider(settings.providers)
            ?: error("Translation provider not found")

        val providerHandler = providerManager.getProviderByType(provider)

        if (!ModelRegistry.QWEN_MT.match(model.modelId)) {
            // Use regular translation with prompt
            val prompt = settings.translatePrompt.applyPlaceholders(
                "source_text" to sourceText,
                "target_lang" to targetLanguage.toString(),
            )

            var messages = listOf(UIMessage.user(prompt))
            var translatedText = ""

            providerHandler.streamText(
                providerSetting = provider,
                messages = messages,
                params = TextGenerationParams(
                    model = model,
                    temperature = 0.3f,
                ),
            ).collect { chunk ->
                messages = messages.handleMessageChunk(chunk)
                translatedText = messages.lastOrNull()?.toContentText() ?: ""

                if (translatedText.isNotBlank()) {
                    onStreamUpdate?.invoke(translatedText)
                    emit(translatedText)
                }
            }
        } else {
            // Use Qwen MT model with special translation options
            val messages = listOf(UIMessage.user(sourceText))
            val chunk = providerHandler.generateText(
                providerSetting = provider,
                messages = messages,
                params = TextGenerationParams(
                    model = model,
                    temperature = 0.3f,
                    topP = 0.95f,
                    customBody = listOf(
                        CustomBody(
                            key = "translation_options",
                            value = buildJsonObject {
                                put("source_lang", JsonPrimitive("auto"))
                                put(
                                    "target_lang",
                                    JsonPrimitive(targetLanguage.getDisplayLanguage(Locale.ENGLISH))
                                )
                            }
                        )
                    )
                ),
            )
            val translatedText = chunk.choices.firstOrNull()?.message?.toContentText() ?: ""

            if (translatedText.isNotBlank()) {
                onStreamUpdate?.invoke(translatedText)
                emit(translatedText)
            }
        }
    }.flowOn(Dispatchers.IO)
}
