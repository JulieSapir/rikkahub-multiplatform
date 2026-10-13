package me.rerere.rikkahub.linux.desktop

import android.util.Log
import java.io.File
import kotlin.system.exitProcess

/**
 * 桌面端的「重启应用」。
 *
 * Android 上备份恢复后 exitProcess(0) 就完了——用户重新点图标即可，系统也随时能拉起来；
 * 桌面端进程一退出，应用就没了，所以这里按 /proc/self/cmdline 原样另起一个进程再退出。
 * 直接读 cmdline（NUL 分隔）而不是拼命令行字符串，是为了不依赖参数里有没有空格，
 * 也顺带保住原生包启动时那个 -Djava.home 参数。
 */
object DesktopProcess {
    private const val TAG = "DesktopProcess"

    fun restart() {
        val argv = restartCommand()
        if (argv == null) {
            Log.w(TAG, "读不到本进程命令行，无法自动重启，请手动重新启动应用")
            exitProcess(0)
        }
        runCatching {
            ProcessBuilder(argv)
                // DISCARD 只能用于输出，用作输入会抛「Redirect invalid for reading: WRITE」
                .redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        }.onFailure { Log.w(TAG, "重新启动进程失败: ${it.message}") }
        exitProcess(0)
    }

    /** 本进程的 argv，读不到时返回 null。 */
    fun restartCommand(): List<String>? = runCatching {
        File("/proc/self/cmdline").readBytes().toString(Charsets.UTF_8)
            .split('\u0000').filter { it.isNotEmpty() }
            .takeIf { it.isNotEmpty() }
    }.getOrNull()
}
