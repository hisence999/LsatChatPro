package me.rerere.rikkahub.data.backup

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.dao.BackupLogDao
import me.rerere.rikkahub.data.db.entity.BackupLogEntity

private const val TAG = "BackupLogManager"
private const val BACKUP_LOG_KEEP_LATEST = 200

private const val BACKUP_LOG_MAX_MESSAGE_CHARS = 2_000
private const val BACKUP_LOG_MAX_ERROR_CHARS = 8_000

enum class BackupLogAction {
    BACKUP,
    CLEANUP,
}

enum class BackupLogTrigger {
    MANUAL,
    AUTO,
}

enum class BackupLogBackend {
    WEBDAV,
    OBJECT_STORAGE,
}

enum class BackupLogStatus {
    SUCCESS,
    FAILED,
    SKIPPED,
}

class BackupLogManager(
    private val dao: BackupLogDao,
) {
    fun observeRecent(limit: Int = BACKUP_LOG_KEEP_LATEST) = dao.observeRecent(limit)

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        runCatching { dao.clearAll() }
    }

    suspend fun log(
        action: BackupLogAction,
        trigger: BackupLogTrigger,
        backend: BackupLogBackend,
        status: BackupLogStatus,
        message: String,
        fileName: String? = null,
        fileSizeBytes: Long? = null,
        durationMs: Long? = null,
        error: Throwable? = null,
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val safeMessage = message.take(BACKUP_LOG_MAX_MESSAGE_CHARS)
            val safeError = error?.let { "[${it.javaClass.simpleName}] ${it.message}".take(BACKUP_LOG_MAX_ERROR_CHARS) }

            dao.insert(
                BackupLogEntity(
                    createdAt = System.currentTimeMillis(),
                    action = action.name,
                    trigger = trigger.name,
                    backend = backend.name,
                    status = status.name,
                    fileName = fileName,
                    fileSizeBytes = fileSizeBytes,
                    durationMs = durationMs,
                    message = safeMessage,
                    error = safeError,
                )
            )
            dao.pruneKeepLatest(BACKUP_LOG_KEEP_LATEST)
        }.onFailure {
            Log.w(TAG, "log failed: ${it.message}", it)
        }
    }
}

