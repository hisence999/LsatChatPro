package me.rerere.rikkahub.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import me.rerere.rikkahub.data.backup.BackupCoordinator
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AutoBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val backupCoordinator: BackupCoordinator by inject()

    override suspend fun doWork(): Result {
        runCatching {
            backupCoordinator.runAutoBackupIfDue()
        }.onFailure { err ->
            Log.w(TAG, "auto backup failed: ${err.message}", err)
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "AutoBackupWorker"
        const val WORK_NAME = "auto_backup"
    }
}

