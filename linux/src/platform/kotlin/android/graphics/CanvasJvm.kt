package android.graphics

import androidx.compose.ui.graphics.asSkiaPath

/**
 * android.graphics.Canvas 的桌面端实现，包一层 Skia Canvas（Compose 桌面端 nativeCanvas 拿到的就是它）。
 *
 * 只覆盖上游自绘内容用到的方法：bettertext 的公式渲染（MathRenderer）与 Markdown 里颜色代码前的色块。
 */
class Canvas(val skia: org.jetbrains.skia.Canvas) {
    fun save(): Int = skia.save()

    fun restore() {
        skia.restore()
    }

    fun clipRect(left: Float, top: Float, right: Float, bottom: Float): Boolean {
        skia.clipRect(org.jetbrains.skia.Rect.makeLTRB(left, top, right, bottom), true)
        return true
    }

    fun clipRect(rect: RectF): Boolean = clipRect(rect.left, rect.top, rect.right, rect.bottom)

    fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        skia.drawRect(org.jetbrains.skia.Rect.makeLTRB(left, top, right, bottom), paint.skia)
    }

    fun drawRoundRect(left: Float, top: Float, right: Float, bottom: Float, rx: Float, ry: Float, paint: Paint) {
        skia.drawRRect(org.jetbrains.skia.RRect.makeLTRB(left, top, right, bottom, rx, ry), paint.skia)
    }

    fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
        skia.drawLine(startX, startY, stopX, stopY, paint.skia)
    }

    fun drawPath(path: Path, paint: Paint) {
        skia.drawPath(path.compose.asSkiaPath(), paint.skia)
    }

    fun drawText(text: String, x: Float, y: Float, paint: Paint) {
        skia.drawString(text, x, y, paint.font, paint.skia)
    }
}
