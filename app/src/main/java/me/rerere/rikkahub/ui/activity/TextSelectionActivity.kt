package me.rerere.rikkahub.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.highlight.Highlighter
import me.rerere.highlight.LocalHighlighter
import me.rerere.rikkahub.DIRECT_CHAT_TARGET_TYPE_ASSISTANT
import me.rerere.rikkahub.DIRECT_CHAT_TARGET_TYPE_GROUP_CHAT
import me.rerere.rikkahub.EXTRA_DIRECT_CHAT_AUTO_SEND
import me.rerere.rikkahub.EXTRA_DIRECT_CHAT_TARGET_ID
import me.rerere.rikkahub.EXTRA_DIRECT_CHAT_TARGET_TYPE
import me.rerere.rikkahub.EXTRA_DIRECT_CHAT_TEXT
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.ChatTarget
import me.rerere.rikkahub.ui.components.textselection.TextSelectionSheet
import me.rerere.rikkahub.ui.components.ui.AppToasterHost
import me.rerere.rikkahub.ui.components.ui.rememberAppToasterState
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Floating activity that handles Android text selection toolbar integration.
 * Appears as an overlay when user selects text and taps "Ask LastChat".
 */
class TextSelectionActivity : ComponentActivity() {
    private val highlighter by inject<Highlighter>()
    private val settingsStore by inject<SettingsStore>()
    private val viewModel: TextSelectionVM by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val selectedText = extractInputText(intent)

        if (selectedText.isBlank()) {
            finish()
            return
        }

        viewModel.updateSelectedText(selectedText)

        setContent {
            val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
            val toastState = rememberAppToasterState()

            RikkahubTheme {
                CompositionLocalProvider(
                    LocalSettings provides settings,
                    LocalHighlighter provides highlighter,
                    LocalToaster provides toastState,
                ) {
                    TextSelectionSheet(
                        viewModel = viewModel,
                        onDismiss = { finish() },
                        onContinueInApp = {
                            val intent = Intent(this@TextSelectionActivity, RouteActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                
                                // Check if it was a translate action
                                if (viewModel.lastAction == QuickAction.TRANSLATE) {
                                    // Navigate to Translator page with pre-filled data
                                    putExtra("navigate_to", "translator")
                                    putExtra("translator_input", viewModel.selectedText)
                                    val state = viewModel.state
                                    if (state is TextSelectionState.Result) {
                                        putExtra("translator_output", state.responseText)
                                    }
                                } else {
                                    // For other actions, open chat with context
                                    putExtra("continue_conversation", true)
                                    putExtra("selected_text", viewModel.selectedText)
                                    // Pass the assistant ID from text selection config
                                    settings.textSelectionConfig.assistantId?.let { 
                                        putExtra("selection_assistant_id", it.toString())
                                    }
                                    val state = viewModel.state
                                    if (state is TextSelectionState.Result) {
                                        putExtra("ai_response", state.responseText)
                                    }
                                    if (viewModel.lastAction == QuickAction.CUSTOM) {
                                        putExtra("user_prompt", viewModel.customPrompt)
                                    }
                                }
                            }
                            startActivity(intent)
                            finish()
                        },
                        onSendToConversation = { target ->
                            val intent = Intent(this@TextSelectionActivity, RouteActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                putExtra(EXTRA_DIRECT_CHAT_TEXT, viewModel.selectedText)
                                putExtra(EXTRA_DIRECT_CHAT_AUTO_SEND, true)
                                when (target) {
                                    is ChatTarget.Assistant -> {
                                        putExtra(EXTRA_DIRECT_CHAT_TARGET_TYPE, DIRECT_CHAT_TARGET_TYPE_ASSISTANT)
                                        putExtra(EXTRA_DIRECT_CHAT_TARGET_ID, target.assistantId.toString())
                                    }

                                    is ChatTarget.GroupChat -> {
                                        putExtra(EXTRA_DIRECT_CHAT_TARGET_TYPE, DIRECT_CHAT_TARGET_TYPE_GROUP_CHAT)
                                        putExtra(EXTRA_DIRECT_CHAT_TARGET_ID, target.templateId.toString())
                                    }
                                }
                            }
                            startActivity(intent)
                            finish()
                        }
                    )
                    AppToasterHost(state = toastState)
                }
            }
        }
    }

    private fun extractInputText(intent: Intent?): String {
        val inputText = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            else -> intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
                ?: intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)
        }
        return inputText?.toString()?.trim().orEmpty()
    }
}
