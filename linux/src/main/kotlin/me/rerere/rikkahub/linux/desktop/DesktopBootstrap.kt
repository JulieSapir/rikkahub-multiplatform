package me.rerere.rikkahub.linux.desktop

import android.content.Context
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.rerere.common.android.appTempFolder
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.sync.BackupManager
import me.rerere.rikkahub.data.sync.RestoreFailedException
import me.rerere.rikkahub.di.appModule
import me.rerere.rikkahub.di.dataSourceModule
import me.rerere.rikkahub.di.repositoryModule
import me.rerere.rikkahub.di.viewModelModule
import me.rerere.rikkahub.utils.CrashHandler
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.web.WebServerManager
import org.koin.core.Koin
import org.koin.core.context.GlobalContext
import org.koin.core.module.Module

/**
 * 对应 Android 端 RikkaHubApp（Application 壳）启动时做的那串准备工作
 */
object DesktopBootstrap {
    private const val TAG = "DesktopBootstrap"

    /** 上游那四个 module：桌面端不另写一份，避免上游加依赖时两边漂移。 */
    val upstreamModules: List<Module>
        get() = listOf(appModule, viewModelModule, dataSourceModule, repositoryModule)

    fun prepareBeforeKoin(context: Context) {
        CrashHandler.install(context)
        runCatching {
            val restored = runBlocking(Dispatchers.IO) {
                BackupManager.applyPendingRestore(context, JsonInstant)
            }
            if (restored) Toast.makeText(context, "已从备份恢复", Toast.LENGTH_SHORT).show()
        }.onFailure { error ->
            if (error is RestoreFailedException) {
                Log.e(TAG, "备份恢复已回滚", error)
                Toast.makeText(context, "备份恢复失败，已保留原数据。请重新导入备份。", Toast.LENGTH_LONG).show()
            } else {
                Log.e(TAG, "应用待恢复备份失败", error)
            }
        }
    }

    fun afterKoin(context: Context) {
        runCatching { context.appTempFolder.listFiles()?.forEach { it.deleteRecursively() } }
            .onFailure { Log.w(TAG, "清理临时目录失败: ${it.message}") }
        // 等 DataStore 真的加载完再看开关：settingsFlow.value 在读盘完成前是默认值，
        // 直接读会让「开机自启 Web 服务」变成一场和磁盘速度的赛跑（实测时好时坏）。
        scope.launch {
            runCatching {
                val settings = koin.get<SettingsStore>().awaitLoaded()
                if (settings.webServerEnabled) {
                    koin.get<WebServerManager>().start(
                        port = settings.webServerPort,
                        localhostOnly = settings.webServerLocalhostOnly,
                    )
                }
            }.onFailure { Log.w(TAG, "按设置启动 Web 服务失败: ${it.message}") }
        }
    }

    private val koin: Koin get() = GlobalContext.get()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
