package me.rerere.rikkahub.service

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppContextWelcomePhraseTest {
    @Test
    fun `inject prepends context into first user text`() {
        val messages = listOf(
            UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("hi"))),
            UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("user text"))),
            UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("ok"))),
        )

        val result = injectWelcomePhraseIntoFirstUserMessage(messages, "你好呀")

        assertTrue(result.injected)
        assertEquals(3, result.messages.size)
        assertEquals("user text", (messages[1].parts[0] as UIMessagePart.Text).text)

        val injectedText = (result.messages[1].parts.first { it is UIMessagePart.Text } as UIMessagePart.Text).text
        assertTrue(injectedText.contains("[APP_CONTEXT_BEGIN]"))
        assertTrue(injectedText.contains("UI刚刚展示的欢迎词：你好呀"))
        assertTrue(injectedText.endsWith("user text"))
    }

    @Test
    fun `inject adds a text part when user message has no text`() {
        val messages = listOf(
            UIMessage(
                role = MessageRole.USER,
                parts = listOf(UIMessagePart.Image("file://dummy")),
            ),
        )

        val result = injectWelcomePhraseIntoFirstUserMessage(messages, "欢迎回来")

        assertTrue(result.injected)
        assertTrue(result.messages[0].parts.first() is UIMessagePart.Text)
        val injectedText = (result.messages[0].parts.first() as UIMessagePart.Text).text
        assertTrue(injectedText.contains("UI刚刚展示的欢迎词：欢迎回来"))
    }

    @Test
    fun `inject no-ops when no user message`() {
        val messages = listOf(
            UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("hi"))),
        )

        val result = injectWelcomePhraseIntoFirstUserMessage(messages, "你好呀")

        assertFalse(result.injected)
        assertEquals(messages, result.messages)
    }
}

