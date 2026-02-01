package me.rerere.rikkahub.service

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatLiveUpdateStateResolverTest {
    @Test
    fun `resolve returns WAITING for empty messages`() {
        assertEquals(ChatLiveUpdateState.WAITING, ChatLiveUpdateStateResolver.resolve(emptyList()))
    }

    @Test
    fun `resolve returns INFERENCE when assistant is reasoning and no output`() {
        val messages = listOf(
            UIMessage(
                role = MessageRole.USER,
                parts = listOf(UIMessagePart.Text("hi")),
            ),
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.Reasoning(reasoning = "thinking...", finishedAt = null)),
            ),
        )
        assertEquals(ChatLiveUpdateState.INFERENCE, ChatLiveUpdateStateResolver.resolve(messages))
    }

    @Test
    fun `resolve returns OUTPUT when assistant has output text`() {
        val messages = listOf(
            UIMessage(
                role = MessageRole.USER,
                parts = listOf(UIMessagePart.Text("hi")),
            ),
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.Text("hello")),
            ),
        )
        assertEquals(ChatLiveUpdateState.OUTPUT, ChatLiveUpdateStateResolver.resolve(messages))
    }

    @Test
    fun `resolve returns OUTPUT when assistant has reasoning and output text`() {
        val messages = listOf(
            UIMessage(
                role = MessageRole.USER,
                parts = listOf(UIMessagePart.Text("hi")),
            ),
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.Reasoning(reasoning = "thinking...", finishedAt = null),
                    UIMessagePart.Text("hello"),
                ),
            ),
        )
        assertEquals(ChatLiveUpdateState.OUTPUT, ChatLiveUpdateStateResolver.resolve(messages))
    }

    @Test
    fun `resolve returns TOOL_CALL when assistant requests a tool`() {
        val messages = listOf(
            UIMessage(
                role = MessageRole.USER,
                parts = listOf(UIMessagePart.Text("search something")),
            ),
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.ToolCall(
                        toolCallId = "call_1",
                        toolName = "search_web",
                        arguments = "{}",
                        metadata = null,
                    ),
                ),
            ),
        )
        assertEquals(ChatLiveUpdateState.TOOL_CALL, ChatLiveUpdateStateResolver.resolve(messages))
    }

    @Test
    fun `resolve returns TOOL_CALL when tool result is the latest message`() {
        val messages = listOf(
            UIMessage(
                role = MessageRole.USER,
                parts = listOf(UIMessagePart.Text("search something")),
            ),
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.ToolCall(
                        toolCallId = "call_1",
                        toolName = "search_web",
                        arguments = "{}",
                        metadata = null,
                    ),
                ),
            ),
            UIMessage(
                role = MessageRole.TOOL,
                parts = listOf(
                    UIMessagePart.ToolResult(
                        toolCallId = "call_1",
                        toolName = "search_web",
                        content = JsonPrimitive("ok"),
                        arguments = JsonPrimitive("{}"),
                        metadata = null,
                    ),
                ),
            ),
        )
        assertEquals(ChatLiveUpdateState.TOOL_CALL, ChatLiveUpdateStateResolver.resolve(messages))
    }

    @Test
    fun `isOngoing includes WAITING and TOOL_CALL`() {
        assertTrue(ChatLiveUpdateState.WAITING.isOngoing())
        assertTrue(ChatLiveUpdateState.INFERENCE.isOngoing())
        assertTrue(ChatLiveUpdateState.TOOL_CALL.isOngoing())
        assertTrue(ChatLiveUpdateState.OUTPUT.isOngoing())
        assertFalse(ChatLiveUpdateState.DONE.isOngoing())
        assertFalse(ChatLiveUpdateState.ERROR.isOngoing())
    }
}
