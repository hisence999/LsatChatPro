package me.rerere.rikkahub.data.datastore

import org.junit.Assert.assertEquals
import org.junit.Test

class McpToolCallTimeoutTest {
    @Test
    fun getMcpToolCallTimeoutSeconds_defaultsTo60() {
        assertEquals(60, Settings().getMcpToolCallTimeoutSeconds())
    }

    @Test
    fun getMcpToolCallTimeoutSeconds_coercesNonPositiveTo1() {
        assertEquals(1, Settings(mcpToolCallTimeoutSeconds = 0).getMcpToolCallTimeoutSeconds())
        assertEquals(1, Settings(mcpToolCallTimeoutSeconds = -10).getMcpToolCallTimeoutSeconds())
    }
}

