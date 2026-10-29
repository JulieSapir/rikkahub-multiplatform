package android.content

import android.util.Log
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor

/**
 * 桌面端剪贴板是真能用的：直接桥到 java.awt.SystemClipboard，
 * 终端里的复制粘贴（WorkspaceTerminalSession）与上游的读写代码都不用改。
 */
class ClipboardManager {

    var primaryClip: ClipData? = null
        private set

    private val listeners = mutableListOf<OnPrimaryClipChangedListener>()

    fun setPrimaryClip(clip: ClipData) {
        primaryClip = clip
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(clip, null)
        }.onFailure { Log.w(TAG, "写入剪贴板失败: ${it.message}") }
        notifyListeners()
    }

    /** 读剪贴板文本，取不到时返回 null，和 Android 无文本内容时一致。 */
    private fun readText(): String? = runCatching {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            clipboard.getData(DataFlavor.stringFlavor) as? String
        } else null
    }.getOrNull()

    fun getText(): String = readText().orEmpty()

    fun addPrimaryClipChangedListener(listener: OnPrimaryClipChangedListener) {
        synchronized(listeners) { listeners += listener }
    }

    fun removePrimaryClipChangedListener(listener: OnPrimaryClipChangedListener) {
        synchronized(listeners) { listeners -= listener }
    }

    /** Android 清空时系统剪贴板也随之清空并回调监听者，这里保持一致。 */
    fun clearPrimaryClip() {
        primaryClip = null
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(EmptyTransferable, null)
        }.onFailure { Log.w(TAG, "清空剪贴板失败: ${it.message}") }
        notifyListeners()
    }

    private fun notifyListeners() {
        val snapshot = synchronized(listeners) { listeners.toList() }
        snapshot.forEach { it.onPrimaryClipChanged() }
    }

    interface OnPrimaryClipChangedListener {
        fun onPrimaryClipChanged()
    }

    companion object {
        private const val TAG = "DesktopClipboard"

        /** AWT 剪贴板不接受 null 内容，清空用它顶一个“空选区”。 */
        private val EmptyTransferable = object : java.awt.datatransfer.Transferable {
            override fun getTransferDataFlavors(): Array<DataFlavor> = emptyArray()
            override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = false
            override fun getTransferData(flavor: DataFlavor): Any =
                throw java.awt.datatransfer.UnsupportedFlavorException(flavor)
        }

        /** 与 Android 的 EXTRA_CLIPBOARD... 一样：从 Context.getSystemService 取的是同一个实例。 */
        internal val shared = ClipboardManager()
    }
}

class ClipDescription(private val label: String) {
    fun getContentDescription(): String = label

    fun hasMimeType(mimeType: String): Boolean = mimeType == MIMETYPE_TEXT_PLAIN

    companion object {
        const val MIMETYPE_TEXT_PLAIN = "text/plain"
    }
}
