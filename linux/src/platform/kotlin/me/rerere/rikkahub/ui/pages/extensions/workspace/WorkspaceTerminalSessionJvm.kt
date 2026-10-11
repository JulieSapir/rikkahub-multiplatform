package me.rerere.rikkahub.ui.pages.extensions.workspace

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import java.io.File

/**
 * 工作区终端的桌面端实现。
 *
 * Android 那份要在进程里跑 PRoot + 一份下载的 rootfs（bind mount、DNS patch、seccomp 兼容开关），
 * 桌面端不需要沙箱：直接在 workspace 的 files 目录起本机 shell，终端仿真复用 Termux 的
 * TerminalEmulator（纯 Java），进程由 pty4j 提供伪终端。
 *
 * 三个 internal 入口的名字与签名和上游保持一致，所以上层的
 * WorkspaceTerminalSessionManager（多标签、生命周期）不用改。
 */
internal fun createWorkspaceTerminalSession(
    context: Context,
    root: String,
    client: TerminalSessionClient,
    shellCompatibilityMode: Boolean,
): TerminalSession {
    val appContext = context.applicationContext
    val workspaceDir = File(File(appContext.filesDir, "workspaces"), root)
    val filesDir = File(workspaceDir, "files").apply { mkdirs() }
    val shell = TerminalSession.defaultShell()

    return TerminalSession(
        shell,
        filesDir.absolutePath,
        arrayOf("-c", "cd ${filesDir.absolutePath} && exec $shell -i"),
        arrayOf("RIKKA_WORKSPACE=$root"),
        TRANSCRIPT_ROWS,
        client,
    ).apply {
        mSessionName = root
        start()
    }
}

/** 桌面端没有 rootfs 要准备，只保证目录存在。 */
internal fun prepareWorkspaceTerminalSession(context: Context, root: String) {
    val workspaceDir = File(File(context.applicationContext.filesDir, "workspaces"), root)
    File(workspaceDir, "files").mkdirs()
    File(workspaceDir, "tmp").mkdirs()
}

/** Android 端要判断 rootfs 是否装好，桌面端的本机 shell 一直可用。 */
internal fun workspaceRootfsReady(context: Context, root: String): Boolean = true

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

    override fun getTerminalCursorStyle(): Int = CURSOR_STYLE_BLOCK

    override fun logError(tag: String, message: String) { Log.e(tag, message) }

    override fun logWarn(tag: String, message: String) { Log.w(tag, message) }

    override fun logInfo(tag: String, message: String) { Log.i(tag, message) }

    override fun logDebug(tag: String, message: String) { Log.d(tag, message) }

    override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }

    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { Log.e(tag, message, e) }

    override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, "Terminal error", e) }
}

private const val TRANSCRIPT_ROWS = 2_000

private const val CURSOR_STYLE_BLOCK = com.termux.terminal.TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE
