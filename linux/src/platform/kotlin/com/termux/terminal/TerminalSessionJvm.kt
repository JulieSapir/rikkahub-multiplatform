package com.termux.terminal

import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Termux 的 TerminalSession 靠它的 JNI（`libtermux.so` + Android 的 socketpair）起 shell，
 * 桌面端换成 JetBrains 的 pty4j：同样是伪终端，输出喂给 Termux 那份纯 Java 的
 * [com.termux.terminal.TerminalEmulator] 解析，VT100 行为与 Android 端一致。
 *
 * 类名、构造参数与上游用到的成员都对齐 Termux 0.118，
 * 因此 WorkspaceTerminalSessionManager 不用改；aar 里的同名类在 build.gradle.kts 里被剔除了。
 */
class TerminalSession(
    private val shellPath: String,
    private val workingDirectory: String,
    private val args: Array<String>,
    private val envAdditions: Array<String>,
    @Suppress("UNUSED_PARAMETER") transcriptRows: Int,
    private val sessionClient: TerminalSessionClient,
) : TerminalOutput() {

    var mSessionName: String? = null

    val emulator: TerminalEmulator = TerminalEmulator(this, INITIAL_COLUMNS, INITIAL_ROWS, null, sessionClient)

    val title: String? get() = emulator.title?.takeIf { it.isNotBlank() }

    val isSessionFinished: Boolean get() = finished.get()

    private val finished = AtomicBoolean(false)
    private var process: PtyProcess? = null
    private var reader: Thread? = null

    fun isRunning(): Boolean = process?.isAlive == true

    /** 启动 shell；失败时按 Termux 的约定回报 finish，让上层显示错误而不是崩溃。 */
    fun start(): Boolean = runCatching {
        val commands = mutableListOf(shellPath).apply { addAll(args.mapNotNull { it.takeIf { a -> a.isNotEmpty() } }) }
        val pty = PtyProcessBuilder(commands.toTypedArray())
            .setDirectory(workingDirectory)
            .setEnvironment(envMap())
            .setConsole(false)
            .setInitialColumns(INITIAL_COLUMNS)
            .setInitialRows(INITIAL_ROWS)
            .start()
        process = pty
        reader = thread(isDaemon = true, name = "terminal-reader-${mSessionName ?: "session"}") { readLoop(pty) }
        true
    }.getOrElse {
        sessionClient.logStackTraceWithMessage(TAG, "无法启动 shell: $shellPath", Exception(it.message))
        finish()
        false
    }

    private fun envMap(): Map<String, String> {
        val base = System.getenv().toMutableMap()
        envAdditions.forEach { entry ->
            val index = entry.indexOf('=')
            if (index > 0) base[entry.substring(0, index)] = entry.substring(index + 1)
        }
        base["TERM"] = base["TERM"] ?: "xterm-256color"
        base["LANG"] = base["LANG"] ?: "C.UTF-8"
        return base
    }

    private fun readLoop(pty: PtyProcess) {
        val buffer = ByteArray(8 * 1024)
        runCatching {
            pty.inputStream.use { input ->
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
        finish()
    }

    /** 键盘输入写入 pty（Termux 的 TerminalSession.write 就是这个）。 */
    override fun write(bytes: ByteArray, offset: Int, count: Int) {
        val output = process?.outputStream ?: return
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
        runCatching { process?.setWinSize(com.pty4j.WinSize(rows.coerceAtLeast(1), columns.coerceAtLeast(1))) }
    }

    fun finishIfRunning() {
        if (isRunning()) {
            runCatching { process?.destroy() }
        }
        finish()
    }

    private fun finish() {
        if (!finished.compareAndSet(false, true)) return
        reader?.interrupt()
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
