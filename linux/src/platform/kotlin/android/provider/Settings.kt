package android.provider

import android.content.Context

/**
 * 上游只会跳到这几个系统设置页（应用详情 / 使用情况访问 / 悬浮窗授权），
 * 桌面端没有对应的面板，Activity 替身里 startActivity 会把它当作不支持的链接丢弃。
 * canDrawOverlays 桌面端恒为 true：悬浮窗能力由窗口系统天然提供，不需要授权。
 */
object Settings {
    const val ACTION_APPLICATION_DETAILS_SETTINGS = "android.settings.APPLICATION_DETAILS_SETTINGS"
    const val ACTION_USAGE_ACCESS_SETTINGS = "android.settings.USAGE_ACCESS_SETTINGS"
    const val ACTION_APPLICATION_DISPLAY_SETTINGS = "android.settings.APPLICATION_DISPLAY_SETTINGS"
    const val ACTION_MANAGE_OVERLAY_PERMISSION = "android.settings.MANAGE_OVERLAY_PERMISSION"

    /** Android 上是 Settings 的静态方法，桌面端没有权限门禁，直接放行让悬浮窗开关可用 */
    fun canDrawOverlays(context: Context): Boolean = true

    object System {
        fun canDrawOverlays(context: Context): Boolean = true

        fun getInt(resolver: android.content.ContentResolver, name: String, default: Int): Int = default
    }

    object Secure {
        fun getString(resolver: android.content.ContentResolver, name: String): String? = null
    }
}
