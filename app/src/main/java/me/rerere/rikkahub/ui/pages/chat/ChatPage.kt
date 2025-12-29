package me.rerere.rikkahub.ui.pages.chat

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HistoryToggleOff

import me.rerere.rikkahub.ui.components.ui.ToastType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.rikkahub.service.selectWelcomePhrase
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.RpStyleRule
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.components.ai.ChatInput
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.rememberChatInputState
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.utils.base64Decode
import me.rerere.rikkahub.utils.createChatFilesByContents
import me.rerere.rikkahub.utils.getFileMimeType
import me.rerere.rikkahub.utils.navigateToChatPage
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.layout

private enum class EmptyChatOverlay {
    None,
    Welcome,
    Temporary,
}

private val EmptyChatOverlayBottomPaddingFallback = 140.dp
private val EmptyChatOverlayContentYOffset = (-16).dp

private data class Grapheme(
    val text: String,
    val range: IntRange,
)

private fun splitIntoGraphemes(text: String): List<Grapheme> {
    val graphemes = mutableListOf<Grapheme>()
    val iterator = java.text.BreakIterator.getCharacterInstance()
    iterator.setText(text)
    var start = iterator.first()
    var end = iterator.next()
    while (end != java.text.BreakIterator.DONE) {
        graphemes.add(Grapheme(text.substring(start, end), start until end))
        start = end
        end = iterator.next()
    }
    return graphemes
}

private data class RpStyledRange(
    val range: IntRange,
    val color: Color,
)

private data class RpStyledText(
    val text: String,
    val ranges: List<RpStyledRange>,
) {
    fun colorAt(index: Int): Color? = ranges.firstOrNull { index in it.range }?.color
}

private val RP_PREFIX_PATTERNS = setOf("#", "##", "###", "####", "#####", "######", ">")

private fun applyRpStyleRules(text: String, rpStyleRules: List<RpStyleRule>): RpStyledText {
    val enabledRules = rpStyleRules
        .asSequence()
        .filter { it.enabled }
        .filter { it.pattern.isNotBlank() }
        .filter { it.pattern !in RP_PREFIX_PATTERNS }
        .toList()

    if (enabledRules.isEmpty()) return RpStyledText(text = text, ranges = emptyList())

    data class Match(val range: IntRange, val content: String, val color: Color)
    val allMatches = mutableListOf<Match>()

    enabledRules.forEach { rule ->
        val color = runCatching {
            Color(android.graphics.Color.parseColor(rule.colorHex))
        }.getOrNull() ?: return@forEach

        val escaped = Regex.escape(rule.pattern)
        val regex = runCatching { Regex("$escaped(.+?)$escaped") }.getOrNull() ?: return@forEach
        regex.findAll(text).forEach { matchResult ->
            allMatches.add(
                Match(
                    range = matchResult.range,
                    content = matchResult.groupValues[1],
                    color = color,
                )
            )
        }
    }

    if (allMatches.isEmpty()) return RpStyledText(text = text, ranges = emptyList())

    allMatches.sortWith(compareBy<Match>({ it.range.first }, { -it.range.last }))

    val nonOverlapping = mutableListOf<Match>()
    var lastEnd = -1
    allMatches.forEach { match ->
        if (match.range.first > lastEnd) {
            nonOverlapping.add(match)
            lastEnd = match.range.last
        }
    }

    val styledRanges = mutableListOf<RpStyledRange>()
    val output = StringBuilder(text.length)
    var currentIndex = 0
    nonOverlapping.forEach { match ->
        if (match.range.first > currentIndex) {
            output.append(text.substring(currentIndex, match.range.first))
        }

        val start = output.length
        output.append(match.content)
        val endExclusive = output.length
        if (endExclusive > start) {
            styledRanges.add(RpStyledRange(range = start until endExclusive, color = match.color))
        }

        currentIndex = match.range.last + 1
    }

    if (currentIndex < text.length) {
        output.append(text.substring(currentIndex))
    }

    return RpStyledText(text = output.toString(), ranges = styledRanges)
}

