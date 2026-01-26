package me.rerere.rikkahub.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import me.rerere.rikkahub.CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity

private const val TAG = "KeepAliveService"

class KeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == null) {
            // If the service is restarted without an intent (e.g., process death and the service was sticky),
            // we still MUST call startForeground() quickly or the system will crash the app process.
            // Stop promptly to avoid resurrecting an always-on keep-alive after app updates.
            startForegroundCompat(buildGeneratingNotification(activeCount = 1))
            stopAll()
            return START_NOT_STICKY
        }

        when (action) {
            ACTION_START_OR_UPDATE_GENERATION -> startOrUpdateGeneration(
                activeCount = intent.getIntExtra(EXTRA_ACTIVE_COUNT, 1).coerceAtLeast(1)
            )
            ACTION_FINISH_GENERATION -> finishGeneration(
                status = intent.getStringExtra(EXTRA_FINISH_STATUS)
            )
            ACTION_STOP -> stopAll()
            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return when (action) {
            // Redeliver the last intent on process death so we don't get restarted with a null intent
            // (which can trigger foreground-service startup timeout crashes if not handled perfectly).
            ACTION_START_OR_UPDATE_GENERATION -> START_REDELIVER_INTENT
            else -> START_NOT_STICKY
        }
    }

    private fun startOrUpdateGeneration(activeCount: Int) {
        currentMode = MODE_GENERATION
        startForegroundCompat(buildGeneratingNotification(activeCount))
    }

    private fun finishGeneration(status: String?) {
        currentMode = null
        when (status) {
            FINISH_STATUS_ERROR -> {
                val notification = buildGenerationFinishedNotification(
                    contentText = getString(R.string.notification_keep_alive_content_error)
                )
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
                stopSelf()
            }

            FINISH_STATUS_CANCELLED -> {
                val notification = buildGenerationFinishedNotification(
                    contentText = getString(R.string.notification_keep_alive_content_cancelled)
                )
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
                stopSelf()
            }

            else -> stopAll()
        }
    }

    private fun stopAll() {
        currentMode = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        stopSelf()
    }

    private fun ensureNotificationChannel() {
        val notificationManager = NotificationManagerCompat.from(this)
        val channel = NotificationChannelCompat
            .Builder(
                CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_LOW
            )
            .setName(getString(R.string.notification_channel_chat_live_update))
            .setVibrationEnabled(false)
            .setShowBadge(false)
            .build()
        notificationManager.createNotificationChannel(channel)
    }

    private fun startForegroundCompat(notification: Notification) {
        try {
            ensureNotificationChannel()
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
            )
        } catch (t: Throwable) {
            // Don't swallow the root cause: it helps debug OEM/permission/channel issues.
            Log.e(TAG, "startForegroundCompat failed: ${t.message}", t)
            // Try one more time with a minimal generation-notification.
            runCatching {
                ensureNotificationChannel()
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildGeneratingNotification(activeCount = 1),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    } else {
                        0
                    }
                )
            }.onFailure { retryError ->
                Log.e(TAG, "startForegroundCompat retry failed: ${retryError.message}", retryError)
            }
        }
    }

    private fun buildGeneratingNotification(activeCount: Int): Notification {
        val contentText = if (activeCount <= 1) {
            getString(R.string.notification_keep_alive_content_generating)
        } else {
            getString(R.string.notification_keep_alive_content_generating_multi, activeCount)
        }
        return NotificationCompat.Builder(this, CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(0, 0, true)
            .setContentIntent(mainPendingIntent())
            .build()
    }

    private fun buildGenerationFinishedNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(mainPendingIntent())
            .build()
    }

    private fun mainPendingIntent(): PendingIntent {
        val intent = Intent(this, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        private const val NOTIFICATION_ID = 0x4C4B // "LK"

        @Volatile
        private var currentMode: String? = null

        private const val MODE_GENERATION = "generation"

        private const val ACTION_START_OR_UPDATE_GENERATION =
            "me.rerere.rikkahub.action.KEEP_ALIVE_START_OR_UPDATE_GENERATION"
        private const val ACTION_FINISH_GENERATION = "me.rerere.rikkahub.action.KEEP_ALIVE_FINISH_GENERATION"
        private const val ACTION_STOP = "me.rerere.rikkahub.action.KEEP_ALIVE_STOP"

        private const val EXTRA_ACTIVE_COUNT = "activeCount"
        private const val EXTRA_FINISH_STATUS = "finishStatus"

        private const val FINISH_STATUS_OK = "ok"
        private const val FINISH_STATUS_CANCELLED = "cancelled"
        private const val FINISH_STATUS_ERROR = "error"

        fun stop(context: Context) {
            context.startServiceBestEffort(
                Intent(context, KeepAliveService::class.java).setAction(ACTION_STOP)
            )
        }

        fun startOrUpdateGeneration(context: Context, activeCount: Int) {
            if (!context.hasPostNotificationsPermissionCompat()) return
            context.startServiceOrForegroundServiceBestEffort(
                Intent(context, KeepAliveService::class.java)
                    .setAction(ACTION_START_OR_UPDATE_GENERATION)
                    .putExtra(EXTRA_ACTIVE_COUNT, activeCount)
            )
        }

        fun finishGenerationOk(context: Context) {
            if (!context.hasPostNotificationsPermissionCompat()) return
            context.startServiceBestEffort(
                Intent(context, KeepAliveService::class.java)
                    .setAction(ACTION_FINISH_GENERATION)
                    .putExtra(EXTRA_FINISH_STATUS, FINISH_STATUS_OK)
            )
        }

        fun finishGenerationCancelled(context: Context) {
            if (!context.hasPostNotificationsPermissionCompat()) return
            context.startServiceBestEffort(
                Intent(context, KeepAliveService::class.java)
                    .setAction(ACTION_FINISH_GENERATION)
                    .putExtra(EXTRA_FINISH_STATUS, FINISH_STATUS_CANCELLED)
            )
        }

        fun finishGenerationError(context: Context) {
            if (!context.hasPostNotificationsPermissionCompat()) return
            context.startServiceBestEffort(
                Intent(context, KeepAliveService::class.java)
                    .setAction(ACTION_FINISH_GENERATION)
                    .putExtra(EXTRA_FINISH_STATUS, FINISH_STATUS_ERROR)
            )
        }
    }
}

private fun Context.startForegroundServiceBestEffort(intent: Intent) {
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

private fun Context.startServiceOrForegroundServiceBestEffort(intent: Intent) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        startServiceBestEffort(intent)
        return
    }

    try {
        startService(intent)
    } catch (e: IllegalStateException) {
        // Background execution limits: fall back to foreground-service start.
        startForegroundServiceBestEffort(intent)
    } catch (t: Throwable) {
        // KeepAlive is best-effort; don't crash the app on OEM/framework quirks.
        Log.e(TAG, "startServiceOrForegroundServiceBestEffort failed: ${t.message}", t)
    }
}

private fun Context.startServiceBestEffort(intent: Intent) {
    runCatching {
        startService(intent)
    }
}

private fun Context.hasPostNotificationsPermissionCompat(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ActivityCompat.checkSelfPermission(
        this,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}
