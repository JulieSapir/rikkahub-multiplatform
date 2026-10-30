package com.termux.terminal

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Termux 的 TerminalSession 靠它的 JNI（libtermux.so + Android 的 socketpair）起进程。上游新版把
 * 「怎么起进程」抽成了 [TerminalTransport]（本地 pty 走 LocalPtyTransport，远程 SSH 走
 * SshTerminalTransport），这里提供同一构造签名的桌面版：进程由 pty4j 起本地伪终端，输出喂给
 * Termux 那份纯 Java 的 TerminalEmulator 解析，VT100 行为与 Android 端一致。
 *
 * 类名、构造参数与上游用到的成员对齐（transport 版构造 + 旧版本地 shell 构造），
 * WorkspaceTerminalSessionManager / TerminalView 不用改。
 */
class TerminalSession constructor(
    private val transport: TerminalTransport,
    @Suppress("UNUSED_PARAMETER") transcriptRows: Int,
    private val sessionClient: TerminalSessionClient,
) : TerminalOutput() {

    /** 与上游旧构造等价：本地 shell 直起一份 pty。 */
    constructor(
        shellPath: String,
        workingDirectory: String,
        args: Array<String>,
        envAdditions: Array<String>,
        transcriptRows: Int,
        sessionClient: TerminalSessionClient,
    ) : this(LocalPtyTransport(shellPath, workingDirectory, args, envAdditions), transcriptRows, sessionClient)

    var mSessionName: String? = null

    val emulator: TerminalEmulator = TerminalEmulator(this, INITIAL_COLUMNS, INITIAL_ROWS, null, sessionClient)

    val title: String? get() = emulator.title?.takeIf { it.isNotBlank() }

    val isSessionFinished: Boolean get() = finished.get()

    private val finished = AtomicBoolean(false)
    private val started = AtomicBoolean(false)

    fun isRunning(): Boolean = started.get() && !finished.get()

    /** 启动进程；失败时按 Termux 的约定回报 finish，让上层显示错误而不是崩溃。 */
    fun start(): Boolean {
        if (!started.compareAndSet(false, true)) return true
        val startDescription = transport.describeStart()
        if (startDescription != null) {
            val banner = "[$startDescription]\r\n".toByteArray(Charsets.UTF_8)
            emulator.append(banner, banner.size)
            sessionClient.onTextChanged(this)
        }
        return runCatching {
            transport.start(INITIAL_COLUMNS, INITIAL_ROWS)
            thread(isDaemon = true, name = "terminal-reader-${mSessionName ?: "session"}") { readLoop() }
            true
        }.getOrElse {
            sessionClient.logStackTraceWithMessage(TAG, "无法启动 shell", Exception(it.message))
            finish()
            false
        }
    }

    private fun readLoop() {
        val buffer = ByteArray(8 * 1024)
        runCatching {
            transport.getInputStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    emulator.append(buffer.copyOf(read), read)
                    sessionClient.onTextChanged(this)
                }
            }
        }.onFailure {
            if (!finished.get()) sessionClient.logStackTraceWithMessage(TAG, "终端读取失败", Exception(it.message))
        }
        val exitStatus = runCatching { transport.waitFor() }.getOrDefault(-1)
        val exitDescription = transport.describeExit(exitStatus)
        if (exitDescription != null) {
            val line = "[$exitDescription]\r\n".toByteArray(Charsets.UTF_8)
            emulator.append(line, line.size)
        }
        finish()
    }

    /** 键盘输入写入进程（Termux 的 TerminalSession.write 就是这个）。 */
    override fun write(bytes: ByteArray, offset: Int, count: Int) {
        val output = runCatching { transport.getOutputStream() }.getOrNull() ?: return
        runCatching {
            output.write(bytes, offset, count)
            output.flush()
        }.onFailure { sessionClient.logStackTraceWithMessage(TAG, "写入终端失败", Exception(it.message)) }
    }

    fun writeText(text: String) {
        // 终端 env 固定 C.UTF-8，这里不能依赖平台默认字符集
        val bytes = text.toByteArray(Charsets.UTF_8)
        write(bytes, 0, bytes.size)
    }

    fun onResize(columns: Int, rows: Int) {
        emulator.resize(columns, rows)
        if (isRunning()) runCatching { transport.setWindowSize(columns, rows) }
    }

    fun finishIfRunning() {
        if (started.get()) {
            runCatching { transport.kill() }
        }
        finish()
    }

    private fun finish() {
        if (!finished.compareAndSet(false, true)) return
        runCatching { transport.close() }
        sessionClient.onSessionFinished(this)
    }

    // --- TerminalOutput：模拟器要写回终端/转给 UI 的事件 ---

    override fun titleChanged(text: String?, title: String?) {
        sessionClient.onTitleChanged(this)
    }

    override fun onCopyTextToClipboard(text: String) = sessionClient.onCopyTextToClipboard(this, text)

    override fun onPasteTextFromClipboard() = sessionClient.onPasteTextFromClipboard(this)

    override fun onBell() = sessionClient.onBell(this)

    override fun onColorsChanged() = sessionClient.onColorsChanged(this)

    companion object {
        private const val TAG = "DesktopTerminal"
        const val INITIAL_COLUMNS = 80
        const val INITIAL_ROWS = 24

        /** Android 端从这里取 shell；桌面端直接用当前用户的登录 shell。 */
        fun defaultShell(): String =
            System.getenv("SHELL")?.takeIf { File(it).canExecute() } ?: "/bin/bash"
    }
}