/**
 * 使用 BreakIterator 正确分割文本为字素簇（支持 emoji）
 */
/**
 * 欢迎词淡入动画组件（支持 Markdown / RP 自定义样式）
 */
@Composable
private fun AnimatedWelcomeText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    rpStyleRules: List<RpStyleRule>,
    modifier: Modifier = Modifier,
) {
    val styledText = remember(text, rpStyleRules) { applyRpStyleRules(text, rpStyleRules) }
    val graphemes = remember(styledText.text) { splitIntoGraphemes(styledText.text) }
    val animationProgress = remember(styledText.text) { graphemes.map { Animatable(0f) } }

    LaunchedEffect(styledText.text) {
        graphemes.forEachIndexed { index, _ ->
            val delayMs = index * 30L
            launch {
                delay(delayMs)
                animationProgress[index].animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 400,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
        }
    }

    FlowRow(
        modifier = modifier.animateContentSize(),
        horizontalArrangement = Arrangement.Start,
        verticalArrangement = Arrangement.Center,
    ) {
        graphemes.forEachIndexed { index, grapheme ->
            val progress = animationProgress.getOrNull(index)?.value ?: 1f

            val alpha = (progress - 0.2f).coerceAtLeast(0f) / 0.8f
            val blurRadius = ((1f - alpha) * 10f).dp
            val rpColor = styledText.colorAt(grapheme.range.first)
            val finalColor = (rpColor ?: color).copy(alpha = alpha)

            Text(
                text = grapheme.text,
                style = style,
                color = finalColor,
                modifier = Modifier
                    .blur(blurRadius)
                    .graphicsLayer {
                        this.alpha = alpha
                    }
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val animatedWidth = (placeable.width * progress).toInt()
                        layout(animatedWidth, placeable.height) {
                            val x = (animatedWidth - placeable.width) / 2
                            placeable.placeRelative(x, 0)
                        }
                    },
            )
        }
    }
}

@Composable
fun ChatPage(id: Uuid, text: String?, files: List<Uri>) {
    val vm: ChatVM = koinViewModel(
        parameters = {
            parametersOf(id.toString())
        }
    )
    val navController = LocalNavController.current
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Handle Error
    LaunchedEffect(Unit) {
        vm.errorFlow.collect { error ->
            toaster.show(error.message ?: "Error", type = ToastType.Error)
        }
    }

    val setting by vm.settings.collectAsStateWithLifecycle()
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val loadingJob by vm.conversationJob.collectAsStateWithLifecycle()
    val currentChatModel by vm.currentChatModel.collectAsStateWithLifecycle()
    val enableWebSearch by vm.enableWebSearch.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val softwareKeyboardController = LocalSoftwareKeyboardController.current

    // Handle back press when drawer is open
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch {
            drawerState.close()
        }
    }

    // Hide keyboard when drawer is open
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            softwareKeyboardController?.hide()
        }
    }

    val windowAdaptiveInfo = currentWindowDpSize()
    val isBigScreen =
        windowAdaptiveInfo.width > windowAdaptiveInfo.height && windowAdaptiveInfo.width >= 1100.dp

    val inputState = rememberChatInputState(
        message = remember(files) {
            buildList {
                val localFiles = context.createChatFilesByContents(files)
                val contentTypes = files.mapNotNull { file ->
                    context.getFileMimeType(file)
                }
                localFiles.forEachIndexed { index, file ->
                    val type = contentTypes.getOrNull(index)
                    if (type?.startsWith("image/") == true) {
                        add(UIMessagePart.Image(url = file.toString()))
                    } else if (type?.startsWith("video/") == true) {
                        add(UIMessagePart.Video(url = file.toString()))
                    } else if (type?.startsWith("audio/") == true) {
                        add(UIMessagePart.Audio(url = file.toString()))
                    }
                }
            }
        },
        textContent = remember(text) {
            text?.base64Decode() ?: ""
        }
    )

    val chatListState = rememberLazyListState()
    LaunchedEffect(vm) {
        if(!vm.chatListInitialized) {
            chatListState.scrollToItem(chatListState.layoutInfo.totalItemsCount)
            vm.chatListInitialized = true
        }
    }

    when {
        isBigScreen -> {
            PermanentNavigationDrawer(
                drawerContent = {
                    ChatDrawerContent(
                        navController = navController,
                        current = conversation,
                        vm = vm,
                        settings = setting,
                        drawerState = drawerState
                    )
                }
            ) {
                ChatPageContent(
                    inputState = inputState,
                    loadingJob = loadingJob,
                    setting = setting,
                    conversation = conversation,
                    drawerState = drawerState,
                    navController = navController,
                    vm = vm,
                    chatListState = chatListState,
                    enableWebSearch = enableWebSearch,
                    currentChatModel = currentChatModel,
                    bigScreen = true
                )
            }
        }

        else -> {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ChatDrawerContent(
                        navController = navController,
                        current = conversation,
                        vm = vm,
                        settings = setting,
                        drawerState = drawerState
                    )
                }
            ) {
                ChatPageContent(
                    inputState = inputState,
                    loadingJob = loadingJob,
                    setting = setting,
                    conversation = conversation,
                    drawerState = drawerState,
                    navController = navController,
                    vm = vm,
                    chatListState = chatListState,
                    enableWebSearch = enableWebSearch,
                    currentChatModel = currentChatModel,
                    bigScreen = false
                )
            }
            BackHandler(drawerState.isOpen) {
                scope.launch { drawerState.close() }
            }
        }
    }
}

