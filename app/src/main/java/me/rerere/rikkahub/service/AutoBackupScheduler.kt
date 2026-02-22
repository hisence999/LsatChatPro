package me.rerere.rikkahub.service

import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collect
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.backup.BackupCoordinator
import me.rerere.rikkahub.data.datastore.SettingsStore
import java.util.concurrent.TimeUnit

class AutoBackupScheduler(
    private val context: Context,
    private val appScope: AppScope,
    private val backupCoordinator: BackupCoordinator,
    private val settingsStore: SettingsStore,
) {
    private var loopJob: Job? = null

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                startLoop()
            }
            Lifecycle.Event.ON_STOP -> {
                stopLoop()
            }
            else -> Unit
        }
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
    }

    private fun startLoop() {
        if (loopJob?.isActive == true) return
        loopJob = appScope.launch {
            val settingsJob = launch {
                settingsStore.settingsFlow
                    .map { settings ->
                        AutoBackupConfigKey(
                            webDavEnabled = settings.webDavConfig.autoEnabled,
                            webDavIntervalDays = settings.webDavConfig.autoIntervalDays,
                            webDavMaxCount = settings.webDavConfig.autoMaxCount,
                            webDavLastSuccessAt = settings.webDavConfig.lastAutoSuccessAt,
                            objectStorageEnabled = settings.objectStorageConfig.autoEnabled,
                            objectStorageIntervalDays = settings.objectStorageConfig.autoIntervalDays,
                            objectStorageMaxCount = settings.objectStorageConfig.autoMaxCount,
                            objectStorageLastSuccessAt = settings.objectStorageConfig.lastAutoSuccessAt,
                        )
                    }
                    .distinctUntilChanged()
                    .collect {
                        runCatching { tryScheduleOnce() }
                            .onFailure { err -> Log.w(TAG, "schedule failed: ${err.message}", err) }
                    }
            }

            val tickerJob = launch {
                while (isActive) {
                    runCatching { tryScheduleOnce() }
                        .onFailure { err -> Log.w(TAG, "schedule failed: ${err.message}", err) }
                    delay(CHECK_INTERVAL_MS)
                }
            }

            settingsJob.join()
            tickerJob.join()
        }
    }

    private fun stopLoop() {
        loopJob?.cancel()
        loopJob = null
    }

    private fun tryScheduleOnce() {
        val now = System.currentTimeMillis()
        if (!backupCoordinator.hasDueAutoBackup(now)) return
        if (!backupCoordinator.isNetworkAvailable()) return

        WorkManager.getInstance(context).enqueueUniqueWork(
            AutoBackupWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<AutoBackupWorker>().build()
        )
    }

    companion object {
        private const val TAG = "AutoBackupScheduler"
        private val CHECK_INTERVAL_MS = TimeUnit.HOURS.toMillis(1)
    }
}

private data class AutoBackupConfigKey(
    val webDavEnabled: Boolean,
    val webDavIntervalDays: Int,
    val webDavMaxCount: Int,
    val webDavLastSuccessAt: Long?,
    val objectStorageEnabled: Boolean,
    val objectStorageIntervalDays: Int,
    val objectStorageMaxCount: Int,
    val objectStorageLastSuccessAt: Long?,
)
