package me.rerere.ai.provider

import me.rerere.ai.registry.ModelRegistry
import java.net.URI
import java.util.Locale

private val OFFICIAL_CLAUDE_API_HOSTS = setOf("api.anthropic.com")

private fun ProviderSetting?.supportsClaudeBuiltInSearchByHost(): Boolean {
    if (this !is ProviderSetting.Claude) return false
    val host = baseUrl.extractHostFromBaseUrl() ?: return false
    return host in OFFICIAL_CLAUDE_API_HOSTS || host.endsWith(".anthropic.com")
}

private fun String.extractHostFromBaseUrl(): String? {
    val normalized = trim()
    if (normalized.isBlank()) return null
    return runCatching { URI(normalized).host?.lowercase(Locale.US) }.getOrNull()
        ?: runCatching { URI("https://$normalized").host?.lowercase(Locale.US) }.getOrNull()
}

fun Model.supportsBuiltInSearch(providerSetting: ProviderSetting? = null): Boolean {
    if (tools.contains(BuiltInTools.Search)) {
        return true
    }
    if (tools.contains(BuiltInTools.ClaudeWebSearch)) {
        return providerSetting.supportsClaudeBuiltInSearchByHost()
    }

    return when {
        ModelRegistry.GEMINI_SERIES.match(modelId) -> true
        ModelRegistry.CLAUDE_SERIES.match(modelId) -> providerSetting.supportsClaudeBuiltInSearchByHost()
        else -> false
    }
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
