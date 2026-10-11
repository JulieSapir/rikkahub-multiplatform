package android.graphics

import java.io.File

/**
 * 上游 ChatFont.kt 在加载自定义字体前会先 Typeface.createFromFile 校验文件是不是合法字体。
 * 桌面端由 Compose 自己加载字体文件，这里用 javax 的字体接口做同样的合法性校验。
 */
class Typeface private constructor(val familyName: String = "Default") {
    companion object {
        val DEFAULT: Typeface = Typeface()
        val MONOSPACE: Typeface = Typeface("Monospace")
        val SANS_SERIF: Typeface = Typeface("SansSerif")
        val DEFAULT_BOLD: Typeface = Typeface("Default-Bold")

        fun create(default: Typeface, style: Int): Typeface = default

        fun createFromFile(file: File): Typeface {
            java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, file)
            return Typeface()
        }
    }
}
