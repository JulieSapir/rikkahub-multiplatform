package android.graphics

import org.jetbrains.skia.Font
import org.jetbrains.skia.PaintMode

/**
 * android.graphics.Paint 的桌面端实现：画笔属性落在 Skia Paint 上，字号/字体落在 Skia Font 上
 * （Android 把两者合在一个 Paint 里）。只覆盖上游自绘内容用到的成员。
 */
class Paint(flags: Int = 0) {
    enum class Style { FILL, STROKE, FILL_AND_STROKE }

    internal val skia = org.jetbrains.skia.Paint().apply {
        isAntiAlias = flags and ANTI_ALIAS_FLAG != 0
    }

    internal val font = Font(Typeface.DEFAULT.skiaTypeface, 12f).apply {
        isSubpixel = flags and SUBPIXEL_TEXT_FLAG != 0
    }

    var isAntiAlias: Boolean
        get() = skia.isAntiAlias
        set(value) {
            skia.isAntiAlias = value
        }

    var color: Int
        get() = skia.color
        set(value) {
            skia.color = value
        }

    var style: Style = Style.FILL
        set(value) {
            field = value
            skia.mode = when (value) {
                Style.FILL -> PaintMode.FILL
                Style.STROKE -> PaintMode.STROKE
                Style.FILL_AND_STROKE -> PaintMode.STROKE_AND_FILL
            }
        }

    var strokeWidth: Float
        get() = skia.strokeWidth
        set(value) {
            skia.strokeWidth = value
        }

    var textSize: Float
        get() = font.size
        set(value) {
            font.size = value
        }

    var typeface: Typeface? = Typeface.DEFAULT
        set(value) {
            field = value
            font.setTypeface((value ?: Typeface.DEFAULT).skiaTypeface)
        }

    fun measureText(text: String): Float = font.measureTextWidth(text)

    /** 与 Android 一致：基线以上为负 */
    fun ascent(): Float = font.metrics.ascent

    fun descent(): Float = font.metrics.descent

    companion object {
        const val ANTI_ALIAS_FLAG = 0x01
        const val SUBPIXEL_TEXT_FLAG = 0x80
    }
}
