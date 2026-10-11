package me.rerere.rikkahub.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlin.uuid.Uuid

/**
 * no-op
 */
object ChatGenerationForegroundService {
    const val NOTIFICATION_ID = 2002

    fun acquire(context: Context, generationId: Uuid, conversationId: Uuid): Boolean = true

    fun release(context: Context, generationId: Uuid) = Unit
}

object MediaCreationForegroundService {
    const val EXTRA_MEDIA_SESSION_ID = "mediaSessionId"
    const val NOTIFICATION_ID = 2003

    fun acquire(context: Context, recordId: Uuid, sessionId: Uuid): Boolean = true

    fun release(context: Context, recordId: Uuid) = Unit

    /** 桌面端点通知没有 Activity 可回，no-op */
    fun sessionPendingIntent(context: Context, sessionId: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            sessionId.hashCode(),
            Intent().apply { putExtra(EXTRA_MEDIA_SESSION_ID, sessionId) },
            PendingIntent.FLAG_IMMUTABLE,
        )
}
