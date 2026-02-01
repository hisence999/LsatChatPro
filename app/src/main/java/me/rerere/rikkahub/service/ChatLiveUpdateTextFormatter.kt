package me.rerere.rikkahub.service

internal object ChatLiveUpdateTextFormatter {
    fun normalizePreviewText(text: String): String {
        if (text.isBlank()) return ""

        val builder = StringBuilder(text.length)
        var lastWasSpace = false
        for (ch in text) {
            if (ch.isWhitespace()) {
                if (!lastWasSpace) {
                    builder.append(' ')
                    lastWasSpace = true
                }
            } else {
                builder.append(ch)
                lastWasSpace = false
            }
        }
        return builder.toString().trim()
    }
}

