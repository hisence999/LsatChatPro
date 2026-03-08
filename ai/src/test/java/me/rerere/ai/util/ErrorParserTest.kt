package me.rerere.ai.util

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorParserTest {

    @Test
    fun `parseErrorDetail keeps numeric 429 status code`() {
        val error = buildJsonObject {
            put("error", buildJsonObject {
                put("code", 429)
                put("message", "Too many requests")
            })
        }.parseErrorDetail()

        assertTrue(error is HttpStatusException)
        assertEquals(429, (error as HttpStatusException).statusCode)
        assertEquals("Too many requests", error.message)
    }

    @Test
    fun `parseErrorDetail maps rate limit type to 429`() {
        val error = buildJsonObject {
            put("type", "rate_limit_error")
            put("message", "Please retry later")
        }.parseErrorDetail()

        assertTrue(error is HttpStatusException)
        assertEquals(429, (error as HttpStatusException).statusCode)
        assertEquals("Please retry later", error.message)
    }

    @Test
    fun `parseErrorDetail maps resource exhausted to 429`() {
        val error = buildJsonObject {
            put("error", buildJsonObject {
                put("status", "RESOURCE_EXHAUSTED")
                put("message", "Quota temporarily exhausted")
            })
        }.parseErrorDetail()

        assertTrue(error is HttpStatusException)
        assertEquals(429, (error as HttpStatusException).statusCode)
        assertEquals("Quota temporarily exhausted", error.message)
    }

    @Test
    fun `parseErrorDetail falls back to plain exception for non rate limit errors`() {
        val error = buildJsonObject {
            put("error", buildJsonObject {
                put("message", "Invalid request")
            })
        }.parseErrorDetail()

        assertTrue(error is HttpException)
        assertEquals("Invalid request", error.message)
    }
}
