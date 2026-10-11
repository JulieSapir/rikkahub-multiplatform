package android.provider

import android.content.Context

/**
 * 上游只用这两个 ACTION 跳到系统设置页（应用详情 / 使用情况访问），
 * 桌面端没有对应的面板，Activity 替身里 startActivity 会把它当作不支持的链接丢弃。
 */
object Settings {
    const val ACTION_APPLICATION_DETAILS_SETTINGS = "android.settings.APPLICATION_DETAILS_SETTINGS"
    const val ACTION_USAGE_ACCESS_SETTINGS = "android.settings.USAGE_ACCESS_SETTINGS"
    const val ACTION_APPLICATION_DISPLAY_SETTINGS = "android.settings.APPLICATION_DISPLAY_SETTINGS"

    object System {
        fun canDrawOverlays(context: Context): Boolean = true

        fun getInt(resolver: android.content.ContentResolver, name: String, default: Int): Int = default
    }

    object Secure {
        fun getString(resolver: android.content.ContentResolver, name: String): String? = null
    }
}
