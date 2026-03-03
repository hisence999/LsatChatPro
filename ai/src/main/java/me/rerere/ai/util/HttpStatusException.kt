package me.rerere.ai.util

class HttpStatusException(
    val statusCode: Int,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
