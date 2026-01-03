package me.rerere.rikkahub

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import me.rerere.rikkahub.ui.components.ui.AppToasterHost
import me.rerere.rikkahub.ui.components.ui.rememberAppToasterState
import kotlinx.serialization.Serializable
import me.rerere.highlight.Highlighter
import me.rerere.highlight.LocalHighlighter
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.components.ui.TTSController
import me.rerere.rikkahub.ui.context.LocalAnimatedVisibilityScope
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalSharedTransitionScope
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.readBooleanPreference
import me.rerere.rikkahub.ui.hooks.readStringPreference
import me.rerere.rikkahub.ui.hooks.rememberCustomTtsState
import me.rerere.rikkahub.ui.pages.assistant.AssistantPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantDetailPage
import me.rerere.rikkahub.ui.pages.assistant.groupchat.GroupChatTemplateDetailPage
import me.rerere.rikkahub.ui.pages.assistant.scheduled.AssistantScheduledTaskEditPage
import me.rerere.rikkahub.ui.pages.assistant.scheduled.AssistantScheduledTasksPage
import me.rerere.rikkahub.ui.pages.backup.BackupPage
import me.rerere.rikkahub.ui.pages.chat.ChatPage
import me.rerere.rikkahub.ui.pages.imggen.ImageGenPage
import me.rerere.rikkahub.ui.pages.logs.RequestLogDetailPage
import me.rerere.rikkahub.ui.pages.logs.RequestLogsOverviewPage
import me.rerere.rikkahub.ui.pages.menu.MenuPage
import me.rerere.rikkahub.ui.pages.setting.SettingAboutPage
import me.rerere.rikkahub.ui.pages.setting.SettingDisplayPage
import me.rerere.rikkahub.ui.pages.setting.SettingLorebookDetailPage
import me.rerere.rikkahub.ui.pages.setting.SettingLorebooksPage
import me.rerere.rikkahub.ui.pages.setting.SettingMcpPage
import me.rerere.rikkahub.ui.pages.setting.SettingModelPage
import me.rerere.rikkahub.ui.pages.setting.SettingModesPage
import me.rerere.rikkahub.ui.pages.setting.SettingPage
import me.rerere.rikkahub.ui.pages.setting.SettingPromptInjectionsPage
import me.rerere.rikkahub.ui.pages.setting.SettingProviderDetailPage
import me.rerere.rikkahub.ui.pages.setting.SettingProviderPage
import me.rerere.rikkahub.ui.pages.setting.SettingSearchPage
import me.rerere.rikkahub.ui.pages.setting.SettingTTSPage
import me.rerere.rikkahub.ui.pages.setting.SettingRpOptimizationsPage
import me.rerere.rikkahub.ui.pages.share.handler.ShareHandlerPage
import me.rerere.rikkahub.ui.pages.translator.TranslatorPage
import me.rerere.rikkahub.ui.pages.webview.WebViewPage
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.service.WelcomePhrasesService
import org.koin.android.ext.android.inject
import me.rerere.rikkahub.utils.fileSizeToString
import kotlin.uuid.Uuid

private const val TAG = "RouteActivity"

class RouteActivity : ComponentActivity() {
    companion object {
        @Volatile
        private var isSingletonImageLoaderFactorySet: Boolean = false
    }

    private val highlighter by inject<Highlighter>()
    private val okHttpClient by inject<OkHttpClient>()
    private val settingsStore by inject<SettingsStore>()
    private val welcomePhrasesService by inject<WelcomePhrasesService>()
    private var navStack by mutableStateOf<NavHostController?>(null)
    private var pendingAssistantId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        disableNavigationBarContrast()
        super.onCreate(savedInstanceState)
        
        // Check for assistant shortcut intent
        pendingAssistantId = intent?.getStringExtra("assistantId")

