package com.termux.terminal

import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import com.pty4j.WinSize
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * 上游的 LocalPtyTransport 靠 JNI（libtermux.so + Android ParcelFileDescriptor）起子进程，
 * 桌面端换成 pty4j 的 PtyProcess：同样是伪终端，实现同一套 [TerminalTransport] 语义。
 * start/waitFor 在会话的后台线程调用，setWindowSize/kill/close 按接口约定在主线程调用。
 */
class LocalPtyTransport(
    private val shellPath: String,
    private val cwd: String,
    private val args: Array<String>,
    private val env: Array<String>,
) : TerminalTransport {

    private var process: PtyProcess? = null

    override fun start(columns: Int, rows: Int) {
        val commands = mutableListOf(shellPath).apply { addAll(args.mapNotNull { it.takeIf { a -> a.isNotEmpty() } }) }
        val pty = PtyProcessBuilder(commands.toTypedArray())
            .setDirectory(cwd.takeIf { File(it).isDirectory } ?: File(".").absolutePath)
            .setEnvironment(envMap())
            .setConsole(false)
            .setInitialColumns(columns.coerceAtLeast(1))
            .setInitialRows(rows.coerceAtLeast(1))
            .start()
        process = pty
    }

    override fun getInputStream(): InputStream = requireProcess().inputStream

    override fun getOutputStream(): OutputStream = requireProcess().outputStream

    override fun setWindowSize(columns: Int, rows: Int) {
        runCatching { process?.setWinSize(WinSize(columns.coerceAtLeast(1), rows.coerceAtLeast(1))) }
    }

    override fun waitFor(): Int = requireProcess().waitFor()

    override fun kill() {
        val pty = process ?: return
        runCatching { pty.destroy() }
    }

    override fun close() {
        val pty = process ?: return
        process = null
        runCatching { pty.destroy() }
    }

    private fun requireProcess(): PtyProcess = process ?: error("Local pty is not started")

    private fun envMap(): Map<String, String> {
        val base = System.getenv().toMutableMap()
        env.forEach { entry ->
            val index = entry.indexOf('=')
            if (index > 0) base[entry.substring(0, index)] = entry.substring(index + 1)
        }
        base["TERM"] = base["TERM"] ?: "xterm-256color"
        base["LANG"] = base["LANG"] ?: "C.UTF-8"
        return base
    }
}
