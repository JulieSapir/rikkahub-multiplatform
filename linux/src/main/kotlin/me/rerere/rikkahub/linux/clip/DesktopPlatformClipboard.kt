package me.rerere.rikkahub.linux.clip

import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.ClipboardOwner
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.datatransfer.Clipboard as AwtClipboard

/**
 * X11 的 getContents() 会同步拉取所有格式（含整张图片），不能放在 Compose 的粘贴入口。
 * 格式查询在 IO 线程执行，具体内容交给 Compose 的异步读取按需获取。
 */
internal class DesktopPlatformClipboard(
    private val systemClipboard: () -> AwtClipboard = { Toolkit.getDefaultToolkit().systemClipboard },
) : Clipboard {
    @Suppress("OVERRIDE_DEPRECATION")
    override val nativeClipboard: AwtClipboard get() = systemClipboard()

    override suspend fun getClipEntry(): ClipEntry? = withContext(Dispatchers.IO) {
        try {
            val clipboard = systemClipboard()
            val flavors = clipboard.availableDataFlavors
            if (flavors.isEmpty()) null else ClipEntry(LazyClipboardTransferable(clipboard, flavors))
        } catch (_: IllegalStateException) {
            // 剪贴板正被其他进程占用时，本次粘贴不处理。
            null
        }
    }

    override suspend fun setClipEntry(clipEntry: ClipEntry?) = withContext(Dispatchers.IO) {
        val transferable = clipEntry?.nativeClipEntry as? Transferable ?: EmptyTransferable
        try {
            systemClipboard().setContents(transferable, transferable as? ClipboardOwner)
        } catch (_: IllegalStateException) {
            // 与 Compose 默认实现一致：剪贴板忙时忽略本次写入。
        }
    }
}

private class LazyClipboardTransferable(
    private val clipboard: AwtClipboard,
    private val flavors: Array<DataFlavor>,
) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = flavors.clone()

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor in flavors

    override fun getTransferData(flavor: DataFlavor): Any {
        if (!isDataFlavorSupported(flavor)) throw UnsupportedFlavorException(flavor)
        return clipboard.getData(flavor)
    }
}

private object EmptyTransferable : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = emptyArray()
    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = false
    override fun getTransferData(flavor: DataFlavor): Nothing = throw UnsupportedFlavorException(flavor)
}
