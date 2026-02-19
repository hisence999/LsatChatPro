package me.rerere.ai.util

class RawResponseException(
    message: String,
    val rawResponse: String,
    cause: Throwable? = null,
) : Exception(message, cause)

