package androidx.core.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context

/**
 * AndroidX 通知兼容层在桌面端压成一层薄壳：Builder 收集的字段最终交给
 * [android.app.NotificationManager]，由它决定用托盘气泡还是日志呈现。
 *
 * 补齐这层 API 而不是 fork 上游 NotificationUtil，是为了让 ChatNotificationManager、
 * MediaCreationService 这些平台无关的编排代码保持原样。
 */
class NotificationCompat private constructor() {

    class BigTextStyle {
        fun bigText(text: CharSequence): BigTextStyle = this
    }

    class Builder(context: Context, private val channelId: String) {
        private var title: CharSequence? = null
        private var text: CharSequence? = null
        private var subText: CharSequence? = null
        private var ongoing = false
        private var contentIntent: PendingIntent? = null

        fun setContentTitle(value: CharSequence?): Builder = apply { title = value }

        fun setContentText(value: CharSequence?): Builder = apply { text = value }

        fun setSubText(value: CharSequence?): Builder = apply { subText = value }

        fun setSmallIcon(icon: Int): Builder = this

        fun setAutoCancel(value: Boolean): Builder = this

        fun setOngoing(value: Boolean): Builder = apply { ongoing = value }

        fun setOnlyAlertOnce(value: Boolean): Builder = this

        fun setVisibility(value: Int): Builder = this

        fun setCategory(value: String?): Builder = this

        fun setContentIntent(value: PendingIntent?): Builder = apply { contentIntent = value }

        fun setStyle(style: BigTextStyle): Builder = this

        fun setDefaults(defaults: Int): Builder = this

        fun setSilent(value: Boolean): Builder = this

        fun setProgress(max: Int, progress: Int, indeterminate: Boolean): Builder = this

        /** Android 15+ 的 Live Update 开关，桌面端没有对应概念 */
        fun setRequestPromotedOngoing(value: Boolean): Builder = this

        fun setShortCriticalText(value: CharSequence?): Builder = this

        fun build(): Notification = Notification(
            channelId = channelId,
            title = title?.toString().orEmpty(),
            text = (text ?: subText ?: "").toString(),
            ongoing = ongoing,
            contentIntent = contentIntent,
        )
    }

    companion object {
        const val VISIBILITY_PRIVATE = 0
        const val VISIBILITY_PUBLIC = 1
        const val VISIBILITY_SECRET = -1
        const val DEFAULT_ALL = -1
        const val CATEGORY_MESSAGE = "message"
        const val CATEGORY_EMAIL = "email"
        const val CATEGORY_PROGRESS = "progress"
        const val CATEGORY_NAVIGATION = "navigation"
        const val CATEGORY_STATUS = "status"
        const val CATEGORY_ALARM = "alarm"
        const val CATEGORY_CALL = "call"
        const val CATEGORY_ERROR = "err"
        const val CATEGORY_EVENT = "event"
        const val CATEGORY_RECOMMENDATION = "recommendation"
        const val CATEGORY_SERVICE = "service"
        const val CATEGORY_SOCIAL = "social"
    }
}

class NotificationChannelCompat private constructor(
    val id: String,
    val name: String,
    val importance: Int,
) {
    class Builder(private val id: String, private val importance: Int) {
        private var name: String = id

        fun setName(value: String): Builder = apply { name = value }

        fun build(): NotificationChannelCompat = NotificationChannelCompat(id, name, importance)
    }

    internal fun toPlatform(): NotificationChannel = NotificationChannel(id, name, importance)
}

class NotificationManagerCompat private constructor(private val context: Context) {

    private val manager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) ?: NotificationManager()

    fun notify(id: Int, notification: Notification) = manager.notify(id, notification)

    fun cancel(id: Int) = manager.cancel(id)

    fun cancelAll() = manager.cancelAll()

    fun createNotificationChannel(channel: NotificationChannelCompat) =
        manager.createNotificationChannel(channel.toPlatform())

    companion object {
        const val IMPORTANCE_UNSPECIFIED = -1000
        const val IMPORTANCE_NONE = 0
        const val IMPORTANCE_MIN = 1
        const val IMPORTANCE_LOW = 2
        const val IMPORTANCE_DEFAULT = 3
        const val IMPORTANCE_HIGH = 4

        fun from(context: Context): NotificationManagerCompat = NotificationManagerCompat(context)
    }
}
