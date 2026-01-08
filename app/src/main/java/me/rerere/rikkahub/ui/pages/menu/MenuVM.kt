package me.rerere.rikkahub.ui.pages.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.repository.ConversationRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.uuid.Uuid

enum class TimeLabel {
    EARLY_BIRD,      // 5am-11am
    DAYTIME_CHATTER, // 11am-6pm
    NIGHT_OWL        // 6pm-5am
}

class MenuVM(
    private val conversationRepository: ConversationRepository,
    private val settingsStore: SettingsStore
) : ViewModel() {

    val currentAssistant = settingsStore.settingsFlow
        .map { it.getCurrentAssistant() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val stats: StateFlow<MenuStats> = combine(
        conversationRepository.getConversationCountFlow(),
        conversationRepository.getDailyActivityDatesFlow(), // Uses persistent activity table instead of conversation dates
        conversationRepository.getMostActiveAssistantIdFlow(),
        conversationRepository.getEpisodeCountFlow(),
        conversationRepository.getConversationHoursFlow(),
        settingsStore.settingsFlow
    ) { flows ->
        val totalChats = flows[0] as Int
        val distinctDates = flows[1] as List<String>
        val mostActiveAssistantId = flows[2] as String?
        val episodeCount = flows[3] as Int
        val hours = flows[4] as List<Int>
        val settings = flows[5] as me.rerere.rikkahub.data.datastore.Settings
        
        // Daily Chat Streak
        val streak = calculateStreak(distinctDates)

        // Most Active Assistant
        val mostActiveAssistantName = mostActiveAssistantId?.let { id ->
            try {
                settings.assistants.find { it.id == Uuid.parse(id) }?.name
            } catch (e: Exception) {
                null
            }
        } ?: "None"

        // Time Label based on when user chats most
        val timeLabel = calculateTimeLabel(hours)

        MenuStats(
            totalChats = totalChats,
            totalMemories = episodeCount,
            mostActiveAssistantName = mostActiveAssistantName,
            totalAssistants = settings.assistants.size,
            dailyChatStreak = streak,
            timeLabel = timeLabel
        )
    }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MenuStats()
        )

    private fun calculateTimeLabel(hours: List<Int>): TimeLabel {
        if (hours.isEmpty()) return TimeLabel.DAYTIME_CHATTER
        
        // Count chats in each time period
        var earlyBird = 0   // 5am-11am (5-10)
        var daytime = 0     // 11am-6pm (11-17)
        var nightOwl = 0    // 6pm-5am (18-23, 0-4)
        
        for (hour in hours) {
            when (hour) {
                in 5..10 -> earlyBird++
                in 11..17 -> daytime++
                else -> nightOwl++ // 18-23 and 0-4
            }
        }
        
        return when {
            earlyBird >= daytime && earlyBird >= nightOwl -> TimeLabel.EARLY_BIRD
            daytime >= earlyBird && daytime >= nightOwl -> TimeLabel.DAYTIME_CHATTER
            else -> TimeLabel.NIGHT_OWL
        }
    }

    private fun calculateStreak(distinctDates: List<String>): Int {
        if (distinctDates.isEmpty()) return 0
        
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        val dates = distinctDates.mapNotNull { 
            try { LocalDate.parse(it, formatter) } catch (e: Exception) { null }
        }.sortedDescending()
        
        if (dates.isEmpty()) return 0
        
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        
        // Check if streak is active (chatted today or yesterday)
        val startDate = when {
            dates.contains(today) -> today
            dates.contains(yesterday) -> yesterday
            else -> return 0 // Streak broken
        }
        
        var streak = 0
        var current = startDate
        
        while (dates.contains(current)) {
            streak++
            current = current.minusDays(1)
        }
        
        return streak
    }
}

data class MenuStats(
    val totalChats: Int = 0,
    val totalMemories: Int = 0,
    val mostActiveAssistantName: String = "None",
    val totalAssistants: Int = 0,
    val dailyChatStreak: Int = 0,
    val timeLabel: TimeLabel = TimeLabel.DAYTIME_CHATTER
)
