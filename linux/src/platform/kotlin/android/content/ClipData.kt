package android.content

import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable

/**
 * 上游用 ClipData.newPlainText(...) 包一层 ClipEntry 再写剪贴板；Compose 桌面端的 ClipEntry
 * 收的就是 java.awt 的 Transferable，所以这里直接实现它，复制功能是真能用的。
 *
 * itemCount/getItemAt 只是把纯文本再暴露一遍，给 utils/ClipboardUtil 的 ClipData.getText() 用。
 */
class ClipData private constructor(
    val label: String,
    private val delegate: Transferable,
    val text: CharSequence?,
) : Transferable by delegate {

    val itemCount: Int get() = if (text == null) 0 else 1

    fun getItemAt(index: Int): Item = Item(text)

    class Item(val text: CharSequence?)

    companion object {
        fun newPlainText(label: String?, text: String): ClipData = ClipData(label.orEmpty(), StringSelection(text), text)
    }
}
