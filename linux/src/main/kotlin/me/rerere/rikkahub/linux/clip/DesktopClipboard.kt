package me.rerere.rikkahub.linux.clip

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor

/**
 * JB 桌面的 Clipboard 只有 ClipEntry 这一层，Android 端的 clipboard.getText() 在这里落地：
 * 直接读系统剪贴板文本，取不到时返回 null（与 Android 无文本时一致）。
 */
object DesktopClipboard {
    fun readText(): String? = runCatching {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            clipboard.getData(DataFlavor.stringFlavor) as? String
        } else null
    }.getOrNull()

    fun writeText(text: String): Boolean = runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(
            java.awt.datatransfer.StringSelection(text), null,
        )
        true
    }.getOrDefault(false)
}
