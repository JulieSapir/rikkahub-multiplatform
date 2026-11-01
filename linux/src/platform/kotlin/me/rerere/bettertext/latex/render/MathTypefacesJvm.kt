package me.rerere.bettertext.latex.render

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import me.rerere.bettertext.latex.layout.GlyphMeasurer
import java.util.concurrent.ConcurrentHashMap

/**
 * bettertext 的 MathTypefaces 桌面端版本：KaTeX 字体由 linux/build.gradle.kts 的 extractKatexFonts
 * 从 latex AAR 的 res/font 抽到 jar 资源 /bettertext/fonts 下，这里按文件名加载（Android 版走 R.font）。
 * 对外形状与上游一致。
 */
internal class MathTypefaces private constructor() {
    private val cache = ConcurrentHashMap<String, Typeface>()

    fun get(font: String): Typeface = cache.getOrPut(font) {
        load(fontFiles[font] ?: fontFiles.getValue("Main-Regular")) ?: Typeface.SERIF
    }

    /** Measures glyphs that the metric tables don't cover, using the real fonts (with system fallback). */
    val measurer: GlyphMeasurer = object : GlyphMeasurer {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = MEASURE_SIZE }

        override fun measureWidth(text: String, font: String): Float = synchronized(paint) {
            paint.typeface = get(font)
            paint.measureText(text) / MEASURE_SIZE
        }
    }

    private fun load(file: String): Typeface? = runCatching {
        val bytes = MathTypefaces::class.java.getResourceAsStream("/bettertext/fonts/$file.ttf")
            ?.use { it.readBytes() } ?: return null
        Typeface.createFromBytes(bytes)
    }.getOrNull()

    companion object {
        private const val MEASURE_SIZE = 100f

        private val instance by lazy { MathTypefaces() }

        fun get(context: Context): MathTypefaces = instance

        private val fontFiles = listOf(
            "AMS-Regular", "Caligraphic-Regular", "Caligraphic-Bold", "Fraktur-Regular", "Fraktur-Bold",
            "Main-Bold", "Main-BoldItalic", "Main-Italic", "Main-Regular", "Math-BoldItalic", "Math-Italic",
            "SansSerif-Bold", "SansSerif-Italic", "SansSerif-Regular", "Script-Regular",
            "Size1-Regular", "Size2-Regular", "Size3-Regular", "Size4-Regular", "Typewriter-Regular",
        ).associateWith { "katex_" + it.lowercase().replace('-', '_') }
    }
}
