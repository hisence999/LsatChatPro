package me.rerere.rikkahub.service

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid
import me.rerere.rikkahub.CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity

private const val EXTRA_CONVERSATION_ID = "conversationId"
private const val EXTRA_SESSION_ID = "sessionId"

private const val ANDROID_EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

enum class ChatLiveUpdateState {
    INFERENCE,
    OUTPUT,
    DONE,
    ERROR,
}

internal fun ChatLiveUpdateState.isOngoing(): Boolean {
    return this == ChatLiveUpdateState.INFERENCE || this == ChatLiveUpdateState.OUTPUT
}

private fun ChatLiveUpdateState.isAutoCancel(): Boolean {
    return this == ChatLiveUpdateState.DONE || this == ChatLiveUpdateState.ERROR
}

class ChatLiveUpdateNotifier(
    private val context: Context,
) {
    fun notify(
        conversationId: Uuid,
        sessionId: Long,
        state: ChatLiveUpdateState,
        contentText: String?,
        bigText: String?,
    ) {
        if (ChatLiveUpdateDismissalTracker.isDismissed(conversationId, sessionId)) return
        if (!hasPostNotificationsPermission()) return

        val builder = NotificationCompat.Builder(context, CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(stateTitle(state))
            .setContentText(contentText.orEmpty())
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setOngoing(state.isOngoing())
            .setAutoCancel(state.isAutoCancel())
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(conversationPendingIntent(conversationId))
            .setDeleteIntent(deleteIntent(conversationId, sessionId))

        if (state.isOngoing()) {
            builder.setProgress(0, 0, true)
            builder.requestPromotedOngoingCompat(true)
        }

        if (!bigText.isNullOrBlank()) {
            builder.setStyle(
                NotificationCompat.BigTextStyle().bigText(bigText)
            )
        }

        NotificationManagerCompat.from(context).notify(notificationId(conversationId), builder.build())
    }

    fun cancel(conversationId: Uuid) {
        NotificationManagerCompat.from(context).cancel(notificationId(conversationId))
    }

    private fun hasPostNotificationsPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun stateTitle(state: ChatLiveUpdateState): String {
        return when (state) {
            ChatLiveUpdateState.INFERENCE -> context.getString(R.string.notification_live_update_inference)
            ChatLiveUpdateState.OUTPUT -> context.getString(R.string.notification_live_update_output)
            ChatLiveUpdateState.DONE -> context.getString(R.string.notification_live_update_done)
            ChatLiveUpdateState.ERROR -> context.getString(R.string.notification_live_update_error)
        }
    }

    private fun conversationPendingIntent(conversationId: Uuid): PendingIntent {
        val intent = Intent(context, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("conversationId", conversationId.toString())
        }
        return PendingIntent.getActivity(
            context,
            conversationId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun deleteIntent(conversationId: Uuid, sessionId: Long): PendingIntent {
        val intent = Intent(context, ChatLiveUpdateDismissReceiver::class.java).apply {
            putExtra(EXTRA_CONVERSATION_ID, conversationId.toString())
            putExtra(EXTRA_SESSION_ID, sessionId)
        }
        return PendingIntent.getBroadcast(
            context,
            conversationId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        fun notificationId(conversationId: Uuid): Int = conversationId.hashCode()
    }
}

private fun NotificationCompat.Builder.requestPromotedOngoingCompat(request: Boolean) {
    runCatching {
        javaClass
            .getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
            .invoke(this, request)
    }.getOrElse {
        addExtras(
            Bundle().apply {
                putBoolean(ANDROID_EXTRA_REQUEST_PROMOTED_ONGOING, request)
            }
        )
    }
}

internal object ChatLiveUpdateDismissalTracker {
    private val dismissedSessionByConversationId = ConcurrentHashMap<Uuid, Long>()

    fun isDismissed(conversationId: Uuid, sessionId: Long): Boolean {
        return dismissedSessionByConversationId[conversationId] == sessionId
    }

    fun clear(conversationId: Uuid) {
        dismissedSessionByConversationId.remove(conversationId)
    }

    fun markDismissed(conversationId: Uuid, sessionId: Long) {
        dismissedSessionByConversationId[conversationId] = sessionId
    }
}

class ChatLiveUpdateDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val conversationIdStr = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: return
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        if (sessionId <= 0L) return
        val conversationId = runCatching { Uuid.parse(conversationIdStr) }.getOrNull() ?: return

        ChatLiveUpdateDismissalTracker.markDismissed(conversationId, sessionId)
        NotificationManagerCompat.from(context).cancel(ChatLiveUpdateNotifier.notificationId(conversationId))
    }
}