        setContent {
            val navStack = rememberNavController()
            this.navStack = navStack
            ShareHandler(navStack)
            AssistantShortcutHandler(navStack)
            RikkahubTheme {
                if (!isSingletonImageLoaderFactorySet) {
                    setSingletonImageLoaderFactory { context ->
                        ImageLoader.Builder(context)
                            .crossfade(true)
                            .memoryCache {
                                MemoryCache.Builder()
                                    .maxSizePercent(context, 0.25) // Use 25% of app's memory for image cache
                                    .build()
                            }
                            .diskCache {
                                DiskCache.Builder()
                                    .directory(context.filesDir.resolve("icon_cache").toOkioPath())
                                    .maxSizeBytes(50L * 1024 * 1024) // 50 MB persistent disk cache for icons
                                    .build()
                            }
                            .components {
                                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
                                add(SvgDecoder.Factory(scaleToDensity = true))
                            }
                            .build()
                    }
                    isSingletonImageLoaderFactorySet = true
                }
                AppRoutes(navStack)
            }
        }
    }

    private fun disableNavigationBarContrast() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }
    
    @Composable
    private fun AssistantShortcutHandler(navBackStack: NavHostController) {
        val assistantIdStr = pendingAssistantId
        LaunchedEffect(assistantIdStr) {
            if (assistantIdStr != null) {
                try {
                    val assistantId = Uuid.parse(assistantIdStr)
                    // Update the selected assistant
                    settingsStore.updateAssistant(assistantId)
                    // Mark as recently used
                    settingsStore.markAssistantUsed(assistantId)
                    welcomePhrasesService.enqueueAutoRefreshForAssistantIfNeeded(this@RouteActivity, assistantId)
                    pendingAssistantId = null
                    // Navigate to a new chat
                    navBackStack.navigate(Screen.Chat(Uuid.random().toString())) {
                        popUpTo(0) { inclusive = true }
                    }
                } catch (e: Exception) {
                    pendingAssistantId = null
                    e.printStackTrace()
                }
            }
        }
    }

    @Composable
    private fun ShareHandler(navBackStack: NavHostController) {
        val shareIntent = remember {
            Intent().apply {
                action = intent?.action
                putExtra(Intent.EXTRA_TEXT, intent?.getStringExtra(Intent.EXTRA_TEXT))
                putExtra(Intent.EXTRA_STREAM, intent?.getStringExtra(Intent.EXTRA_STREAM))
            }
        }

        LaunchedEffect(navBackStack) {
            if (shareIntent.action == Intent.ACTION_SEND) {
                val text = shareIntent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                val imageUri = shareIntent.getStringExtra(Intent.EXTRA_STREAM)
                navBackStack.navigate(Screen.ShareHandler(text, imageUri))
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Navigate to the chat screen if a conversation ID is provided
        intent.getStringExtra("conversationId")?.let { text ->
            navStack?.navigate(Screen.Chat(text))
        }
        // Handle assistant shortcut
        intent.getStringExtra("assistantId")?.let { assistantIdStr ->
            pendingAssistantId = assistantIdStr
        }
    }

    @Composable
    fun AppRoutes(navBackStack: NavHostController) {
        val toastState = rememberAppToasterState()
        val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
        val tts = rememberCustomTtsState()

        LaunchedEffect(settings.init, settings.assistantId, pendingAssistantId) {
            if (!settings.init && pendingAssistantId == null) {
                welcomePhrasesService.enqueueAutoRefreshForCurrentAssistantIfNeeded(this@RouteActivity)
            }
        }

        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalNavController provides navBackStack,
                LocalSharedTransitionScope provides this,
                LocalSettings provides settings,
                LocalHighlighter provides highlighter,
                LocalToaster provides toastState,
                LocalTTSState provides tts,
            ) {
                // Check for backup cleanup results and show toast
                LaunchedEffect(Unit) {
                    val prefs = this@RouteActivity.getSharedPreferences("backup_cleanup", MODE_PRIVATE)
                    val unsupportedBytes = prefs.getLong("unsupported_bytes", 0)
                    val issuesFixed = prefs.getInt("issues_fixed", 0)
                    
                    if (unsupportedBytes > 0 || issuesFixed > 0) {
                        // Clear the stored values
                        prefs.edit().clear().apply()
                        
                        // Build cleanup message
                        val parts = mutableListOf<String>()
                        if (unsupportedBytes > 0) {
                            parts.add("${unsupportedBytes.fileSizeToString()} of unsupported data")
                        }
                        if (issuesFixed > 0) {
                            parts.add("$issuesFixed invalid references")
                        }
                        
                        val message = "Backup cleaned: ${parts.joinToString(", ")}"
                        toastState.show(message, type = me.rerere.rikkahub.ui.components.ui.ToastType.Info)
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                TTSController()
                NavHost(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    startDestination = Screen.Chat(
                        id = if (readBooleanPreference("create_new_conversation_on_start", true)) {
                            Uuid.random().toString()
                        } else {
                            readStringPreference(
                                "lastConversationId",
                                Uuid.random().toString()
                            ) ?: Uuid.random().toString()
                        }
                    ),
                    navController = navBackStack,
                    enterTransition = { 
                        slideInHorizontally(
                            animationSpec = tween(200, easing = FastOutSlowInEasing)
                        ) { it / 2 } + fadeIn(animationSpec = tween(150))
                    },
                    exitTransition = { 
                        slideOutHorizontally(
                            animationSpec = tween(200, easing = FastOutSlowInEasing)
                        ) { -it / 4 } + fadeOut(animationSpec = tween(100))
                    },
                    popEnterTransition = {
                        slideInHorizontally(
                            animationSpec = tween(200, easing = FastOutSlowInEasing)
                        ) { -it / 4 } + fadeIn(animationSpec = tween(150))
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            animationSpec = tween(200, easing = FastOutSlowInEasing)
                        ) { it / 2 } + fadeOut(animationSpec = tween(100))
                    }
                ) {
                    composable<Screen.Chat>(
                        enterTransition = { fadeIn() },
                        exitTransition = { fadeOut() },
                    ) { backStackEntry ->
                        val route = backStackEntry.toRoute<Screen.Chat>()
                        ChatPage(
                            id = Uuid.parse(route.id),
                            text = route.text,
                            files = route.files.map { it.toUri() }
                        )
                    }

                    composable<Screen.ShareHandler> { backStackEntry ->
                        val route = backStackEntry.toRoute<Screen.ShareHandler>()
                        ShareHandlerPage(
                            text = route.text,
                            image = route.streamUri
                        )
                    }



                    // All assistant-related routes share the same AnimatedVisibilityScope
                    // for seamless hero animations across all screens
                    composable<Screen.Assistant> {
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                            AssistantPage()
                        }
                    }

                    composable<Screen.AssistantDetail> { backStackEntry ->
                        val route = backStackEntry.toRoute<Screen.AssistantDetail>()
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                            AssistantDetailPage(route.id)
                        }
                    }

                    composable<Screen.GroupChatTemplateDetail> { backStackEntry ->
                        val route = backStackEntry.toRoute<Screen.GroupChatTemplateDetail>()
                        GroupChatTemplateDetailPage(id = route.id)
                    }

                    composable<Screen.AssistantScheduledTasks> { backStackEntry ->
                        val route = backStackEntry.toRoute<Screen.AssistantScheduledTasks>()
                        AssistantScheduledTasksPage(assistantId = route.assistantId)
                    }

                    composable<Screen.AssistantScheduledTaskEdit> { backStackEntry ->
                        val route = backStackEntry.toRoute<Screen.AssistantScheduledTaskEdit>()
                        AssistantScheduledTaskEditPage(
                            assistantId = route.assistantId,
                            taskId = route.taskId,
                        )
                    }

                    composable<Screen.Menu> {
                        MenuPage()
                    }

                    composable<Screen.Translator> {
                        TranslatorPage()
                    }

                    composable<Screen.Setting> {
                        SettingPage()
                    }

                    composable<Screen.Backup> {
                        BackupPage()
                    }

                    composable<Screen.ImageGen> {
                        ImageGenPage()
                    }

                    composable<Screen.WebView> { backStackEntry ->
                        val route = backStackEntry.toRoute<Screen.WebView>()
                        WebViewPage(route.url, route.content)
                    }

                    composable<Screen.SettingDisplay> {
                        SettingDisplayPage()
                    }

                    composable<Screen.SettingProvider> {
                        SettingProviderPage()
                    }

                    composable<Screen.SettingProviderDetail> {
                        val route = it.toRoute<Screen.SettingProviderDetail>()
                        val id = Uuid.parse(route.providerId)
                        SettingProviderDetailPage(id = id)
                    }

                    composable<Screen.SettingModels> {
                        SettingModelPage()
                    }

                    composable<Screen.SettingAbout> {
                        SettingAboutPage()
                    }

                    composable<Screen.SettingSearch> {
                        SettingSearchPage()
                    }

                    composable<Screen.SettingTTS> {
                        SettingTTSPage()
                    }

                    composable<Screen.SettingMcp> {
                        SettingMcpPage()
                    }

                    composable<Screen.SettingRpOptimizations> {
                        SettingRpOptimizationsPage()
                    }

                    composable<Screen.SettingPromptInjections> {
                        SettingPromptInjectionsPage()
                    }

                    composable<Screen.SettingModes>(
                        enterTransition = { fadeIn(animationSpec = tween(300)) },
                        exitTransition = { fadeOut(animationSpec = tween(300)) },
                        popEnterTransition = { fadeIn(animationSpec = tween(300)) },
                        popExitTransition = { fadeOut(animationSpec = tween(300)) }
                    ) {
                        SettingModesPage()
                    }

                    composable<Screen.SettingLorebooks>(
                        enterTransition = { fadeIn(animationSpec = tween(300)) },
                        exitTransition = { fadeOut(animationSpec = tween(300)) },
                        popEnterTransition = { fadeIn(animationSpec = tween(300)) },
                        popExitTransition = { fadeOut(animationSpec = tween(300)) }
                    ) {
                        SettingLorebooksPage()
                    }

                    composable<Screen.SettingLorebookDetail> { backStackEntry ->
                        val route = backStackEntry.toRoute<Screen.SettingLorebookDetail>()
                        SettingLorebookDetailPage(id = route.id)
                    }

                    composable<Screen.RequestLogs> {
                        RequestLogsOverviewPage()
                    }

                    composable<Screen.RequestLogDetail> { backStackEntry ->
                        val route = backStackEntry.toRoute<Screen.RequestLogDetail>()
                        RequestLogDetailPage(id = route.id)
                    }

                }
                // Toast host must be last so it renders on top of all content
                AppToasterHost(state = toastState)
                }
            }
        }
    }
}

