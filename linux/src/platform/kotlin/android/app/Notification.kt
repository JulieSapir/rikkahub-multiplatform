package android.app

import android.content.Context
import android.content.Intent
import android.util.Log
import java.awt.GraphicsEnvironment
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap

/**
 * 通知在桌面端落到系统托盘气泡（java.awt.SystemTray），没有托盘时只打日志。
 * Android 的渠道、图标、优先级这些概念在这里被压平，只为让上游构建通知的代码原样编译。
 */
class Notification internal constructor(
    val channelId: String,
    val title: String,
    val text: String,
    val ongoing: Boolean,
    val contentIntent: PendingIntent?,
)

class NotificationChannel(val id: String, val name: String, val importance: Int)

/** 桌面端没有 Activity 栈，PendingIntent 只作为“打开某个东西”的回调句柄保留。 */
class PendingIntent internal constructor(val intent: Intent) {
    fun cancel() = Unit

    companion object {
        const val FLAG_ONE_SHOT = 0x40000000
        const val FLAG_NO_CREATE = 0x20000000
        const val FLAG_CANCEL_CURRENT = 0x10000000
        const val FLAG_UPDATE_CURRENT = 0x08000000
        const val FLAG_IMMUTABLE = 0x04000000
        const val FLAG_MUTABLE = 0x02000000

        fun getActivity(context: Context, requestCode: Int, intent: Intent, flags: Int) = PendingIntent(intent)

        fun getService(context: Context, requestCode: Int, intent: Intent, flags: Int) = PendingIntent(intent)

        fun getBroadcast(context: Context, requestCode: Int, intent: Intent, flags: Int) = PendingIntent(intent)
    }
}

class NotificationManager {
    private val channels = ConcurrentHashMap<String, NotificationChannel>()
    private val shown = ConcurrentHashMap<Int, Notification>()

    fun createNotificationChannel(channel: NotificationChannel) {
        channels[channel.id] = channel
    }

    fun getNotificationChannel(id: String): NotificationChannel? = channels[id]

    fun notify(id: Int, notification: Notification) {
        shown[id] = notification
        display(notification)
    }

    fun cancel(id: Int) {
        shown.remove(id)
    }

    fun cancelAll() {
        shown.clear()
    }

    private fun display(notification: Notification) {
        val message = "${notification.title} · ${notification.text}".trim()
        val tray = DesktopTray.icon
        if (tray == null) {
            Log.i(TAG, "通知[${notification.channelId}] $message")
            return
        }
        // 常驻（进度）通知只更新内容，系统托盘没有“静默刷新”，因此仅在首次弹出
        if (notification.ongoing && notification.channelId in announced) return
        announced += notification.channelId
        runCatching {
            tray.displayMessage(notification.title.ifBlank { "RikkaHub" }, notification.text, TrayIcon.MessageType.NONE)
        }.onFailure {
            Log.w(TAG, "系统通知发送失败: ${it.message}")
        }
    }

    companion object {
        private const val TAG = "DesktopNotification"
        private val announced = ConcurrentHashMap.newKeySet<String>()
    }
}

/** 托盘图标只注册一次，供通知与“回到窗口”共用。 */
object DesktopTray {
    val icon: TrayIcon? by lazy { create() }

    private fun create(): TrayIcon? {
        if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) return null
        val icon = TrayIcon(BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), "RikkaHub")
        return runCatching {
            SystemTray.getSystemTray().add(icon)
            icon
        }.getOrNull()
    }
}

/** Context.getSystemService(NOTIFICATION_SERVICE) 用这一个实例。 */
internal val desktopNotificationManager = NotificationManager()
