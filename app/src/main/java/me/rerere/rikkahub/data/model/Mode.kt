package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Injection position for modes and lorebook entries.
 * Determines where the prompt will be injected in the message context.
 */
@Serializable
enum class InjectionPosition {
    @SerialName("before_system")
    BEFORE_SYSTEM,         // Before the system prompt
    
    @SerialName("after_system")
    AFTER_SYSTEM,          // After the system prompt
    
    @SerialName("top_of_chat")
    TOP_OF_CHAT,           // As the first user/assistant message
    
    @SerialName("before_latest")
    BEFORE_LATEST,         // Before the latest user message
    
    @SerialName("at_depth")
    AT_DEPTH               // At a specific depth in message history
}

/**
 * A custom mode that can be enabled to inject additional prompts.
 * Modes are ordered by priority (list position).
 * Default state is set here, but can be overridden per-conversation.
 */
@Serializable
data class Mode(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val prompt: String = "",
    val defaultEnabled: Boolean = false,  // Default state for new chats
    val injectionPosition: InjectionPosition = InjectionPosition.AFTER_SYSTEM,
    val depth: Int = 0  // Only used when injectionPosition is AT_DEPTH
)
