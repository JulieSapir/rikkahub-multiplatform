package android.graphics

import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import java.io.File

/**
 * android.graphics.Typeface 的桌面端实现，包一层 Skia Typeface，供 [Paint] 量字与绘制。
 *
 * 上游 ChatFont.kt 在加载自定义字体前会先 Typeface.createFromFile 校验文件是不是合法字体，
 * Skia 解析失败时同样抛异常。
 */
class Typeface private constructor(internal val skiaTypeface: org.jetbrains.skia.Typeface?) {
    companion object {
        val DEFAULT: Typeface = system(null, FontStyle.NORMAL)
        val DEFAULT_BOLD: Typeface = system(null, FontStyle.BOLD)
        val MONOSPACE: Typeface = system("monospace", FontStyle.NORMAL)
        val SANS_SERIF: Typeface = system("sans-serif", FontStyle.NORMAL)
        val SERIF: Typeface = system("serif", FontStyle.NORMAL)

        private fun system(family: String?, style: FontStyle): Typeface =
            Typeface(runCatching { FontMgr.default.matchFamilyStyle(family, style) }.getOrNull())

        fun create(default: Typeface, style: Int): Typeface = default

        fun createFromFile(file: File): Typeface = createFromBytes(file.readBytes())

        /** 桌面端额外的入口：从内存里的字体文件（比如 jar 资源）建字体 */
        fun createFromBytes(bytes: ByteArray): Typeface {
            val typeface = FontMgr.default.makeFromData(Data.makeFromBytes(bytes))
                ?: throw RuntimeException("Font asset not found or invalid")
            return Typeface(typeface)
        }
    }
}
