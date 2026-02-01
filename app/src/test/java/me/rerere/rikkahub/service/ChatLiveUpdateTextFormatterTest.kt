package me.rerere.rikkahub.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatLiveUpdateTextFormatterTest {
    @Test
    fun `normalizePreviewText collapses whitespace and removes newlines`() {
        val input = "Hello\n\nworld\t\t!\r\n  Nice  to  meet\tyou "
        val output = ChatLiveUpdateTextFormatter.normalizePreviewText(input)
        assertEquals("Hello world ! Nice to meet you", output)
    }

    @Test
    fun `tail returns ellipsis plus tail`() {
        val input = "A\nB\nC\nD"
        val output = ChatLiveUpdateTextFormatter.tail(input, maxChars = 4)
        assertEquals("…C D", output)
    }
}
