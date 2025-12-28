package me.rerere.rikkahub.service

import android.Manifest
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.finishReasoning
import me.rerere.ai.ui.truncate
import me.rerere.common.android.Logging
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.ai.GenerationChunk
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.tools.LocalTools
import me.rerere.rikkahub.data.ai.transformers.Base64ImageToLocalFileTransformer
import me.rerere.rikkahub.data.ai.transformers.DocumentAsPromptTransformer
import me.rerere.rikkahub.data.ai.transformers.OcrTransformer
import me.rerere.rikkahub.data.ai.transformers.PlaceholderTransformer
import me.rerere.rikkahub.data.ai.transformers.RegexOutputTransformer
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.ai.transformers.ThinkTagTransformer
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.model.AssistantSearchMode
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.utils.JsonInstantPretty
import me.rerere.rikkahub.utils.applyPlaceholders
import me.rerere.rikkahub.utils.deleteChatFiles
import me.rerere.search.SearchService
import me.rerere.search.SearchServiceOptions
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

private const val TAG = "ChatService"

private val inputTransformers by lazy {
    listOf(
        PlaceholderTransformer,
        DocumentAsPromptTransformer,
        OcrTransformer,
    )
}

private val outputTransformers by lazy {
    listOf(
        ThinkTagTransformer,
        Base64ImageToLocalFileTransformer,
        RegexOutputTransformer,
    )
}

