package me.rerere.rikkahub.data.backup

import me.rerere.rikkahub.data.datastore.ObjectStorageConfig
import me.rerere.rikkahub.data.datastore.WebDavConfig
import java.util.concurrent.TimeUnit

object AutoBackupPolicy {
    fun isDue(
        lastSuccessAt: Long?,
        intervalDays: Int,
        now: Long,
    ): Boolean {
        val safeIntervalDays = intervalDays.coerceAtLeast(1)
        val intervalMs = TimeUnit.DAYS.toMillis(safeIntervalDays.toLong())
        val last = lastSuccessAt ?: return true
        return now - last >= intervalMs
    }

    fun canRunWebDav(config: WebDavConfig, now: Long): Boolean {
        if (!config.autoEnabled) return false
        if (config.url.isBlank()) return false
        return isDue(
            lastSuccessAt = config.lastAutoSuccessAt,
            intervalDays = config.autoIntervalDays,
            now = now,
        )
    }

    fun canRunObjectStorage(config: ObjectStorageConfig, now: Long): Boolean {
        if (!config.autoEnabled) return false
        if (config.endpoint.isBlank()) return false
        if (config.accessKeyId.isBlank()) return false
        if (config.secretAccessKey.isBlank()) return false
        if (config.bucket.isBlank()) return false
        if (config.region.isBlank()) return false
        return isDue(
            lastSuccessAt = config.lastAutoSuccessAt,
            intervalDays = config.autoIntervalDays,
            now = now,
        )
    }
}