@Composable
private fun ChatPageContent(
    inputState: ChatInputState,
    loadingJob: Job?,
    setting: Settings,
    bigScreen: Boolean,
    conversation: Conversation,
    drawerState: DrawerState,
    navController: NavHostController,
    vm: ChatVM,
    chatListState: LazyListState,
    enableWebSearch: Boolean,
    currentChatModel: Model?,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    var previewMode by rememberSaveable { mutableStateOf(false) }
    var isTemporaryChat by rememberSaveable { mutableStateOf(false) }
    var pendingJumpNodeId by remember { mutableStateOf<Uuid?>(null) }
    val currentConversationState = rememberUpdatedState(conversation)
    val conversationInitialized by vm.conversationInitialized.collectAsStateWithLifecycle()

    val density = LocalDensity.current
    var chatInputHeightPx by remember { mutableStateOf(0) }
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val chatInputChromeHeightDp = remember(chatInputHeightPx, imeBottomPx, density) {
        with(density) {
            (chatInputHeightPx - imeBottomPx).coerceAtLeast(0).toDp()
        }
    }
    val chatListBottomPadding = remember(chatInputChromeHeightDp) {
        if (chatInputChromeHeightDp > 0.dp) {
            maxOf(140.dp, chatInputChromeHeightDp + 32.dp)
        } else {
            140.dp
        }
    }

    LaunchedEffect(previewMode, pendingJumpNodeId) {
        val requestedNodeId = pendingJumpNodeId ?: return@LaunchedEffect
        if (previewMode) return@LaunchedEffect

        try {
            // Wait a couple of frames for AnimatedContent to swap and LazyColumn to attach.
            repeat(3) { withFrameNanos { } }

            val nodes = currentConversationState.value.messageNodes
            val targetIndex = nodes.indexOfFirst { it.id == requestedNodeId }
            if (targetIndex < 0) return@LaunchedEffect

            // Retry a few frames in case list layout isn't ready yet.
            repeat(15) {
                if (chatListState.layoutInfo.totalItemsCount > targetIndex) {
                    runCatching { chatListState.scrollToItem(targetIndex) }
                    if (chatListState.firstVisibleItemIndex == targetIndex) return@LaunchedEffect
                }
                withFrameNanos { }
            }
        } finally {
            pendingJumpNodeId = null
        }
    }



    LaunchedEffect(loadingJob) {
        inputState.loading = loadingJob != null
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        AssistantBackground(setting = setting)
        Scaffold(
            topBar = {
                TopBar(
                    settings = setting,
                    conversation = conversation,
                    bigScreen = bigScreen,
                    drawerState = drawerState,
                    previewMode = previewMode,
                    isTemporaryChat = isTemporaryChat,
                    onNewChat = {
                        // Temporary chats are not persisted, so just navigate to new chat
                        navigateToChatPage(navController)
                    },
                    onClickMenu = {
                        previewMode = !previewMode
                    },
                    onUpdateTitle = {
                        vm.updateTitle(it)
                    },
                    onUpdateSettings = { newSettings ->
                        vm.updateSettings(newSettings)
                    },
                    onToggleTemporaryChat = {
                        isTemporaryChat = !isTemporaryChat
                    },
                    onSetConversationAssistant = { assistantId ->
                        vm.setConversationAssistant(assistantId)
                    },
                )
            },
            // Removed bottomBar to allow floating input
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0.dp)
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                ChatList(
                    innerPadding = PaddingValues(bottom = chatListBottomPadding),
                    conversation = conversation,
                    state = chatListState,
                    loading = loadingJob != null,
                    previewMode = previewMode,
                    settings = setting,
                    recentlyRestoredNodeIds = vm.recentlyRestoredNodeIds.collectAsStateWithLifecycle().value,
                    onRegenerate = {
                        vm.regenerateAtMessage(it)
                    },
                    onEdit = {
                        inputState.editingMessage = it.id
                        inputState.setContents(it.parts)
                    },

                    onDelete = {
                        val backup = conversation
                        val deletedNodeIds = conversation.messageNodes.map { it.id }.toSet()
                        vm.deleteMessage(it)
                        val newNodeIds = vm.conversation.value.messageNodes.map { it.id }.toSet()
                        val removedIds = deletedNodeIds - newNodeIds
                        toaster.show(
                            message = context.getString(R.string.message_deleted),
                            action = me.rerere.rikkahub.ui.components.ui.ToastAction(
                                label = context.getString(R.string.undo),
                                onClick = {
                                    vm.updateConversation(backup)
                                    // Track restored node IDs for fade animation
                                    vm.markNodesAsRestored(removedIds)
                                }
                            )
                        )
                    },
                    onUpdateMessage = { newNode ->
                        vm.updateConversation(
                            conversation.copy(
                                messageNodes = conversation.messageNodes.map { node ->
                                    if (node.id == newNode.id) {
                                        newNode
                                    } else {
                                        node
                                    }
                                }
                            )
                        )
                        vm.saveConversationAsync()
                    },
                    onForkMessage = {
                        scope.launch {
                            vm.forkMessage(it)
                        }
                    },
                    onJumpToMessage = { nodeId ->
                        pendingJumpNodeId = nodeId
                        previewMode = false
                    },
                )

                val hasUserSentMessages =
                    conversation.messageNodes.any { it.role == me.rerere.ai.core.MessageRole.USER }
                val assistantForConversation = setting.getAssistantById(conversation.assistantId)
                    ?: setting.getCurrentAssistant()
                val hasAnyPresetMessages = assistantForConversation.presetMessages.isNotEmpty()

                val welcomeText = remember(assistantForConversation.id, assistantForConversation.welcomePhrases) {
                    selectWelcomePhrase(assistantForConversation.welcomePhrases)
                } ?: stringResource(R.string.welcome_phrases_fallback)

                val overlayState = remember(
                    conversationInitialized,
                    isTemporaryChat,
                    hasUserSentMessages,
                    hasAnyPresetMessages,
                    assistantForConversation.enableWelcomePhrases,
                ) {
                    when {
                        !conversationInitialized -> EmptyChatOverlay.None
                        isTemporaryChat && !hasUserSentMessages && !hasAnyPresetMessages -> EmptyChatOverlay.Temporary
                        assistantForConversation.enableWelcomePhrases && !isTemporaryChat && !hasUserSentMessages && !hasAnyPresetMessages ->
                            EmptyChatOverlay.Welcome

                        else -> EmptyChatOverlay.None
                    }
                }

                LaunchedEffect(conversation.id, overlayState, welcomeText) {
                    if (overlayState == EmptyChatOverlay.Welcome && welcomeText.isNotBlank()) {
                        vm.setPendingUiWelcomePhraseForAppContext(welcomeText)
                    }
                }

                val overlayBottomPadding = remember(chatInputChromeHeightDp) {
                    maxOf(EmptyChatOverlayBottomPaddingFallback, chatInputChromeHeightDp)
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = overlayBottomPadding)
                        .padding(WindowInsets.ime.asPaddingValues()),
                    contentAlignment = Alignment.Center,
                ) {
                    when (overlayState) {
                        EmptyChatOverlay.Welcome -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.dp),
                                modifier = Modifier
                                    .padding(horizontal = 32.dp)
                                    .offset(y = EmptyChatOverlayContentYOffset),
                            ) {
                                me.rerere.rikkahub.ui.components.ui.UIAvatar(
                                    name = assistantForConversation.name.ifBlank {
                                        stringResource(R.string.assistant_page_default_assistant)
                                    },
                                    value = assistantForConversation.avatar,
                                    modifier = Modifier.size(64.dp),
                                )
                                val fontSizeRatio = setting.displaySetting.fontSizeRatio
                                val welcomeTextStyle = MaterialTheme.typography.headlineSmall.copy(
                                    fontSize = MaterialTheme.typography.headlineSmall.fontSize * fontSizeRatio,
                                    lineHeight = 34.sp * fontSizeRatio,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                key(assistantForConversation.id) {
                                    AnimatedWelcomeText(
                                        text = welcomeText,
                                        style = welcomeTextStyle,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        rpStyleRules = setting.displaySetting.rpStyleRules,
                                    )
                                }
                            }
                        }

                        EmptyChatOverlay.Temporary -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                                modifier = Modifier
                                    .padding(32.dp)
                                    .offset(y = EmptyChatOverlayContentYOffset),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.HistoryToggleOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                                Text(
                                    text = stringResource(R.string.temporary_chat_description),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }

                        EmptyChatOverlay.None -> Unit
                    }
                }

                // Gradient behind floating toolbar
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
                                )
                            )
                        )
                )

                ChatInput(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { chatInputHeightPx = it.height },
                    state = inputState,
                    settings = setting,
                    conversation = conversation,
                    mcpManager = vm.mcpManager,
                    chatSuggestions = conversation.chatSuggestions,
                    onClickSuggestion = { suggestion ->
                        if (currentChatModel != null) {
                            vm.handleMessageSend(
                                listOf(me.rerere.ai.ui.UIMessagePart.Text(suggestion)),
                                isTemporaryChat = isTemporaryChat
                            )
                            scope.launch {
                                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                            }
                        } else {
                            toaster.show("Please select a model first", type = ToastType.Error)
                        }
                    },
                    onCancelClick = {
                        loadingJob?.cancel()
                    },
                    enableSearch = enableWebSearch,
                    onToggleSearch = {
                        if (enableWebSearch) {
                            vm.updateAssistantSearchMode(me.rerere.rikkahub.data.model.AssistantSearchMode.Off)
                        } else {
                            // Turn on search - use first provider if available
                            if (setting.searchServices.isNotEmpty()) {
                                vm.updateAssistantSearchMode(me.rerere.rikkahub.data.model.AssistantSearchMode.Provider(0))
                            }
                        }
                    },
                    onSendClick = {
                        if (currentChatModel == null) {
                            toaster.show("Please select a model first", type = ToastType.Error)
                            return@ChatInput
                        }
                        if (inputState.isEditing()) {
                            vm.handleMessageEdit(
                                parts = inputState.getContents(),
                                messageId = inputState.editingMessage!!,
                            )
                        } else {

                            vm.handleMessageSend(inputState.getContents(), isTemporaryChat = isTemporaryChat)
                            scope.launch {
                                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                            }
                        }
                        inputState.clearInput()
                    },
                    onLongSendClick = {
                        if (inputState.isEditing()) {
                            vm.handleMessageEdit(
                                parts = inputState.getContents(),
                                messageId = inputState.editingMessage!!,
                            )
                        } else {
                            vm.handleMessageSend(content = inputState.getContents(), answer = false, isTemporaryChat = isTemporaryChat)
                            scope.launch {
                                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                            }
                        }
                        inputState.clearInput()
                    },
                    onUpdateChatModel = {
                        vm.setChatModel(assistant = setting.getCurrentAssistant(), model = it)
                    },
                    onUpdateAssistant = {
                        vm.updateSettings(
                            setting.copy(
                                assistants = setting.assistants.map { assistant ->
                                    if (assistant.id == it.id) {
                                        it
                                    } else {
                                        assistant
                                    }
                                }
                            )
                        )
                    },
                    onUpdateConversation = { updatedConversation ->
                        vm.updateConversation(updatedConversation)
                    },
                    onUpdateSearchService = { index ->
                        // Only persist the selection to the assistant's searchMode to avoid double-update flicker
                        // The global setting 'searchServiceSelected' is deprecated in favor of assistant-specific settings
                        vm.updateAssistantSearchMode(me.rerere.rikkahub.data.model.AssistantSearchMode.Provider(index))
                    },
                    onClearContext = {
                        vm.handleMessageTruncate()
                    },
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    settings: Settings,
    conversation: Conversation,
    drawerState: DrawerState,
    bigScreen: Boolean,
    previewMode: Boolean,
    isTemporaryChat: Boolean,
    onClickMenu: () -> Unit,
    onNewChat: () -> Unit,
    onUpdateTitle: (String) -> Unit,
    onUpdateSettings: (Settings) -> Unit,
    onToggleTemporaryChat: () -> Unit,
    onSetConversationAssistant: (Uuid) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val titleState = useEditState<String> {
        onUpdateTitle(it)
    }
    
    // State for assistant picker - must be at function level for proper recomposition
    var showAssistantPicker by remember { mutableStateOf(false) }
    val assistantForConversation = settings.getAssistantById(conversation.assistantId)
        ?: settings.getCurrentAssistant()

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        navigationIcon = {
            if (!bigScreen) {
                IconButton(
                    onClick = {
                        scope.launch { drawerState.open() }
                    }
                ) {
                    Icon(Icons.Rounded.Menu, contentDescription = stringResource(R.string.a11y_messages))
                }
            }
        },
        title = {
            val editTitleWarning = stringResource(R.string.chat_page_edit_title_warning)
            
            // Crossfade between normal title and "Temporary Chat"
            androidx.compose.animation.AnimatedContent(
                targetState = isTemporaryChat,
                transitionSpec = {
                    androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 400f)
                    ) togetherWith androidx.compose.animation.fadeOut(
                        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 400f)
                    )
                },
                label = "title_crossfade"
            ) { isTempChat ->
                if (isTempChat) {
                    Text(
                        text = stringResource(R.string.temporary_chat_title),
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Surface(
                        onClick = {
                            if (conversation.messageNodes.isNotEmpty()) {
                                titleState.open(conversation.title)
                            } else {
                                toaster.show(editTitleWarning, type = ToastType.Warning)
                            }
                        },
                        color = Color.Transparent,
                    ) {
                        Text(
                            text = conversation.title.ifBlank { stringResource(R.string.chat_page_new_chat) },
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        actions = {
            // Check if chat is "empty" (no user-sent messages, ignoring preset messages)
            val isEmpty = !conversation.messageNodes.any { it.role == me.rerere.ai.core.MessageRole.USER }
            
            // Fluid transition between assistant icon and search/new icons
            androidx.compose.animation.AnimatedContent(
                targetState = isEmpty to isTemporaryChat,
                transitionSpec = {
                    (androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 400f)
                    ) + androidx.compose.animation.scaleIn(
                        initialScale = 0.85f,
                        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 400f)
                    )) togetherWith (androidx.compose.animation.fadeOut(
                        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 400f)
                    ) + androidx.compose.animation.scaleOut(
                        targetScale = 0.85f,
                        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 400f)
                    ))
                },
                label = "topbar_actions"
            ) { (isEmptyState, isTempChat) ->
                when {
                    // Empty normal chat: show temp toggle + assistant
                    isEmptyState && !isTempChat -> {
                        Row {
                            IconButton(onClick = { onToggleTemporaryChat() }) {
                                Icon(
                                    Icons.Rounded.HistoryToggleOff,
                                    contentDescription = stringResource(R.string.temporary_chat_title)
                                )
                            }
                            Box(
                                modifier = Modifier.size(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                me.rerere.rikkahub.ui.components.ui.UIAvatar(
                                    name = assistantForConversation.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                                    value = assistantForConversation.avatar,
                                    modifier = Modifier.size(32.dp),
                                    onClick = { showAssistantPicker = true }
                                )
                            }
                        }
                    }
                    // Empty temporary chat: show history (toggle back) + assistant
                    isEmptyState && isTempChat -> {
                        Row {
                            IconButton(onClick = { onToggleTemporaryChat() }) {
                                Icon(
                                    Icons.Rounded.History,
                                    contentDescription = stringResource(R.string.a11y_make_normal_chat)
                                )
                            }
                            Box(
                                modifier = Modifier.size(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                me.rerere.rikkahub.ui.components.ui.UIAvatar(
                                    name = assistantForConversation.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                                    value = assistantForConversation.avatar,
                                    modifier = Modifier.size(32.dp),
                                    onClick = { showAssistantPicker = true }
                                )
                            }
                        }
                    }
                    // Non-empty (either temporary or normal): show search + new chat
                    else -> {
                        Row {
                            IconButton(onClick = { onClickMenu() }) {
                                Icon(
                                    if (previewMode) Icons.Rounded.Close else Icons.Rounded.Search,
                                    contentDescription = stringResource(R.string.a11y_chat_options)
                                )
                            }
                            IconButton(onClick = { onNewChat() }) {
                                Icon(
                                    Icons.Rounded.AddCircle,
                                    contentDescription = stringResource(R.string.chat_page_new_message)
                                )
                            }
                        }
                    }
                }
            }
        },
    )
    
    // Assistant picker sheet - outside TopAppBar for proper state handling
    if (showAssistantPicker) {
        val assistantState = me.rerere.rikkahub.ui.hooks.rememberAssistantState(settings, onUpdateSettings)
        me.rerere.rikkahub.ui.components.ai.AssistantPickerSheet(
            settings = settings,
            currentAssistant = assistantForConversation,
            onAssistantSelected = { selectedAssistant ->
                assistantState.setSelectAssistant(selectedAssistant)
                onSetConversationAssistant(selectedAssistant.id)
                showAssistantPicker = false
            },
            onDismiss = { showAssistantPicker = false }
        )
    }
    titleState.EditStateContent { title, onUpdate ->
        AlertDialog(
            onDismissRequest = {
                titleState.dismiss()
            },
            title = {
                Text(stringResource(R.string.chat_page_edit_title))
            },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = onUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        titleState.confirm()
                    }
                ) {
                    Text(stringResource(R.string.chat_page_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        titleState.dismiss()
                    }
                ) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        )
    }
}
