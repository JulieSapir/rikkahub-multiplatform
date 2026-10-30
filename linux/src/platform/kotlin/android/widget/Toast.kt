package android.widget

import android.content.Context
import java.util.Locale
import me.rerere.rikkahub.linux.res.RikkaRes

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

        /** 上游有直接传字符串资源 id 的调用点（如 WorkspaceDetailPage 的复制提示），桌面端查 RikkaRes 的字符串表。 */
        fun makeText(context: Context, resId: Int, duration: Int): Toast =
            Toast(runCatching { RikkaRes.string(resId, Locale.getDefault()) }.getOrDefault(""))
    }
}
