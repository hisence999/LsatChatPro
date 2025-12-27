package me.rerere.rikkahub.service.scheduledtask

import me.rerere.rikkahub.data.db.entity.ScheduledTaskEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

object ScheduledTaskNextRunCalculator {
    fun computeNextRunAtMillis(
        task: ScheduledTaskEntity,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        val now = Instant.ofEpochMilli(nowMillis)

        val timeOfDay = task.timeOfDayMinutes.toLocalTimeOrNull() ?: return null
        return when (task.repeatType) {
            ScheduledTaskRepeatType.ONCE,
            ScheduledTaskRepeatType.DAILY -> {
                nextDaily(now, zoneId, timeOfDay).toEpochMilli()
            }

            ScheduledTaskRepeatType.WEEKLY -> {
                if (task.weeklyMask == 0) return null
                nextWeekly(now, zoneId, timeOfDay, task.weeklyMask).toEpochMilli()
            }

            ScheduledTaskRepeatType.MONTHLY -> {
                if (task.monthlyDay == 0) return null
                nextMonthly(now, zoneId, timeOfDay, task.monthlyDay).toEpochMilli()
            }

            ScheduledTaskRepeatType.INTERVAL -> {
                val intervalMillis = intervalMillis(task.intervalValue, task.intervalUnit) ?: return null
                nextInterval(
                    baseMillis = task.lastScheduledFor ?: nowMillis,
                    nowMillis = nowMillis,
                    intervalMillis = intervalMillis,
                    zoneId = zoneId,
                    timeOfDay = timeOfDay,
                )?.toEpochMilli()
            }

            else -> null
        }
    }

    private fun Int.toLocalTimeOrNull(): LocalTime? {
        if (this !in 0..(24 * 60 - 1)) return null
        return LocalTime.of(this / 60, this % 60)
    }

    private fun nextDaily(now: Instant, zoneId: ZoneId, timeOfDay: LocalTime): Instant {
        val nowZdt = ZonedDateTime.ofInstant(now, zoneId)
        var candidate = zonedAt(nowZdt.toLocalDate(), timeOfDay, zoneId)
        if (!candidate.toInstant().isAfter(now)) {
            candidate = candidate.plusDays(1)
        }
        return candidate.toInstant()
    }

    private fun nextWeekly(now: Instant, zoneId: ZoneId, timeOfDay: LocalTime, weeklyMask: Int): Instant {
        val nowZdt = ZonedDateTime.ofInstant(now, zoneId)
        val startDate = nowZdt.toLocalDate()
        for (daysToAdd in 0..7) {
            val date = startDate.plusDays(daysToAdd.toLong())
            val weekdayMask = 1 shl (date.dayOfWeek.value - 1) // Monday=1..Sunday=7
            if (weeklyMask and weekdayMask == 0) continue

            val candidate = zonedAt(date, timeOfDay, zoneId).toInstant()
            if (candidate.isAfter(now)) return candidate
        }
        return zonedAt(startDate.plusWeeks(1), timeOfDay, zoneId).toInstant()
    }

    private fun nextMonthly(now: Instant, zoneId: ZoneId, timeOfDay: LocalTime, monthlyDay: Int): Instant {
        val nowZdt = ZonedDateTime.ofInstant(now, zoneId)
        val today = nowZdt.toLocalDate()

        fun computeForMonth(dateInMonth: LocalDate): Instant {
            val yearMonth = java.time.YearMonth.from(dateInMonth)
            val day = when {
                monthlyDay == -1 -> yearMonth.lengthOfMonth()
                monthlyDay <= 0 -> 1
                else -> monthlyDay.coerceAtMost(yearMonth.lengthOfMonth())
            }
            val candidateDate = yearMonth.atDay(day)
            return zonedAt(candidateDate, timeOfDay, zoneId).toInstant()
        }

        val thisMonth = computeForMonth(today)
        return if (thisMonth.isAfter(now)) {
            thisMonth
        } else {
            val nextMonthDate = today.plusMonths(1).withDayOfMonth(1)
            computeForMonth(nextMonthDate)
        }
    }

    private fun nextInterval(
        baseMillis: Long,
        nowMillis: Long,
        intervalMillis: Long,
        zoneId: ZoneId,
        timeOfDay: LocalTime,
    ): Instant? {
        if (intervalMillis <= 0) return null
        val now = Instant.ofEpochMilli(nowMillis)

        if (baseMillis == nowMillis) {
            return nextDaily(now, zoneId, timeOfDay)
        }

        var next = Instant.ofEpochMilli(baseMillis).plusMillis(intervalMillis)
        if (!next.isAfter(now)) {
            val diff = ChronoUnit.MILLIS.between(next, now).coerceAtLeast(0)
            val steps = (diff / intervalMillis) + 1
            next = next.plusMillis(steps * intervalMillis)
        }
        return next
    }

    private fun intervalMillis(value: Int, unit: Int): Long? {
        if (value <= 0) return null
        return when (unit) {
            ScheduledTaskIntervalUnit.HOURS -> value.toLong() * 60L * 60L * 1000L
            ScheduledTaskIntervalUnit.DAYS -> value.toLong() * 24L * 60L * 60L * 1000L
            else -> null
        }
    }

    private fun zonedAt(date: LocalDate, timeOfDay: LocalTime, zoneId: ZoneId): ZonedDateTime {
        val localDateTime = date.atTime(timeOfDay)
        return ZonedDateTime.of(localDateTime, zoneId)
    }
}
