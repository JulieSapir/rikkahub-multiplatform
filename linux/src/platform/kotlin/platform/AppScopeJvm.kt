package me.rerere.rikkahub

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * RikkaHubApp.kt 里的 AppScope 在桌面端也要有（SettingsStore 的构造签名引用它），
 * 上游那份文件本身依赖 Android 生命周期，被 exclude 了。
 *
 * Dispatchers.Main 在桌面端由 kotlinx-coroutines-swing 提供。
 */
class AppScope : CoroutineScope by CoroutineScope(
    SupervisorJob()
        + Dispatchers.Main
        + CoroutineName("AppScope")
        + CoroutineExceptionHandler { _, e ->
        Log.e("AppScope", "AppScope exception", e)
    }
)
