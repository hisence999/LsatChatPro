package me.rerere.rikkahub.service.scheduledtask

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class ScheduledTaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(ScheduledTaskWorkKeys.TASK_ID) ?: return
        val scheduledFor = intent.getLongExtra(ScheduledTaskWorkKeys.SCHEDULED_FOR, -1L)
        if (scheduledFor <= 0L) return

        val request = OneTimeWorkRequestBuilder<ScheduledTaskWorker>()
            .setInputData(
                workDataOf(
                    ScheduledTaskWorkKeys.TASK_ID to taskId,
                    ScheduledTaskWorkKeys.SCHEDULED_FOR to scheduledFor,
                )
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "scheduled_task_work_$taskId",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }
}
