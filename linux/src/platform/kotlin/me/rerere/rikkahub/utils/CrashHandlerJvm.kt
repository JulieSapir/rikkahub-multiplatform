package me.rerere.rikkahub.utils

import android.content.Context
import java.io.File

/**
 * Android 版把未捕获异常写进文件，下次启动进安全模式。桌面端进程崩了就由启动脚本重启，
 * 没有“安全模式”这层 UI：这里保留同样的 API，把崩溃记到数据目录的日志里，hasCrashed 恒为 false，
 * 于是上游的启动流程不会跳到 SafeModeActivity。
 */
object CrashHandler {
    private fun crashFile(context: Context) = File(context.noBackupFilesDir, "crash.log")

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                crashFile(context).appendText("--- $thread ${throwable.message}\n${throwable.stackTraceToString()}\n")
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun hasCrashed(context: Context): Boolean = false

    fun getStackTrace(context: Context): String? = crashFile(context).takeIf { it.isFile }?.readText()

    fun clearCrashed(context: Context) {
        crashFile(context).delete()
    }
}
