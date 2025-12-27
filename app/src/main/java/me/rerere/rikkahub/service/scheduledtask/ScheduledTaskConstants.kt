package me.rerere.rikkahub.service.scheduledtask

object ScheduledTaskRepeatType {
    const val ONCE = 0
    const val DAILY = 1
    const val WEEKLY = 2
    const val MONTHLY = 3
    const val INTERVAL = 4
}

object ScheduledTaskIntervalUnit {
    const val HOURS = 0
    const val DAYS = 1
}

object ScheduledTaskAccuracyMode {
    const val ECO = 0
    const val EXACT = 1
}

object ScheduledTaskOverrideType {
    const val INHERIT = 0
    const val OFF = 1
    const val OVERRIDE = 2
}

/**
 * Search override type is slightly more complex than generic [ScheduledTaskOverrideType],
 * because we also need a per-task "prefer built-in search" toggle.
 *
 * Values 0/1/2 are kept compatible with [ScheduledTaskOverrideType] for existing data.
 */
object ScheduledTaskSearchOverrideType {
    const val INHERIT = 0
    const val OFF = 1
    const val OVERRIDE = 2

    /** Prefer model built-in search (if supported), otherwise fall back to OVERRIDE provider. */
    const val OVERRIDE_PREFER_BUILTIN = 3
}

object ScheduledTaskRunStatus {
    const val PENDING = 0
    const val SUCCESS = 1
    const val FAILED = 2
}

object ScheduledTaskWorkKeys {
    const val TASK_ID = "taskId"
    const val SCHEDULED_FOR = "scheduledFor"
}
