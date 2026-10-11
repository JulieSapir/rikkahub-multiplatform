package android.graphics

/**
 * 上游用 android.graphics.Rect 表示公式图片的尺寸（width()/height() 与解构都会用到）。
 */
class Rect(
    var left: Int = 0,
    var top: Int = 0,
    var right: Int = 0,
    var bottom: Int = 0,
) {
    fun width(): Int = right - left
    fun height(): Int = bottom - top

    val w: Int get() = width()
    val h: Int get() = height()

    operator fun component1(): Int = left
    operator fun component2(): Int = top
    operator fun component3(): Int = right
    operator fun component4(): Int = bottom
}