sealed interface Screen {
    @Serializable
    data class Chat(val id: String, val text: String? = null, val files: List<String> = emptyList()) : Screen

    @Serializable
    data class ShareHandler(val text: String, val streamUri: String? = null) : Screen


    @Serializable
    data object Assistant : Screen

    @Serializable
    data class AssistantDetail(val id: String) : Screen

    @Serializable
    data class GroupChatTemplateDetail(val id: String) : Screen

    @Serializable
    data class AssistantScheduledTasks(val assistantId: String) : Screen

    @Serializable
    data class AssistantScheduledTaskEdit(val assistantId: String, val taskId: String? = null) : Screen

    @Serializable
    data object Menu : Screen

    @Serializable
    data object Translator : Screen

    @Serializable
    data object Setting : Screen

    @Serializable
    data object Backup : Screen

    @Serializable
    data object ImageGen : Screen

    @Serializable
    data class WebView(val url: String = "", val content: String = "") : Screen

    @Serializable
    data object SettingDisplay : Screen

    @Serializable
    data object SettingProvider : Screen

    @Serializable
    data class SettingProviderDetail(val providerId: String) : Screen

    @Serializable
    data object SettingModels : Screen

    @Serializable
    data object SettingAbout : Screen

    @Serializable
    data object SettingSearch : Screen

    @Serializable
    data object SettingTTS : Screen

    @Serializable
    data object SettingMcp : Screen

    @Serializable
    data object SettingRpOptimizations : Screen

    @Serializable
    data object SettingPromptInjections : Screen

    @Serializable
    data object SettingModes : Screen

    @Serializable
    data object SettingLorebooks : Screen

    @Serializable
    data class SettingLorebookDetail(val id: String) : Screen

    @Serializable
    data object RequestLogs : Screen

    @Serializable
    data class RequestLogDetail(val id: Long) : Screen

}
