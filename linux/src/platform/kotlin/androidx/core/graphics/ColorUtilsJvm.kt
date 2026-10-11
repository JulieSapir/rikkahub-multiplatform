package androidx.core.graphics

import android.graphics.Color
/**
 * AndroidX 的颜色工具里上游只用到“混合 / 加透明度 / ARGB 分量”这几个纯算术方法，
 * 直接按 int ARGB 实现，语义与 Android 一致。
 */
object ColorUtils {
    fun setAlphaComponent(color: Int, alpha: Int): Int =
        (color and 0x00ffffff) or (alpha.coerceIn(0, 255) shl 24)

    fun blendARGB(color1: Int, color2: Int, ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        val a = lerp(Color.alpha(color1), Color.alpha(color2), r)
        val red = lerp(Color.red(color1), Color.red(color2), r)
        val green = lerp(Color.green(color1), Color.green(color2), r)
        val blue = lerp(Color.blue(color1), Color.blue(color2), r)
        return Color.argb(a, red, green, blue)
    }

    /** 与 Android 版一致：outHsl[0]=色相 0..360，[1]=饱和度 0..1，[2]=明度 0..1。 */
    fun colorToHSL(color: Int, outHsl: FloatArray) {
        require(outHsl.size >= 3) { "outHsl must have 3 slots" }
        val red = Color.red(color) / 255f
        val green = Color.green(color) / 255f
        val blue = Color.blue(color) / 255f
        val max = maxOf(red, green, blue)
        val min = minOf(red, green, blue)
        val luminance = (max + min) / 2f
        if (max == min) {
            outHsl[0] = 0f
            outHsl[1] = 0f
        } else {
            val d = max - min
            outHsl[1] = if (luminance > 0.5f) d / (2f - max - min) else d / (max + min)
            outHsl[0] = when (max) {
                red -> (green - blue) / d + (if (green < blue) 6f else 0f)
                green -> (blue - red) / d + 2f
                else -> (red - green) / d + 4f
            } * 60f
        }
        outHsl[2] = luminance
    }

    fun HSLToColor(hsl: FloatArray): Int {
        val h = hsl[0] / 360f
        val s = hsl[1]
        val l = hsl[2]
        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q
        fun hueToRgb(t: Float): Float {
            var x = t
            if (x < 0f) x += 1f
            if (x > 1f) x -= 1f
            return when {
                x < 1f / 6f -> p + (q - p) * 6f * x
                x < 1f / 2f -> q
                x < 2f / 3f -> p + (q - p) * (2f / 3f - x) * 6f
                else -> p
            }
        }
        return Color.rgb(
            (hueToRgb(h + 1f / 3f) * 255).toInt().coerceIn(0, 255),
            (hueToRgb(h) * 255).toInt().coerceIn(0, 255),
            (hueToRgb(h - 1f / 3f) * 255).toInt().coerceIn(0, 255),
        )
    }

    private fun lerp(from: Int, to: Int, ratio: Float): Int =
        Math.round(from + (to - from) * ratio).coerceIn(0, 255)
}
