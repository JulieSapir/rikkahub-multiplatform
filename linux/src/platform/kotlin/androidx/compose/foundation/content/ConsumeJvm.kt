package androidx.compose.foundation.content

import android.net.Uri
import androidx.compose.ui.Modifier
import java.awt.datatransfer.DataFlavor
import java.io.File

/**
 * Compose Multiplatform 的桌面端没有 Android 那套 `TransferableItem` / `consume()`：
 * 拖放内容以一个 AWT Transferable 的形式到达。这里按 Android 的签名补出等价物，
 * 让聊天输入框的“拖图片进对话框 / 长文本转文件”逻辑原样运行。
 */
class TransferableItem internal constructor(
    val uri: Uri?,
    val text: CharSequence?,
)

/** 把拖放内容拆成条目：文件各一条（有 uri），纯文本一条。 */
fun TransferableContent.consume(onItem: (TransferableItem) -> Boolean): TransferableContent {
    desktopItems().forEach { item -> onItem(item) }
    return this
}

/** Android 的 consume() 会返回未处理的部分；桌面端全部当作已处理。 */
fun TransferableContent.consume(): TransferableContent = this

fun TransferableContent.items(): List<TransferableItem> = desktopItems()

private fun TransferableContent.desktopItems(): List<TransferableItem> {
    val transferable = clipEntry.nativeClipEntry as? java.awt.datatransfer.Transferable
        ?: return emptyList()
    val files = runCatching {
        if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            @Suppress("UNCHECKED_CAST")
            (transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>).orEmpty()
        } else emptyList()
    }.getOrDefault(emptyList())
    if (files.isNotEmpty()) {
        return files.map { TransferableItem(uri = Uri.fromFile(it), text = null) }
    }
    val text = runCatching {
        if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            transferable.getTransferData(DataFlavor.stringFlavor) as? String
        } else null
    }.getOrNull()
    return if (text == null) emptyList() else listOf(TransferableItem(uri = null, text = text))
}

/**
 * JB 桌面端的 `Modifier.contentReceiver` 会抛 NotImplementedError（foundation-desktop 里还是 TODO），
 * 所以兼容改写把上游的拖放注册换成这个空实现：拖文件进输入框暂不生效，粘贴与选文件都正常。
 */
fun Modifier.desktopContentReceiver(listener: ReceiveContentListener?): Modifier = this
