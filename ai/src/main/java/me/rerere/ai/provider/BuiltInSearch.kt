package me.rerere.ai.provider

import me.rerere.ai.registry.ModelRegistry

fun Model.supportsBuiltInSearch(): Boolean {
    if (tools.any { tool ->
            tool == BuiltInTools.Search || tool == BuiltInTools.ClaudeWebSearch
        }) {
        return true
    }

    return ModelRegistry.GEMINI_SERIES.match(modelId) || ModelRegistry.CLAUDE_SERIES.match(modelId)
}

fun Model.preferredBuiltInSearchTool(): BuiltInTools? {
    return when {
        tools.contains(BuiltInTools.ClaudeWebSearch) -> BuiltInTools.ClaudeWebSearch
        tools.contains(BuiltInTools.Search) -> BuiltInTools.Search
        ModelRegistry.CLAUDE_SERIES.match(modelId) -> BuiltInTools.ClaudeWebSearch
        ModelRegistry.GEMINI_SERIES.match(modelId) -> BuiltInTools.Search
        else -> null
    }
}

fun Model.ensureBuiltInSearchTool(): Model {
    val tool = preferredBuiltInSearchTool() ?: return this
    return if (tools.contains(tool)) this else copy(tools = tools + tool)
}

fun Model.withoutBuiltInSearchTools(): Model {
    val filtered = tools.filterNot { tool ->
        tool == BuiltInTools.Search || tool == BuiltInTools.ClaudeWebSearch
    }.toSet()
    return if (filtered == tools) this else copy(tools = filtered)
}
