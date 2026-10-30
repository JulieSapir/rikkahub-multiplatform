package me.rerere.rikkahub.ui.pages.extensions.workspace

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.termux.terminal.LocalPtyTransport
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.terminal.TerminalTransport
import com.termux.view.TerminalView
import me.rerere.workspace.WorkspaceTerminalLaunch
import java.io.File

/**
 * 工作区终端的桌面端会话装配（上游 WorkspaceTerminalSession.kt 里 TerminalView/软键盘相关的那半是
 * Android View 层，桌面端由 TerminalViewJvm 自己渲染，这里只保留 Manager 用到的入口）。
 *
 * 与上游的差异：
 * - createWorkspaceTerminalSession 收 [TerminalTransport]（本地 pty / SSH 远程 shell 都从这走），
 *   构造完立刻启动，失败时由 TerminalSession 按 Termux 约定回报 finish。
 * - toTransport() 识别出上游 terminalLaunch 给的 proot 启动项在本机跑不了（桌面端没有 rootfs 沙箱
 *   与 proot 二进制）时，退回宿主机 shell，直接以工作区 files 目录为 cwd 起交互 shell。
 * - activeDnsServers() 从 /etc/resolv.conf 读 nameserver（仅用于 rootfs DNS patch，桌面端 rootfs
 *   不存在时 RootfsPatcher 自己会跳过）。
 */
internal fun createWorkspaceTerminalSession(
    transport: TerminalTransport,
    root: String,
    client: TerminalSessionClient,
): TerminalSession =
    TerminalSession(transport, TRANSCRIPT_ROWS, client).apply {
        mSessionName = root
        start()
    }

internal fun WorkspaceTerminalLaunch.toTransport(): TerminalTransport {
    val executableFile = File(executable)
    val canRun = executableFile.isFile && executableFile.canExecute()
    return if (canRun) {
        LocalPtyTransport(executable, cwd, args.toTypedArray(), env.toTypedArray())
    } else {
        // 上游给的是 proot 启动项而桌面端没有 proot：退回宿主机 shell，工作目录即工作区 files
        Log.i(TAG, "terminal launch executable unavailable ($executable), falling back to host shell")
        LocalPtyTransport(
            TerminalSession.defaultShell(),
            cwd,
            arrayOf("-i"),
            emptyArray(),
        )
    }
}

/**
 * 桌面端的 rootfs 探针：上游 Manager 用 `workspaceManager.hasRootfs()`（检查 linux/bin/sh 是否存在）
 * 决定终端就绪状态，而桌面端没有 rootfs 沙箱（终端实际跑宿主机 shell，见 toTransport 的回退）。
 * 建一个空的 bin/sh 占位文件让 hasRootfs 判真，终端才能进入 Ready。
 */
internal fun ensureDesktopRootfsProbe(context: Context, root: String) {
    if (root.isBlank()) return
    runCatching {
        File(context.applicationContext.filesDir, "workspaces/$root/linux/bin").apply { mkdirs() }
            .resolve("sh")
            .takeIf { !it.exists() }
            ?.createNewFile()
    }.onFailure { Log.w(TAG, "failed to create rootfs probe for $root", it) }
}

internal fun Context.activeDnsServers(): List<String> {
    // Android 端从 ConnectivityManager 拿当前网络的 DNS；桌面端读 resolv.conf 的 nameserver 行
    return runCatching {
        File("/etc/resolv.conf").readLines()
            .mapNotNull { line ->
                line.trim().split(Regex("\\s+")).takeIf { it.size >= 2 && it[0] == "nameserver" }?.get(1)
            }
            .filter { it.isNotBlank() }
            .distinct()
    }.getOrDefault(emptyList())
}

internal class WorkspaceTerminalSessionClient(
    private val context: Context,
    private val onTitleUpdated: (String?) -> Unit,
    private val onFinished: () -> Unit,
) : TerminalSessionClient {
    var terminalView: TerminalView? = null

    override fun onTextChanged(changedSession: TerminalSession) {
        terminalView?.onScreenUpdated()
    }

    override fun onTitleChanged(changedSession: TerminalSession) {
        onTitleUpdated(changedSession.title)
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
        terminalView?.onScreenUpdated()
        onFinished()
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text))
    }

    override fun onPasteTextFromClipboard(session: TerminalSession) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.getText().takeIf { it.isNotEmpty() }
            ?: clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
            ?: return
        val bytes = text.toByteArray()
        session.write(bytes, 0, bytes.size)
    }

    override fun onBell(session: TerminalSession) = Unit

    override fun onColorsChanged(session: TerminalSession) {
        terminalView?.invalidate()
    }

    override fun onTerminalCursorStateChange(state: Boolean) {
        terminalView?.invalidate()
    }

    override fun getTerminalCursorStyle(): Int = TerminalEmulatorCursorStyle

    override fun logError(tag: String, message: String) { Log.e(tag, message) }

    override fun logWarn(tag: String, message: String) { Log.w(tag, message) }

    override fun logInfo(tag: String, message: String) { Log.i(tag, message) }

    override fun logDebug(tag: String, message: String) { Log.d(tag, message) }

    override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }

    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { Log.e(tag, message, e) }

    override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, "Terminal error", e) }
}

private const val TAG = "WorkspaceTerminalJvm"
private const val TRANSCRIPT_ROWS = 2_000

private val TerminalEmulatorCursorStyle = com.termux.terminal.TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE
