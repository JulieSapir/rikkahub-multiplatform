package android.graphics

/**
 * android.graphics.Path 的桌面端实现，内部是 Compose 的 Path（桌面端由 Skia 支撑），
 * 这样 SVG 路径解析可以直接用 Compose 的 PathParser。
 */
class Path internal constructor(internal val compose: androidx.compose.ui.graphics.Path) {
    constructor() : this(androidx.compose.ui.graphics.Path())

    fun reset() {
        compose.reset()
    }

    fun set(src: Path) {
        compose.reset()
        compose.addPath(src.compose)
    }

    fun transform(matrix: Matrix) {
        compose.transform(matrix.toComposeMatrix())
    }
}

class RectF(
    var left: Float = 0f,
    var top: Float = 0f,
    var right: Float = 0f,
    var bottom: Float = 0f,
) {
    fun set(left: Float, top: Float, right: Float, bottom: Float) {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
    }

    fun width(): Float = right - left

    fun height(): Float = bottom - top
}
