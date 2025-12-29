package me.rerere.rikkahub.data.ai

enum class AIRequestSource {
    CHAT,
    TITLE_SUMMARY,
    CHAT_SUGGESTION,
    WELCOME_PHRASES,
    MEMORY_CONSOLIDATION,
    TRANSLATION,
    OCR,
    SCHEDULED_MESSAGE,
    SPONTANEOUS,
    OTHER,
}

fun AIRequestSource.displayNameZh(): String {
    return when (this) {
        AIRequestSource.CHAT -> "聊天"
        AIRequestSource.TITLE_SUMMARY -> "标题总结"
        AIRequestSource.CHAT_SUGGESTION -> "聊天建议"
        AIRequestSource.WELCOME_PHRASES -> "欢迎词"
        AIRequestSource.MEMORY_CONSOLIDATION -> "记忆整合"
        AIRequestSource.TRANSLATION -> "翻译"
        AIRequestSource.OCR -> "OCR"
        AIRequestSource.SCHEDULED_MESSAGE -> "定时消息"
        AIRequestSource.SPONTANEOUS -> "主动通知"
        AIRequestSource.OTHER -> "其他"
    }
}

