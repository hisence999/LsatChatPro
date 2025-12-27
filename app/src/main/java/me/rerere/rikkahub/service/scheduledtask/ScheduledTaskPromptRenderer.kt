package me.rerere.rikkahub.service.scheduledtask

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object ScheduledTaskPromptRenderer {
    fun render(
        template: String,
        scheduledForMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(scheduledForMillis), zoneId)
        val date = zdt.toLocalDate().toString()
        val time = zdt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm", locale))
        val weekday = zdt.dayOfWeek.getDisplayName(TextStyle.FULL, locale)

        return template
            .replace("{date}", date)
            .replace("{time}", time)
            .replace("{weekday}", weekday)
    }
}

