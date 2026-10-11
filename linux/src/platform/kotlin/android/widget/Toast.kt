package android.widget

import android.content.Context

/**
 * 桌面端没有 Toast：把消息打到标准错误，调用方（错误提示、复制成功之类）保持原样。
 * Compose 侧真正给用户看的那部分用的是 sonner 的 Toaster，不依赖这里。
 */
class Toast private constructor(private val text: String) {
    fun show() {
        System.err.println("[toast] $text")
    }

    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1

        fun makeText(context: Context, text: CharSequence?, duration: Int): Toast =
            Toast(text?.toString().orEmpty())
    }
}
