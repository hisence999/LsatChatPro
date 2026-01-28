package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantUISettings
import org.junit.Assert.assertEquals
import org.junit.Test

class EffectiveDisplaySettingTest {
    @Test
    fun getEffectiveDisplaySetting_showAssistantName_defaultsToGlobal() {
        val settings = Settings(displaySetting = DisplaySetting(showModelName = false))
        val assistant = Assistant(uiSettings = AssistantUISettings(showAssistantName = null))
        val effective = settings.getEffectiveDisplaySetting(assistant)
        assertEquals(false, effective.showModelName)
    }

    @Test
    fun getEffectiveDisplaySetting_showAssistantName_overridesGlobal() {
        val settings = Settings(displaySetting = DisplaySetting(showModelName = false))
        val assistant = Assistant(uiSettings = AssistantUISettings(showAssistantName = true))
        val effective = settings.getEffectiveDisplaySetting(assistant)
        assertEquals(true, effective.showModelName)
    }
}

