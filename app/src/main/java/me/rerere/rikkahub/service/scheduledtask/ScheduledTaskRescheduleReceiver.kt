package me.rerere.rikkahub.service.scheduledtask

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class ScheduledTaskRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val request = OneTimeWorkRequestBuilder<ScheduledTaskRescheduleWorker>()
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "scheduled_task_reschedule",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}

