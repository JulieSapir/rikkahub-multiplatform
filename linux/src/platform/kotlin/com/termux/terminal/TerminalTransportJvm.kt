package com.termux.terminal

import java.io.InputStream
import java.io.OutputStream

/**
 * 上游把本地 pty 抽象成了 [TerminalTransport]（terminal 模块里的同名 Java 接口，桌面端不编译那个
 * 模块，这里给一份语义一致的 Kotlin 版）：会话背后可以是本地伪终端进程，也可以是远程主机上的
 * shell（SshTerminalTransport）。
 *
 * 线程模型与上游一致：start/waitFor 在后台线程调用且允许阻塞；流由会话的读写线程消费；
 * setWindowSize/kill/close 在主线程调用且不允许阻塞。
 */
interface TerminalTransport {
    /** 以初始窗口尺寸启动进程；只调用一次，抛异常视为启动失败。 */
    @Throws(java.io.IOException::class)
    fun start(columns: Int, rows: Int)

    /** 进程输出，读到流结束为止。 */
    fun getInputStream(): InputStream

    /** 进程输入。 */
    fun getOutputStream(): OutputStream

    /** 通知进程窗口尺寸变化，只在 start 成功之后调用。 */
    fun setWindowSize(columns: Int, rows: Int)

    /** 阻塞等进程退出，返回退出码（被信号杀死时为负的信号号）。 */
    fun waitFor(): Int

    /** 强制结束进程；可能仍在 start 里阻塞时被调用。 */
    fun kill()

    /** 进程退出或启动失败后释放资源。 */
    fun close()

    /** 启动前打印在终端里的一行说明（如连接中），null 表示不打印。 */
    fun describeStart(): String? = null

    /** 进程退出后打印的一行说明。 */
    fun describeExit(exitStatus: Int): String {
        var description = "Process completed"
        if (exitStatus > 0) {
            description += " (code $exitStatus)"
        } else if (exitStatus < 0) {
            description += " (signal ${-exitStatus})"
        }
        return "$description - press Enter"
    }
}
