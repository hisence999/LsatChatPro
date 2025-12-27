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

object ScheduledTaskRunStatus {
    const val PENDING = 0
    const val SUCCESS = 1
    const val FAILED = 2
}

object ScheduledTaskWorkKeys {
    const val TASK_ID = "taskId"
    const val SCHEDULED_FOR = "scheduledFor"
}

