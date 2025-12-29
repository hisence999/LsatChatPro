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
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import me.rerere.rikkahub.KEEP_ALIVE_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity

class KeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ALWAYS -> startAlways()
            ACTION_START_OR_UPDATE_GENERATION -> startOrUpdateGeneration(
                activeCount = intent.getIntExtra(EXTRA_ACTIVE_COUNT, 1).coerceAtLeast(1)
            )
            ACTION_FINISH_GENERATION -> finishGeneration(
                status = intent.getStringExtra(EXTRA_FINISH_STATUS)
            )
            ACTION_STOP_ALWAYS -> stopAlwaysIfRunning()
            ACTION_STOP -> stopAll()
        }
        return START_REDELIVER_INTENT
    }

    private fun startAlways() {
        currentMode = MODE_ALWAYS
        startForegroundCompat(buildAlwaysNotification())
    }

    private fun startOrUpdateGeneration(activeCount: Int) {
        currentMode = MODE_GENERATION
        startForegroundCompat(buildGeneratingNotification(activeCount))
    }

    private fun finishGeneration(status: String?) {
        currentMode = null
        val notification = when (status) {
            FINISH_STATUS_ERROR -> buildGenerationFinishedNotification(
                contentText = getString(R.string.notification_keep_alive_content_error)
            )
            FINISH_STATUS_CANCELLED -> buildGenerationFinishedNotification(
                contentText = getString(R.string.notification_keep_alive_content_cancelled)
            )
            else -> buildGenerationFinishedNotification(
                contentText = getString(R.string.notification_keep_alive_content_done)
            )
        }

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        stopSelf()
    }

    private fun stopAlwaysIfRunning() {
        if (currentMode != MODE_ALWAYS) return
        stopAll()
    }

    private fun stopAll() {
        currentMode = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        stopSelf()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (!hasPostNotificationsPermission()) return

        runCatching {
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
        }.onFailure {
            stopSelf()
        }
    }

    private fun hasPostNotificationsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun buildAlwaysNotification(): Notification {
        return NotificationCompat.Builder(this, KEEP_ALIVE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_statusbar)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_keep_alive_content_always))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(mainPendingIntent())
            .build()
    }

    private fun buildGeneratingNotification(activeCount: Int): Notification {
        val contentText = if (activeCount <= 1) {
            getString(R.string.notification_keep_alive_content_generating)
        } else {
            getString(R.string.notification_keep_alive_content_generating_multi, activeCount)
        }
        return NotificationCompat.Builder(this, KEEP_ALIVE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_statusbar)
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
        return NotificationCompat.Builder(this, KEEP_ALIVE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_statusbar)
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

        private const val MODE_ALWAYS = "always"
        private const val MODE_GENERATION = "generation"

        private const val ACTION_START_ALWAYS = "me.rerere.rikkahub.action.KEEP_ALIVE_START_ALWAYS"
        private const val ACTION_START_OR_UPDATE_GENERATION =
            "me.rerere.rikkahub.action.KEEP_ALIVE_START_OR_UPDATE_GENERATION"
        private const val ACTION_FINISH_GENERATION = "me.rerere.rikkahub.action.KEEP_ALIVE_FINISH_GENERATION"
        private const val ACTION_STOP_ALWAYS = "me.rerere.rikkahub.action.KEEP_ALIVE_STOP_ALWAYS"
        private const val ACTION_STOP = "me.rerere.rikkahub.action.KEEP_ALIVE_STOP"

        private const val EXTRA_ACTIVE_COUNT = "activeCount"
        private const val EXTRA_FINISH_STATUS = "finishStatus"

        private const val FINISH_STATUS_OK = "ok"
        private const val FINISH_STATUS_CANCELLED = "cancelled"
        private const val FINISH_STATUS_ERROR = "error"

        fun startAlways(context: Context) {
            if (!context.hasPostNotificationsPermissionCompat()) return
            context.startForegroundServiceBestEffort(
                Intent(context, KeepAliveService::class.java).setAction(ACTION_START_ALWAYS)
            )
        }

        fun stop(context: Context) {
            context.startServiceBestEffort(
                Intent(context, KeepAliveService::class.java).setAction(ACTION_STOP)
            )
        }

        fun stopAlways(context: Context) {
            context.startServiceBestEffort(
                Intent(context, KeepAliveService::class.java).setAction(ACTION_STOP_ALWAYS)
            )
        }

        fun startOrUpdateGeneration(context: Context, activeCount: Int) {
            if (!context.hasPostNotificationsPermissionCompat()) return
            context.startForegroundServiceBestEffort(
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