class ChatService(
    private val context: Application,
    private val appScope: AppScope,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val memoryRepository: MemoryRepository,
    private val generationHandler: GenerationHandler,
    private val templateTransformer: TemplateTransformer,
    private val providerManager: ProviderManager,
    private val localTools: LocalTools,
    val mcpManager: McpManager,
) {
    // 存储每个对话的状态
    private val conversations = ConcurrentHashMap<Uuid, MutableStateFlow<Conversation>>()

    private val pendingUiWelcomePhraseForAppContext = ConcurrentHashMap<Uuid, String>()

    // 记录哪些conversation有VM引用
    private val conversationReferences = ConcurrentHashMap<Uuid, Int>()

    // 记录哪些对话是临时对话（不持久化、不使用记忆）
    private val temporaryConversations = ConcurrentHashMap.newKeySet<Uuid>()

    private val liveUpdateNotifier = ChatLiveUpdateNotifier(context)
    private val liveUpdateSessionIds = ConcurrentHashMap<Uuid, Long>()
    private val liveUpdateStates = ConcurrentHashMap<Uuid, ChatLiveUpdateState>()
    private val liveUpdateLastNotifyAtMs = ConcurrentHashMap<Uuid, Long>()
    private val liveUpdateLastNotifiedState = ConcurrentHashMap<Uuid, ChatLiveUpdateState>()
    private val liveUpdateSmallIcons = ConcurrentHashMap<Uuid, Icon>()
    private val liveUpdateLargeIcons = ConcurrentHashMap<Uuid, Icon>()

    fun setPendingUiWelcomePhraseForAppContext(conversationId: Uuid, welcomePhrase: String) {
        val normalized = welcomePhrase.replace("\r", "").trim()
        if (normalized.isBlank()) return
        pendingUiWelcomePhraseForAppContext[conversationId] = normalized
    }

    // 存储每个对话的生成任务状态
    private val _generationJobs = MutableStateFlow<Map<Uuid, Job?>>(emptyMap())
    private val generationJobs: StateFlow<Map<Uuid, Job?>> = _generationJobs
        .asStateFlow()

    // 错误流
    private val _errorFlow = MutableSharedFlow<Throwable>()
    val errorFlow: SharedFlow<Throwable> = _errorFlow.asSharedFlow()

    // 生成完成流
    private val _generationDoneFlow = MutableSharedFlow<Uuid>()
    val generationDoneFlow: SharedFlow<Uuid> = _generationDoneFlow.asSharedFlow()

    // 前台状态管理
    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                _isForeground.value = true
                appScope.launch { cancelOngoingLiveUpdates() }
            }

            Lifecycle.Event.ON_STOP -> {
                _isForeground.value = false
                appScope.launch { notifyOngoingLiveUpdates(force = true) }
            }

            else -> {}
        }
    }

    init {
        // 添加生命周期观察者
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
    }

    fun cleanup() = runCatching {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        _generationJobs.value.values.forEach { it?.cancel() }
    }

    private fun shouldUseLiveUpdate(settings: Settings): Boolean {
        val display = settings.displaySetting
        return display.enableNotificationOnMessageGeneration && display.enableLiveUpdate
    }

    private fun startLiveUpdateSession(conversationId: Uuid): Long {
        ChatLiveUpdateDismissalTracker.clear(conversationId)
        val sessionId = System.currentTimeMillis()
        liveUpdateSessionIds[conversationId] = sessionId
        liveUpdateStates[conversationId] = ChatLiveUpdateState.INFERENCE
        liveUpdateLastNotifyAtMs.remove(conversationId)
        liveUpdateLastNotifiedState.remove(conversationId)
        liveUpdateSmallIcons.remove(conversationId)
        liveUpdateLargeIcons.remove(conversationId)
        liveUpdateSmallIcons[conversationId] = Icon.createWithResource(context, R.drawable.ic_launcher_monochrome)
        return sessionId
    }

    private fun clearLiveUpdateSession(conversationId: Uuid) {
        liveUpdateSessionIds.remove(conversationId)
        liveUpdateStates.remove(conversationId)
        liveUpdateLastNotifyAtMs.remove(conversationId)
        liveUpdateLastNotifiedState.remove(conversationId)
        liveUpdateSmallIcons.remove(conversationId)
        liveUpdateLargeIcons.remove(conversationId)
    }

    private fun cancelOngoingLiveUpdates() {
        liveUpdateStates.forEach { (conversationId, state) ->
            if (state.isOngoing()) {
                liveUpdateNotifier.cancel(conversationId)
            }
        }
    }

    private fun notifyOngoingLiveUpdates(force: Boolean) {
        val settings = settingsStore.settingsFlow.value
        if (!shouldUseLiveUpdate(settings)) return
        if (isForeground.value) return

        liveUpdateStates.forEach { (conversationId, state) ->
            notifyLiveUpdate(conversationId, state, settings = settings, force = force, error = null)
        }
    }

    private fun notifyLiveUpdate(
        conversationId: Uuid,
        state: ChatLiveUpdateState,
        settings: Settings,
        force: Boolean,
        error: Throwable?,
    ) {
        if (!shouldUseLiveUpdate(settings)) return
        if (isForeground.value) return

        val sessionId = liveUpdateSessionIds[conversationId] ?: return

        val now = System.currentTimeMillis()
        val lastAt = liveUpdateLastNotifyAtMs[conversationId] ?: 0L
        val lastState = liveUpdateLastNotifiedState[conversationId]

        val shouldNotify = force || lastState != state || now - lastAt >= 750L
        if (!shouldNotify) return

        liveUpdateLastNotifyAtMs[conversationId] = now
        liveUpdateLastNotifiedState[conversationId] = state

        val (contentText, bigText) = buildLiveUpdateTexts(conversationId, state, error)
        val title = buildLiveUpdateTitle(conversationId, settings)
        val smallIcon = liveUpdateSmallIcons[conversationId]
        val largeIcon = liveUpdateLargeIcons[conversationId]
        liveUpdateNotifier.notify(
            conversationId = conversationId,
            sessionId = sessionId,
            state = state,
            title = title,
            contentText = contentText,
            bigText = bigText,
            smallIcon = smallIcon,
            largeIcon = largeIcon,
        )
    }

    private fun buildLiveUpdateTitle(conversationId: Uuid, settings: Settings): String {
        val conversation = getConversationFlow(conversationId).value
        val assistantName = settings.getAssistantById(conversation.assistantId)?.name
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        return assistantName ?: context.getString(R.string.app_name)
    }

    private fun warmUpLiveUpdateIcon(conversationId: Uuid, settings: Settings) {
        if (!shouldUseLiveUpdate(settings)) return
        if (liveUpdateSmallIcons.containsKey(conversationId) && liveUpdateLargeIcons.containsKey(conversationId)) return

        val sessionId = liveUpdateSessionIds[conversationId] ?: return
        val conversation = getConversationFlow(conversationId).value
        val assistant = settings.getAssistantById(conversation.assistantId) ?: return

        appScope.launch(Dispatchers.IO) {
            val small = buildAssistantAvatarSmallIcon(assistant.name, assistant.avatar)
            val large = buildAssistantAvatarLargeIcon(assistant.name, assistant.avatar)
            if (small == null && large == null) return@launch

            if (liveUpdateSessionIds[conversationId] != sessionId) return@launch
            small?.let { liveUpdateSmallIcons[conversationId] = it }
            large?.let { liveUpdateLargeIcons[conversationId] = it }

            val state = liveUpdateStates[conversationId] ?: return@launch
            notifyLiveUpdate(
                conversationId = conversationId,
                state = state,
                settings = settings,
                force = true,
                error = null,
            )
        }
    }

    private fun buildAssistantAvatarSmallIcon(name: String, avatar: Avatar): Icon? {
        return when (avatar) {
            is Avatar.Resource -> {
                val drawable = runCatching {
                    ResourcesCompat.getDrawable(context.resources, avatar.id, context.theme)
                }.getOrNull()
                if (drawable == null || drawable is BitmapDrawable) {
                    null
                } else {
                    Icon.createWithResource(context, avatar.id)
                }
            }
            is Avatar.Emoji -> buildEmojiSmallIcon(avatar.content)
            is Avatar.Image -> null
            is Avatar.Dummy -> buildTextSmallIcon(name)
        }
    }

    private fun buildAssistantAvatarLargeIcon(name: String, avatar: Avatar): Icon? {
        return when (avatar) {
            is Avatar.Resource -> {
                val drawable = runCatching {
                    ResourcesCompat.getDrawable(context.resources, avatar.id, context.theme)
                }.getOrNull()
                if (drawable is BitmapDrawable) {
                    val scaled = scaleCenterCropSquare(drawable.bitmap, size = 160)
                    Icon.createWithAdaptiveBitmap(scaled)
                } else {
                    Icon.createWithResource(context, avatar.id)
                }
            }
            is Avatar.Emoji -> buildEmojiLargeIcon(avatar.content)
            is Avatar.Image -> buildImageLargeIcon(avatar.url)
            is Avatar.Dummy -> buildTextLargeIcon(name)
        }
    }

    private fun buildEmojiSmallIcon(emoji: String): Icon? {
        val normalized = emoji.trim()
        if (normalized.isBlank()) return null

        val size = 108
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = size * 0.56f
        }
        val fm = textPaint.fontMetrics
        val x = size / 2f
        val y = size / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(normalized, x, y, textPaint)

        return Icon.createWithBitmap(bitmap)
    }

    private fun buildTextSmallIcon(name: String): Icon? {
        val normalized = name.trim().takeIf { it.isNotBlank() }
            ?.firstOrNull()
            ?.uppercaseChar()
            ?.toString()
            ?: return null

        val size = 108
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = size * 0.62f
        }
        val fm = textPaint.fontMetrics
        val x = size / 2f
        val y = size / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(normalized, x, y, textPaint)

        return Icon.createWithBitmap(bitmap)
    }

    private fun buildEmojiLargeIcon(emoji: String): Icon? {
        val normalized = emoji.trim()
        if (normalized.isBlank()) return null

        val size = 160
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE0E0E0.toInt()
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bgPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = size * 0.56f
        }
        val fm = textPaint.fontMetrics
        val x = size / 2f
        val y = size / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(normalized, x, y, textPaint)

        return Icon.createWithAdaptiveBitmap(bitmap)
    }

    private fun buildTextLargeIcon(name: String): Icon? {
        val normalized = name.trim().takeIf { it.isNotBlank() }
            ?.firstOrNull()
            ?.uppercaseChar()
            ?.toString()
            ?: return null

        val size = 160
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE0E0E0.toInt()
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bgPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = size * 0.62f
        }
        val fm = textPaint.fontMetrics
        val x = size / 2f
        val y = size / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(normalized, x, y, textPaint)

        return Icon.createWithAdaptiveBitmap(bitmap)
    }

    private fun buildImageLargeIcon(url: String): Icon? {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") return null

        val bitmap = decodeSampledBitmapFromUri(uri, reqSize = 256) ?: return null
        val scaled = scaleCenterCropSquare(bitmap, size = 160)
        if (!bitmap.isRecycled) bitmap.recycle()
        return Icon.createWithAdaptiveBitmap(scaled)
    }

    private fun decodeSampledBitmapFromUri(uri: Uri, reqSize: Int): Bitmap? {
        fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
            val (height, width) = options.outHeight to options.outWidth
            var inSampleSize = 1
            if (height > reqHeight || width > reqWidth) {
                var halfHeight = height / 2
                var halfWidth = width / 2
                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize.coerceAtLeast(1)
        }

        return runCatching {
            val resolver = context.contentResolver

            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, boundsOptions)
            } ?: return@runCatching null

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(boundsOptions, reqSize, reqSize)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            }
        }.getOrNull()
    }

    private fun scaleCenterCropSquare(source: Bitmap, size: Int): Bitmap {
        val srcWidth = source.width.coerceAtLeast(1)
        val srcHeight = source.height.coerceAtLeast(1)
        val srcSize = minOf(srcWidth, srcHeight)

        val srcLeft = (srcWidth - srcSize) / 2
        val srcTop = (srcHeight - srcSize) / 2

        val cropped = Bitmap.createBitmap(source, srcLeft, srcTop, srcSize, srcSize)
        return if (cropped.width == size && cropped.height == size) {
            cropped
        } else {
            Bitmap.createScaledBitmap(cropped, size, size, true).also {
                if (cropped != source) cropped.recycle()
            }
        }
    }

    private fun buildLiveUpdateTexts(
        conversationId: Uuid,
        state: ChatLiveUpdateState,
        error: Throwable?,
    ): Pair<String?, String?> {
        val conversation = getConversationFlow(conversationId).value
        val lastUserText = conversation.currentMessages
            .lastOrNull { it.role == MessageRole.USER }
            ?.toContentText()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val lastAssistantText = conversation.currentMessages
            .lastOrNull { it.role == MessageRole.ASSISTANT }
            ?.toContentText()
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        fun String?.short(): String? = this?.take(80)?.takeIf { it.isNotBlank() }
        fun String?.long(): String? = this?.take(420)?.takeIf { it.isNotBlank() }

        return when (state) {
            ChatLiveUpdateState.INFERENCE -> lastUserText.short() to lastUserText.long()
            ChatLiveUpdateState.OUTPUT -> lastAssistantText.short() to lastAssistantText.long()
            ChatLiveUpdateState.DONE -> lastAssistantText.short() to lastAssistantText.long()
            ChatLiveUpdateState.ERROR -> {
                val errorSummary = error?.message?.trim()?.take(120)?.takeIf { it.isNotBlank() }
                    ?: error?.javaClass?.simpleName
                errorSummary to buildString {
                    if (!errorSummary.isNullOrBlank()) {
                        append(errorSummary)
                    }
                    if (!lastUserText.isNullOrBlank()) {
                        if (isNotEmpty()) append("\n\n")
                        append(lastUserText.take(420))
                    }
                }.take(600)
            }
        }
    }

    // 添加引用
    fun addConversationReference(conversationId: Uuid) {
        conversationReferences[conversationId] = conversationReferences.getOrDefault(conversationId, 0) + 1
        Log.d(
            TAG,
            "Added reference for $conversationId (current references: ${conversationReferences[conversationId] ?: 0})"
        )
    }

    // 移除引用
    fun removeConversationReference(conversationId: Uuid) {
        conversationReferences[conversationId]?.let { count ->
            if (count > 1) {
                conversationReferences[conversationId] = count - 1
            } else {
                conversationReferences.remove(conversationId)
            }
        }
        Log.d(
            TAG,
            "Removed reference for $conversationId (current references: ${conversationReferences[conversationId] ?: 0})"
        )
        appScope.launch {
            delay(500)
            checkAllConversationsReferences()
        }
    }

    // 检查是否有引用
    private fun hasReference(conversationId: Uuid): Boolean {
        return conversationReferences.containsKey(conversationId) || _generationJobs.value.containsKey(
            conversationId
        )
    }

    // 检查所有conversation的引用情况（生成结束后调用）
    fun checkAllConversationsReferences() {
        conversations.keys.forEach { conversationId ->
            if (!hasReference(conversationId)) {
                cleanupConversation(conversationId)
            }
        }
    }

    // 获取对话的StateFlow
    fun getConversationFlow(conversationId: Uuid): StateFlow<Conversation> {
        val settings = settingsStore.settingsFlow.value
        return conversations.getOrPut(conversationId) {
            MutableStateFlow(
                Conversation.ofId(
                    id = conversationId,
                    assistantId = settings.getCurrentAssistant().id
                )
            )
        }
    }

    // 获取生成任务状态流
    fun getGenerationJobStateFlow(conversationId: Uuid): Flow<Job?> {
        return generationJobs.map { jobs -> jobs[conversationId] }
    }

    fun getConversationJobs(): Flow<Map<Uuid, Job?>> {
        return generationJobs
    }

    private fun setGenerationJob(conversationId: Uuid, job: Job?) {
        if (job == null) {
            removeGenerationJob(conversationId)
            return
        }
        _generationJobs.value = _generationJobs.value.toMutableMap().apply {
            this[conversationId] = job
        }.toMap() // 确保创建新的不可变Map实例
    }

    private fun getGenerationJob(conversationId: Uuid): Job? {
        return _generationJobs.value[conversationId]
    }

    private fun removeGenerationJob(conversationId: Uuid) {
        _generationJobs.value = _generationJobs.value.toMutableMap().apply {
            remove(conversationId)
        }.toMap() // 确保创建新的不可变Map实例
    }

    // 初始化对话
    suspend fun initializeConversation(conversationId: Uuid) {
        val conversation = conversationRepo.getConversationById(conversationId)
        if (conversation != null) {
            updateConversation(conversationId, conversation)
            settingsStore.updateAssistant(conversation.assistantId)
        } else {
            // 新建对话, 并添加预设消息
            val currentSettings = settingsStore.settingsFlowRaw.first()
            val assistant = currentSettings.getCurrentAssistant()
            val newConversation = Conversation.ofId(
                id = conversationId,
                assistantId = assistant.id,
            ).updateCurrentMessages(assistant.presetMessages)
            updateConversation(conversationId, newConversation)
        }
    }

    // 发送消息
    fun sendMessage(conversationId: Uuid, content: List<UIMessagePart>, answer: Boolean=true, isTemporaryChat: Boolean = false) {
        // 标记为临时对话
        if (isTemporaryChat) {
            temporaryConversations.add(conversationId)
        }
        
        // 取消现有的生成任务
        getGenerationJob(conversationId)?.cancel()

        val job = appScope.launch {
            try {
                val currentConversation = getConversationFlow(conversationId).value

                // 添加消息到列表
                val newConversation = currentConversation.copy(
                    messageNodes = currentConversation.messageNodes + UIMessage(
                        role = MessageRole.USER,
                        parts = content,
                    ).toMessageNode(),
                )
                saveConversation(conversationId, newConversation)

                // 开始补全
                if(answer){
                    handleMessageComplete(conversationId)
                }

                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                e.printStackTrace()
                _errorFlow.emit(e)
            }
        }
        setGenerationJob(conversationId, job)
        job.invokeOnCompletion {
            setGenerationJob(conversationId, null)
            // 取消生成任务后，检查是否有其他任务在进行
            appScope.launch {
                delay(500)
                checkAllConversationsReferences()
            }
        }
    }

    // 重新生成消息
    fun regenerateAtMessage(
        conversationId: Uuid,
        message: UIMessage,
        regenerateAssistantMsg: Boolean = true
    ) {
        getGenerationJob(conversationId)?.cancel()

        val job = appScope.launch {
            try {
                val conversation = getConversationFlow(conversationId).value

                if (message.role == MessageRole.USER) {
                    // 如果是用户消息，则截止到当前消息
                    val node = conversation.getMessageNodeByMessage(message)
                    val indexAt = conversation.messageNodes.indexOf(node)
                    val newConversation = conversation.copy(
                        messageNodes = conversation.messageNodes.subList(0, indexAt + 1)
                    )
                    saveConversation(conversationId, newConversation)
                    handleMessageComplete(conversationId)
                } else {
                    if (regenerateAssistantMsg) {
                        val node = conversation.getMessageNodeByMessage(message)
                        val nodeIndex = conversation.messageNodes.indexOf(node)
                        handleMessageComplete(conversationId, messageRange = 0..<nodeIndex)
                    } else {
                        saveConversation(conversationId, conversation)
                    }
                }

                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                _errorFlow.emit(e)
            }
        }

        setGenerationJob(conversationId, job)
        job.invokeOnCompletion {
            setGenerationJob(conversationId, null)
            // 取消生成任务后，检查是否有其他任务在进行
            appScope.launch {
                delay(500)
                checkAllConversationsReferences()
            }
        }
    }

    // 处理消息补全
    private suspend fun handleMessageComplete(
        conversationId: Uuid,
        messageRange: ClosedRange<Int>? = null
    ) {
        val settings = settingsStore.settingsFlow.first()
        val model = settings.getCurrentChatModel() ?: return
        val useLiveUpdate = shouldUseLiveUpdate(settings)

        // Track generation start time for tokens/sec calculation
        // Set on first token arrival to exclude TTFT (time to first token) from the calculation
        var firstTokenTime: Long? = null

        var shouldConsumeWelcomePhraseAppContext = false
        runCatching {
            val conversation = getConversationFlow(conversationId).value

            // reset suggestions
            updateConversation(conversationId, conversation.copy(chatSuggestions = emptyList()))

            // Check if model supports tools when external tools are configured
            val assistant = settings.getCurrentAssistant()
            val hasExternalTools = (assistant.searchMode !is AssistantSearchMode.Off) || mcpManager.getAllAvailableTools().isNotEmpty()
            if (!model.abilities.contains(ModelAbility.TOOL)) {
                if (hasExternalTools) {
                    _errorFlow.emit(IllegalStateException(context.getString(R.string.tools_warning)))
                }
            }

            // check invalid messages
            checkInvalidMessages(conversationId)

            val baseMessages = conversation.currentMessages.let {
                if (messageRange != null) {
                    it.subList(messageRange.start, messageRange.endInclusive + 1)
                } else {
                    it
                }
            }
            val welcomePhraseForAppContext = pendingUiWelcomePhraseForAppContext[conversationId]
            val appContextTransformer = if (!welcomePhraseForAppContext.isNullOrBlank() && baseMessages.any { it.role == MessageRole.USER }) {
                shouldConsumeWelcomePhraseAppContext = true
                object : me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer {
                    override suspend fun transform(
                        ctx: me.rerere.rikkahub.data.ai.transformers.TransformerContext,
                        messages: List<UIMessage>,
                    ): List<UIMessage> {
                        return injectWelcomePhraseIntoFirstUserMessage(messages, welcomePhraseForAppContext).messages
                    }
                }
            } else {
                null
            }

            if (useLiveUpdate) {
                startLiveUpdateSession(conversationId)
                warmUpLiveUpdateIcon(conversationId, settings)
                notifyLiveUpdate(
                    conversationId = conversationId,
                    state = ChatLiveUpdateState.INFERENCE,
                    settings = settings,
                    force = true,
                    error = null,
                )
            }

            // start generating
            generationHandler.generateText(
                settings = settings,
                model = model,
                messages = baseMessages,
                assistant = settings.getCurrentAssistant(),
                memories = if (settings.getCurrentAssistant().enableMemory && !temporaryConversations.contains(conversationId)) {
                    val assistant = settings.getCurrentAssistant()
                    if (assistant.useRagMemoryRetrieval) {
                        // RAG mode: retrieve relevant memories based on context
                        val lastUserMessage = conversation.currentMessages.lastOrNull { it.role == MessageRole.USER }?.toText() ?: ""
                        
                        if (settings.enableRagLogging) {
                            Log.d("RAG", "Query: $lastUserMessage")
                        }

                        if (lastUserMessage.isNotBlank()) {
                            val results = memoryRepository.retrieveRelevantMemories(
                                assistantId = settings.assistantId.toString(),
                                query = lastUserMessage,
                                limit = 50, // Hardcoded high limit for dynamic context
                                similarityThreshold = assistant.ragSimilarityThreshold,
                                includeCore = assistant.ragIncludeCore,
                                includeEpisodes = assistant.ragIncludeEpisodes
                            )
                            if (settings.enableRagLogging) {
                                Log.d("RAG", "Retrieved ${results.size} memories")
                                results.forEach { Log.d("RAG", " - [${it.type}] ${it.content.take(50)}...") }
                            }
                            results
                        } else {
                            if (settings.enableRagLogging) Log.d("RAG", "Empty query, using all memories")
                            memoryRepository.getMemoriesOfAssistant(settings.assistantId.toString())
                        }
                    } else {
                        // Simple mode: inject all memories
                        memoryRepository.getMemoriesOfAssistant(settings.assistantId.toString())
                    }
                } else {
                    emptyList()
                },
                inputTransformers = buildList {
                    appContextTransformer?.let(::add)
                    addAll(inputTransformers)
                    add(templateTransformer)
                },
                outputTransformers = outputTransformers,
                tools = buildList {
                    // Check if we should use built-in search instead of external tools
                    // Built-in search is used when:
                    // 1. preferBuiltInSearch is enabled on assistant
                    // 2. Model supports built-in search (Gemini series with search grounding)
                    val modelSupportsBuiltIn = model.tools.isNotEmpty() || 
                        me.rerere.ai.registry.ModelRegistry.GEMINI_SERIES.match(model.modelId)
                    val useBuiltInSearch = assistant.preferBuiltInSearch && modelSupportsBuiltIn
                    
                    // Use assistant's searchMode for external tools (only if NOT using built-in)
                    when (val searchMode = assistant.searchMode) {
                        is AssistantSearchMode.Provider -> {
                            // Only add external search tools if NOT using built-in search
                            if (!useBuiltInSearch) {
                                addAll(createSearchTool(settings, searchMode.index))
                            }
                        }
                        is AssistantSearchMode.BuiltIn -> {
                            // Built-in search is handled via model.tools, no external tool needed
                        }
                        is AssistantSearchMode.Off -> {
                            // No search tools
                        }
                    }
                    addAll(localTools.getTools(
                        options = settings.getCurrentAssistant().localTools,
                        assistantId = settings.getCurrentAssistant().id,
                        conversationId = conversation.id
                    ))
                    mcpManager.getAllAvailableTools().forEach { tool ->
                        add(
                            Tool(
                                name = tool.name,
                                description = tool.description ?: "",
                                parameters = { tool.inputSchema },
                                execute = {
                                    mcpManager.callTool(tool.name, it.jsonObject)
                                },
                            )
                        )
                    }
                },
                truncateIndex = conversation.truncateIndex,
                enabledModeIds = conversation.enabledModeIds,
            ).onCompletion { cause ->
                // Calculate generation duration from first token (excludes TTFT)
                val generationDurationMs = firstTokenTime?.let { System.currentTimeMillis() - it }

                // 可能被取消了，或者意外结束，兜底更新
                val currentConversation = getConversationFlow(conversationId).value
                val updatedConversation = currentConversation.copy(
                    messageNodes = currentConversation.messageNodes.mapIndexed { index, node ->
                        val isLastNode = index == currentConversation.messageNodes.lastIndex
                        node.copy(messages = node.messages.map { msg ->
                            val finishedMsg = msg.finishReasoning()
                            // Add generation duration to the last assistant message
                            if (isLastNode && finishedMsg.role == MessageRole.ASSISTANT && finishedMsg.generationDurationMs == null) {
                                // Debug usage
                                if (finishedMsg.usage == null) {
                                    Log.w(TAG, "Assistant message usage is null in onCompletion")
                                }
                                finishedMsg.copy(generationDurationMs = generationDurationMs)
                            } else {
                                finishedMsg
                            }
                        })
                    },
                    updateAt = Instant.now()
                )
                updateConversation(conversationId, updatedConversation)

                val generationFinishedNormally = cause == null
                if (generationFinishedNormally) {
                    liveUpdateStates.remove(conversationId)

                    // Show notification if app is not in foreground
                    if (!isForeground.value && settings.displaySetting.enableNotificationOnMessageGeneration) {
                        if (useLiveUpdate) {
                            notifyLiveUpdate(
                                conversationId = conversationId,
                                state = ChatLiveUpdateState.DONE,
                                settings = settings,
                                force = true,
                                error = null,
                            )
                        } else {
                            sendGenerationDoneNotification(conversationId)
                        }
                    }
                }
            }.collect { chunk ->
                // Set first token time on first chunk arrival (excludes TTFT from tok/s)
                if (firstTokenTime == null) {
                    firstTokenTime = System.currentTimeMillis()
                }
                
                when (chunk) {
                    is GenerationChunk.Messages -> {
                        val updatedConversation = getConversationFlow(conversationId).value
                            .updateCurrentMessages(chunk.messages)
                        updateConversation(conversationId, updatedConversation)

                        if (useLiveUpdate) {
                            val previousState = liveUpdateStates.put(conversationId, ChatLiveUpdateState.OUTPUT)
                            notifyLiveUpdate(
                                conversationId = conversationId,
                                state = ChatLiveUpdateState.OUTPUT,
                                settings = settings,
                                force = previousState != ChatLiveUpdateState.OUTPUT,
                                error = null,
                            )
                        }
                    }
                }
            }
        }.onFailure {
            if (useLiveUpdate) {
                liveUpdateStates.remove(conversationId)
                if (it is CancellationException) {
                    liveUpdateNotifier.cancel(conversationId)
                } else {
                    notifyLiveUpdate(
                        conversationId = conversationId,
                        state = ChatLiveUpdateState.ERROR,
                        settings = settings,
                        force = true,
                        error = it,
                    )
                }
                clearLiveUpdateSession(conversationId)
            }
            it.printStackTrace()
            _errorFlow.emit(it)
            Logging.log(TAG, "handleMessageComplete: $it")
            Logging.log(TAG, it.stackTraceToString())
        }.onSuccess {
            if (useLiveUpdate) {
                clearLiveUpdateSession(conversationId)
            }
            if (shouldConsumeWelcomePhraseAppContext) {
                pendingUiWelcomePhraseForAppContext.remove(conversationId)
            }
            val finalConversation = getConversationFlow(conversationId).value
            saveConversation(conversationId, finalConversation)

            addConversationReference(conversationId) // 添加引用
            appScope.launch {
                coroutineScope {
                    launch {
                        // Fetch fresh conversation from DB to ensure we have the latest state
                        // This matches the manual regeneration pattern which works correctly
                        val freshConversation = conversationRepo.getConversationById(conversationId)
                        if (freshConversation != null) {
                            generateTitle(conversationId, freshConversation)
                        } else {
                            Log.w(TAG, "generateTitle: conversation not found in DB for $conversationId")
                        }
                    }
                    launch { generateSuggestion(conversationId, finalConversation) }
                }
            }.invokeOnCompletion {
                removeConversationReference(conversationId) // 移除引用
            }
        }
    }

    // 创建搜索工具
    private fun createSearchTool(settings: Settings, providerIndex: Int? = null): Set<Tool> {
        // Use the provided providerIndex (from assistant's searchMode) or fall back to global selection
        val effectiveIndex = providerIndex ?: settings.searchServiceSelected
        return buildSet {
            add(
                Tool(
                    name = "search_web",
                    description = "search web for latest information",
                    parameters = {
                        val options = settings.searchServices.getOrElse(
                            index = effectiveIndex,
                            defaultValue = { SearchServiceOptions.DEFAULT })
                        val service = SearchService.getService(options)
                        service.parameters
                    },
                    execute = {
                        val options = settings.searchServices.getOrElse(
                            index = effectiveIndex,
                            defaultValue = { SearchServiceOptions.DEFAULT })
                        val service = SearchService.getService(options)
                        val result = service.search(
                            params = it.jsonObject,
                            commonOptions = settings.searchCommonOptions,
                            serviceOptions = options,
                        )
                        val results =
                            JsonInstantPretty.encodeToJsonElement(result.getOrThrow()).jsonObject.let { json ->
                                val map = json.toMutableMap()
                                val items = map["items"]
                                if (items is JsonArray) {
                                    map["items"] = JsonArray(items.mapIndexed { index, item ->
                                        if (item is JsonObject) {
                                            JsonObject(item.toMutableMap().apply {
                                                put("id", JsonPrimitive(Uuid.random().toString().take(6)))
                                                put("index", JsonPrimitive(index + 1))
                                            })
                                        } else {
                                            item
                                        }
                                    })
                                }
                                JsonObject(map)
                            }
                        results
                    }, systemPrompt = { model, messages ->
                        if (model.tools.isNotEmpty()) return@Tool ""
                        val hasToolCall =
                            messages.any { it.getToolCalls().any { toolCall -> toolCall.toolName == "search_web" } }
                        val prompt = StringBuilder()
                        prompt.append(
                            """
                    ## tool: search_web

                    ### usage
                    - You can use the search_web tool to search the internet for the latest news or to confirm some facts.
                    - You can perform multiple search if needed
                    - Generate keywords based on the user's question
                    - Today is {{cur_date}}
                    """.trimIndent()
                        )
                        if (hasToolCall) {
                            prompt.append(
                                """
                        ### result example
                        ```json
                        {
                            "items": [
                                {
                                    "id": "random id in 6 characters",
                                    "title": "Title",
                                    "url": "https://example.com",
                                    "text": "Some relevant snippets"
                                }
                            ]
                        }
                        ```

                        ### citation
                        After using the search tool, when replying to users, you need to add a reference format to the referenced search terms in the content.
                        When citing facts or data from search results, you need to add a citation marker after the sentence: `[citation,domain](id of the search result)`.

                        For example:
                        ```
                        The capital of France is Paris. [citation,example.com](id of the search result)

                        The population of Paris is about 2.1 million. [citation,example.com](id of the search result) [citation,example2.com](id of the search result)
                        ```

                        If no search results are cited, you do not need to add a citation marker.
                        """.trimIndent()
                            )
                        }
                        prompt.toString()
                    }
                )
            )

            val options = settings.searchServices.getOrElse(
                index = effectiveIndex,
                defaultValue = { SearchServiceOptions.DEFAULT })
            val service = SearchService.getService(options)
            if (service.scrapingParameters != null) {
                add(
                    Tool(
                        name = "scrape_web",
                        description = "scrape web for content",
                        parameters = {
                            val options = settings.searchServices.getOrElse(
                                index = effectiveIndex,
                                defaultValue = { SearchServiceOptions.DEFAULT })
                            val service = SearchService.getService(options)
                            service.scrapingParameters
                        },
                        execute = {
                            val options = settings.searchServices.getOrElse(
                                index = effectiveIndex,
                                defaultValue = { SearchServiceOptions.DEFAULT })
                            val service = SearchService.getService(options)
                            val result = service.scrape(
                                params = it.jsonObject,
                                commonOptions = settings.searchCommonOptions,
                                serviceOptions = options,
                            )
                            JsonInstantPretty.encodeToJsonElement(result.getOrThrow()).jsonObject
                        },
                        systemPrompt = { model, messages ->
                            return@Tool """
                            ## tool: scrape_web

                            ### usage
                            - You can use the scrape_web tool to scrape url for detailed content.
                            - You can perform multiple scrape if needed.
                            - For common problems, try not to use this tool unless the user requests it.
                        """.trimIndent()
                        }
                    ))
            }
        }
    }

    // 检查无效消息
    private fun checkInvalidMessages(conversationId: Uuid) {
        val conversation = getConversationFlow(conversationId).value
        var messagesNodes = conversation.messageNodes

        // Step 1: 移除空消息节点 (do this FIRST to prevent exceptions)
        messagesNodes = messagesNodes.filter { it.messages.isNotEmpty() }

        // Step 2: 更新无效的selectIndex (do this BEFORE accessing currentMessage)
        messagesNodes = messagesNodes.map { node ->
            if (node.selectIndex !in node.messages.indices) {
                node.copy(selectIndex = 0)
            } else {
                node
            }
        }

        // Step 3: 移除无效tool call (now safe to access currentMessage)
        messagesNodes = messagesNodes.mapIndexed { index, node ->
            val next = if (index < messagesNodes.size - 1) messagesNodes[index + 1] else null
            if (node.currentMessage.hasPart<UIMessagePart.ToolCall>()) {
                if (next?.currentMessage?.hasPart<UIMessagePart.ToolResult>() != true) {
                    return@mapIndexed node.copy(
                        messages = node.messages.filter { it.id != node.currentMessage.id },
                        selectIndex = node.selectIndex - 1
                    )
                }
            }
            node
        }

        // Step 4: Final cleanup after tool call removal
        messagesNodes = messagesNodes.filter { it.messages.isNotEmpty() }
        messagesNodes = messagesNodes.map { node ->
            if (node.selectIndex !in node.messages.indices) {
                node.copy(selectIndex = 0.coerceAtMost(node.messages.lastIndex))
            } else {
                node
            }
        }

        updateConversation(conversationId, conversation.copy(messageNodes = messagesNodes))
    }

    // 生成标题
    suspend fun generateTitle(
        conversationId: Uuid,
        conversation: Conversation,
        force: Boolean = false
    ) {
        val shouldGenerate = when {
            force -> true
            conversation.title.isBlank() -> true
            else -> false
        }
        if (!shouldGenerate) {
            Log.d(TAG, "generateTitle: skipped (title='${conversation.title.take(20)}', force=$force)")
            return
        }
        Log.d(TAG, "generateTitle: starting for conversation ${conversation.id}, messages=${conversation.messageNodes.size}")

        runCatching {
            val settings = settingsStore.settingsFlow.first()
            val model =
                settings.findModelById(settings.titleModelId) ?: settings.getCurrentChatModel()
            if (model == null) {
                Log.w(TAG, "generateTitle: No model found for titleModelId=${settings.titleModelId} and no current chat model")
                return
            }
            val provider = model.findProvider(settings.providers)
            if (provider == null) {
                Log.w(TAG, "generateTitle: No provider found for model ${model.displayName}")
                return
            }

            val providerHandler = providerManager.getProviderByType(provider)
            
            // Check if we have content to generate a title from
            val contentForTitle = conversation.currentMessages.truncate(conversation.truncateIndex)
                .joinToString("\n\n") { it.summaryAsText() }
            
            if (contentForTitle.isBlank()) {
                Log.w(TAG, "generateTitle: No content available for title generation (messages=${conversation.messageNodes.size}, truncateIndex=${conversation.truncateIndex})")
                return
            }
            
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.user(
                        prompt = settings.titlePrompt.applyPlaceholders(
                            "locale" to Locale.getDefault().displayName,
                            "content" to contentForTitle)
                    ),
                ),
                params = TextGenerationParams(
                    model = model, temperature = 0.3f, thinkingBudget = 0
                ),
            )

            // 生成完，conversation可能不是最新了，因此需要重新获取
            conversationRepo.getConversationById(conversation.id)?.let {
                saveConversation(
                    conversationId,
                    it.copy(title = result.choices[0].message?.toContentText()?.trim() ?: "")
                )
            }
        }.onFailure {
            Log.e(TAG, "generateTitle failed: ${it.message}", it)
        }
    }

    // 生成建议
    suspend fun generateSuggestion(conversationId: Uuid, conversation: Conversation) {
        runCatching {
            val settings = settingsStore.settingsFlow.first()
            val model = settings.findModelById(settings.suggestionModelId) ?: return
            val provider = model.findProvider(settings.providers) ?: return

            updateConversation(
                conversationId,
                getConversationFlow(conversationId).value.copy(chatSuggestions = emptyList())
            )

            val providerHandler = providerManager.getProviderByType(provider)
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.user(
                        settings.suggestionPrompt.applyPlaceholders(
                            "locale" to Locale.getDefault().displayName,
                            "content" to conversation.currentMessages.truncate(conversation.truncateIndex)
                                .takeLast(8).joinToString("\n\n") { it.summaryAsText() }),
                    )
                ),
                params = TextGenerationParams(
                    model = model,
                    temperature = 1.0f,
                    thinkingBudget = 0,
                ),
            )
            val suggestions =
                result.choices[0].message?.toContentText()?.split("\n")?.map { it.trim() }
                    ?.filter { it.isNotBlank() } ?: emptyList()

            // Fetch fresh conversation from DB to avoid overwriting concurrent updates (e.g., title generation)
            conversationRepo.getConversationById(conversationId)?.let { freshConversation ->
                saveConversation(
                    conversationId,
                    freshConversation.copy(chatSuggestions = suggestions)
                )
            }
        }.onFailure {
            it.printStackTrace()
        }
    }

    private val conversationDeletionJobs = java.util.concurrent.ConcurrentHashMap<Uuid, Job>()
    private val recentlyDeletedConversations = java.util.concurrent.ConcurrentHashMap<Uuid, Conversation>()

    // Track recently restored conversations for fade-in animation
    private val _recentlyRestoredIds = kotlinx.coroutines.flow.MutableStateFlow<Set<Uuid>>(emptySet())
    val recentlyRestoredIds: kotlinx.coroutines.flow.StateFlow<Set<Uuid>> = _recentlyRestoredIds

    fun deleteConversation(conversation: Conversation) {
        appScope.launch {
            val conversationFull = conversationRepo.getConversationById(conversation.id) ?: return@launch

            // Cancel any pending deletion for this conversation
            conversationDeletionJobs[conversation.id]?.cancel()

            // Soft delete (DB only, preserve files)
            conversationRepo.deleteConversation(conversationFull, deleteFiles = false)
            recentlyDeletedConversations[conversation.id] = conversationFull

            // Schedule file deletion
            val job = appScope.launch {
                kotlinx.coroutines.delay(4000)
                context.deleteChatFiles(conversationFull.files)
                conversationDeletionJobs.remove(conversation.id)
                recentlyDeletedConversations.remove(conversation.id)
            }
            conversationDeletionJobs[conversation.id] = job
        }
    }

    fun undoDeleteConversation(conversationId: Uuid) {
        conversationDeletionJobs[conversationId]?.cancel()
        conversationDeletionJobs.remove(conversationId)

        val conversation = recentlyDeletedConversations[conversationId]
        if (conversation != null) {
            appScope.launch {
                conversationRepo.insertConversation(conversation)
                recentlyDeletedConversations.remove(conversationId)

                // Track for fade-in animation
                _recentlyRestoredIds.value = _recentlyRestoredIds.value + conversationId

                // Remove from tracking after animation completes
                kotlinx.coroutines.delay(1000)
                _recentlyRestoredIds.value = _recentlyRestoredIds.value - conversationId
            }
        }
    }


    // 发送生成完成通知
    private fun sendGenerationDoneNotification(conversationId: Uuid) {
        val conversation = getConversationFlow(conversationId).value
        val notification =
            NotificationCompat.Builder(context, CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(context.getString(R.string.notification_chat_done_title))
                .setContentText(conversation.currentMessages.lastOrNull()?.toContentText()?.take(50) ?: "")
                .setSmallIcon(R.drawable.ic_notification)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(getPendingIntent(context, conversationId))

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(1, notification.build())
    }

    private fun getPendingIntent(context: Context, conversationId: Uuid): PendingIntent {
        val intent = Intent(context, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("conversationId", conversationId.toString())
        }
        return PendingIntent.getActivity(
            context,
            conversationId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // 更新对话
    private fun updateConversation(conversationId: Uuid, conversation: Conversation) {
        if (conversation.id != conversationId) return
        checkFilesDelete(conversation, getConversationFlow(conversationId).value)
        conversations.getOrPut(conversationId) { MutableStateFlow(conversation) }.value =
            conversation
    }

    // 检查文件删除
    private fun checkFilesDelete(newConversation: Conversation, oldConversation: Conversation) {
        val newFiles = newConversation.files
        val oldFiles = oldConversation.files
        val deletedFiles = oldFiles.filter { file ->
            newFiles.none { it == file }
        }
        if (deletedFiles.isNotEmpty()) {
            context.deleteChatFiles(deletedFiles)
            Log.w(TAG, "checkFilesDelete: $deletedFiles")
        }
    }

    // 保存对话
    suspend fun saveConversation(conversationId: Uuid, conversation: Conversation) {
        // 临时对话不持久化到数据库
        if (temporaryConversations.contains(conversationId)) {
            updateConversation(conversationId, conversation)
            return
        }

        val updatedConversation = conversation.copy()
        updateConversation(conversationId, updatedConversation)

        // 空对话不落库，但仍需要更新内存态（例如：首次发送消息前启用 modes）
        if (updatedConversation.title.isBlank() && updatedConversation.messageNodes.isEmpty()) return

        try {
            if (conversationRepo.getConversationById(conversation.id) == null) {
                conversationRepo.insertConversation(updatedConversation)
            } else {
                conversationRepo.updateConversation(updatedConversation)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 翻译消息
    fun translateMessage(
        conversationId: Uuid,
        message: UIMessage,
        targetLanguage: Locale
    ) {
        appScope.launch(Dispatchers.IO) {
            try {
                val settings = settingsStore.settingsFlow.first()

                val messageText = message.parts.filterIsInstance<UIMessagePart.Text>()
                    .joinToString("\n\n") { it.text }
                    .trim()

                if (messageText.isBlank()) return@launch

                // Set loading state for translation
                val loadingText = context.getString(R.string.translating)
                updateTranslationField(conversationId, message.id, loadingText)

                generationHandler.translateText(
                    settings = settings,
                    sourceText = messageText,
                    targetLanguage = targetLanguage
                ) { translatedText ->
                    // Update translation field in real-time
                    updateTranslationField(conversationId, message.id, translatedText)
                }.collect { /* Final translation already handled in onStreamUpdate */ }

                // Save the conversation after translation is complete
                saveConversation(conversationId, getConversationFlow(conversationId).value)
            } catch (e: Exception) {
                // Clear translation field on error
                clearTranslationField(conversationId, message.id)
                _errorFlow.emit(e)
            }
        }
    }

    private fun updateTranslationField(
        conversationId: Uuid,
        messageId: Uuid,
        translationText: String
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) {
                val updatedMessages = node.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(translation = translationText)
                    } else {
                        msg
                    }
                }
                node.copy(messages = updatedMessages)
            } else {
                node
            }
        }

        updateConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    fun clearTranslationField(conversationId: Uuid, messageId: Uuid) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) {
                val updatedMessages = node.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(translation = null)
                    } else {
                        msg
                    }
                }
                node.copy(messages = updatedMessages)
            } else {
                node
            }
        }

        updateConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    // 清理对话相关资源
    fun cleanupConversation(conversationId: Uuid) {
        getGenerationJob(conversationId)?.cancel()
        removeGenerationJob(conversationId)
        conversations.remove(conversationId)

        Log.i(
            TAG,
            "cleanupConversation: removed $conversationId (current references: ${conversationReferences.size}, generation jobs: ${_generationJobs.value.size})"
        )
    }
}
