package android.graphics

/** 上游只用到 ARGB 分量拆装，直接按 int 位运算实现。 */
object Color {
    const val BLACK = 0xff000000.toInt()
    const val WHITE = 0xffffffff.toInt()
    const val TRANSPARENT = 0

    fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        (alpha and 0xff shl 24) or (red and 0xff shl 16) or (green and 0xff shl 8) or (blue and 0xff)

    fun alpha(color: Int): Int = color ushr 24

    fun red(color: Int): Int = color shr 16 and 0xff

    fun green(color: Int): Int = color shr 8 and 0xff

    fun blue(color: Int): Int = color and 0xff

    fun rgb(red: Int, green: Int, blue: Int): Int = 0xff000000.toInt() or (red shl 16) or (green shl 8) or blue
}
