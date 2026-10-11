package androidx.lifecycle

import android.app.Application

/**
 * JB 的 lifecycle-viewmodel 没有 Android 侧的 AndroidViewModel，
 * 上游几个 VM 用它拿 Application，这里补一个把 Context 存起来的子类。
 */
open class AndroidViewModel(@get:JvmName("desktopApplication") val application: Application) : ViewModel() {
    @Suppress("UNCHECKED_CAST")
    fun <T : Application> getApplication(): T = application as T
}

/**
 * Android 的 Activity 带 lifecycleScope（随生命周期取消）。桌面端窗口活到进程结束，
 * 这里给一个常驻 scope，语义上等价于“始终 STARTED 的生命周期”。
 */
val android.app.Activity.lifecycleScope: kotlinx.coroutines.CoroutineScope
    get() = androidActivityScope

private val androidActivityScope = kotlinx.coroutines.CoroutineScope(
    kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate,
)
