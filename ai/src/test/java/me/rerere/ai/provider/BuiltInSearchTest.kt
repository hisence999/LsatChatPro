package me.rerere.ai.provider

import org.junit.Test

class BuiltInSearchTest {
    @Test
    fun testClaudeBuiltInSearchRequiresOfficialHost() {
        val model = Model(modelId = "claude-3.7-sonnet")
        val officialProvider = ProviderSetting.Claude(baseUrl = "https://api.anthropic.com/v1")
        val unofficialProvider = ProviderSetting.Claude(baseUrl = "https://openrouter.ai/api/v1")

        assert(model.supportsBuiltInSearch(officialProvider))
        assert(!model.supportsBuiltInSearch(unofficialProvider))
    }

    @Test
    fun testExplicitClaudeToolStillChecksProviderHost() {
        val model = Model(
            modelId = "custom-claude",
            tools = setOf(BuiltInTools.ClaudeWebSearch)
        )
        val officialProvider = ProviderSetting.Claude(baseUrl = "https://api.anthropic.com/v1")
        val unofficialProvider = ProviderSetting.Claude(baseUrl = "https://example.com/v1")

        assert(model.supportsBuiltInSearch(officialProvider))
        assert(!model.supportsBuiltInSearch(unofficialProvider))
    }

    @Test
    fun testGeminiBuiltInSearchStillWorksByModelId() {
        val model = Model(modelId = "gemini-2.5-pro")

        assert(model.supportsBuiltInSearch())
    }

    @Test
    fun testExplicitGenericSearchToolAlwaysSupported() {
        val model = Model(
            modelId = "unknown-model",
            tools = setOf(BuiltInTools.Search)
        )

        assert(model.supportsBuiltInSearch())
    }
}
