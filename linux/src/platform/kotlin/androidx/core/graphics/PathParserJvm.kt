package androidx.core.graphics

import android.graphics.Path

/** androidx.core 的 SVG 路径解析，桌面端交给 Compose 的 PathParser */
object PathParser {
    fun createPathFromPathData(pathData: String): Path {
        val path = Path()
        androidx.compose.ui.graphics.vector.PathParser().parsePathString(pathData).toPath(path.compose)
        return path
    }
}
