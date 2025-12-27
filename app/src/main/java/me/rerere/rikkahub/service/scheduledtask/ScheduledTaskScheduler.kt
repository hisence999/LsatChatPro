package me.rerere.rikkahub.service.scheduledtask

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.dao.ScheduledTaskDao
import me.rerere.rikkahub.data.db.entity.ScheduledTaskEntity
import java.util.concurrent.TimeUnit

private const val WORK_NAME_PREFIX = "scheduled_task_work_"
private const val MIN_BACKOFF_SECONDS = 30L

class ScheduledTaskScheduler(
    private val context: Context,
    private val taskDao: ScheduledTaskDao,
) {
    suspend fun schedule(taskId: String) = withContext(Dispatchers.IO) {
        val task = taskDao.getById(taskId) ?: return@withContext
        schedule(task)
    }

    suspend fun schedule(task: ScheduledTaskEntity) = withContext(Dispatchers.IO) {
        cancelInternal(task.id)
        val now = System.currentTimeMillis()
        val nextRunAt = ScheduledTaskNextRunCalculator.computeNextRunAtMillis(task, now)
        taskDao.updateRunFields(
            id = task.id,
            lastRunAt = task.lastRunAt,
            lastScheduledFor = task.lastScheduledFor,
            nextRunAt = nextRunAt,
            lastErrorCode = task.lastErrorCode,
            lastErrorAt = task.lastErrorAt,
        )

        if (!task.enabled) return@withContext
        if (nextRunAt == null) return@withContext
        scheduleInternal(taskId = task.id, scheduledFor = nextRunAt, accuracyMode = task.accuracyMode)
    }

    suspend fun rescheduleAll() = withContext(Dispatchers.IO) {
        val tasks = taskDao.getAllEnabled()
        tasks.forEach { task ->
            schedule(task)
        }
    }

    fun cancel(taskId: String) {
        cancelInternal(taskId)
    }

    fun scheduleNextAfterRun(taskId: String, scheduledFor: Long, accuracyMode: Int) {
        if (scheduledFor <= 0L) return
        val useExactAlarm = accuracyMode == ScheduledTaskAccuracyMode.EXACT && canScheduleExactAlarms()
        if (useExactAlarm) {
            cancelAlarm(taskId)
            scheduleExactAlarm(taskId, scheduledFor)
        } else {
            cancelAlarm(taskId)
            enqueueWork(taskId, scheduledFor, existingWorkPolicy = ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
    }

    private fun cancelInternal(taskId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(taskId))
        cancelAlarm(taskId)
    }

    private fun scheduleInternal(taskId: String, scheduledFor: Long, accuracyMode: Int) {
        val useExactAlarm = accuracyMode == ScheduledTaskAccuracyMode.EXACT && canScheduleExactAlarms()
        if (useExactAlarm) {
            scheduleExactAlarm(taskId, scheduledFor)
        } else {
            enqueueWork(taskId, scheduledFor, existingWorkPolicy = ExistingWorkPolicy.REPLACE)
        }
    }

    private fun enqueueWork(taskId: String, scheduledFor: Long, existingWorkPolicy: ExistingWorkPolicy) {
        val now = System.currentTimeMillis()
        val delayMillis = (scheduledFor - now).coerceAtLeast(0L)

        val request = OneTimeWorkRequestBuilder<ScheduledTaskWorker>()
            .setInputData(
                workDataOf(
                    ScheduledTaskWorkKeys.TASK_ID to taskId,
                    ScheduledTaskWorkKeys.SCHEDULED_FOR to scheduledFor,
                )
            )
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, MIN_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(taskId),
            existingWorkPolicy,
            request
        )
    }

    private fun scheduleExactAlarm(taskId: String, scheduledFor: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildAlarmPendingIntent(taskId, scheduledFor)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, scheduledFor, pendingIntent)
    }

    private fun cancelAlarm(taskId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ScheduledTaskAlarmReceiver::class.java).apply {
            putExtra(ScheduledTaskWorkKeys.TASK_ID, taskId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun buildAlarmPendingIntent(taskId: String, scheduledFor: Long): PendingIntent {
        val intent = Intent(context, ScheduledTaskAlarmReceiver::class.java).apply {
            putExtra(ScheduledTaskWorkKeys.TASK_ID, taskId)
            putExtra(ScheduledTaskWorkKeys.SCHEDULED_FOR, scheduledFor)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun workName(taskId: String): String = WORK_NAME_PREFIX + taskId

    private fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }
}
