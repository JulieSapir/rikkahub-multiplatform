package me.rerere.rikkahub.service

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.web.WebServerManager
import org.koin.core.context.GlobalContext

/**
 * 上游的 WebServerService 是前台服务（常驻通知 + 保活），那份文件在 linux/build.gradle.kts 里 exclude；
 * 桌面端进程本来就活着，
 * 所以这里保留上游用到的 ACTION/EXTRA 常量，收到 Intent 后直接驱动同一个 WebServerManager：
 * 内嵌 API 与 mDNS 发现在桌面端是真能用的。
 */
object WebServerService {
    const val ACTION_START = "me.rerere.rikkahub.action.WEB_SERVER_START"
    const val ACTION_STOP = "me.rerere.rikkahub.action.WEB_SERVER_STOP"
    const val EXTRA_PORT = "port"
    const val EXTRA_LOCALHOST_ONLY = "localhost_only"
    const val NOTIFICATION_ID = 2001

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun handleIntent(context: Context, intent: Intent) {
        val manager = runCatching { GlobalContext.get().get<WebServerManager>() }.getOrNull() ?: run {
            Log.w(TAG, "WebServerManager 还没注册，忽略 ${intent.action}")
            return
        }
        val settingsStore = runCatching { GlobalContext.get().get<SettingsStore>() }.getOrNull()

        when (intent.action) {
            ACTION_START -> manager.start(
                port = intent.getIntExtra(EXTRA_PORT, 8080),
                localhostOnly = intent.getBooleanExtra(EXTRA_LOCALHOST_ONLY, false),
            )

            ACTION_STOP -> {
                manager.stop()
                settingsStore?.let { store ->
                    scope.launch { store.update { it.copy(webServerEnabled = false) } }
                }
            }

            null -> scope.launch {
                val settings = settingsStore?.awaitLoaded() ?: return@launch
                if (settings.webServerEnabled) {
                    manager.start(
                        port = settings.webServerPort,
                        localhostOnly = settings.webServerLocalhostOnly,
                    )
                }
            }
        }
    }

    private const val TAG = "DesktopWebServer"
}
