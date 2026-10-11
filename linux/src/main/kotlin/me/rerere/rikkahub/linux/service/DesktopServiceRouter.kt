package me.rerere.rikkahub.linux.service

import android.content.Context
import android.content.Intent
import android.util.Log
import me.rerere.rikkahub.service.WebServerService

/**
 * Android 用 Service 承载后台任务，设置页通过 `startService(Intent(this, X::class.java))` 投递。
 * 桌面端没有 Service：这里按 Intent 的组件名分流，把消息转给对应的桌面端实现。
 * 目前只有内嵌 Web 服务这一个消费者。
 */
object DesktopServiceRouter {
    fun route(context: Context, intent: Intent) {
        // Intent(context, X::class.java) 存的是全限定类名，setClassName 存的是 "包名/类名"，两种都归一到简单类名
        when (intent.component?.substringAfterLast('/')?.substringAfterLast('.')) {
            WEB_SERVER_COMPONENT -> WebServerService.handleIntent(context, intent)
            else -> Log.d(TAG, "没有桌面端实现接管 ${intent.component}")
        }
    }

    private const val WEB_SERVER_COMPONENT = "WebServerService"
    private const val TAG = "DesktopServiceRouter"
}
