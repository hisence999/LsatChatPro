package me.rerere.rikkahub.service.scheduledtask

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ScheduledTaskRescheduleWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val scheduler: ScheduledTaskScheduler by inject()

    override suspend fun doWork(): Result {
        return runCatching {
            scheduler.rescheduleAll()
            Result.success()
        }.getOrElse {
            it.printStackTrace()
            Result.retry()
        }
    }
}

