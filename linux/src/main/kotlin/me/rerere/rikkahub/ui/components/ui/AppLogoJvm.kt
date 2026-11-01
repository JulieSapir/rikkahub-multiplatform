package me.rerere.rikkahub.ui.components.ui

import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastForEach
import androidx.graphics.shapes.Morph
import me.rerere.rikkahub.R
import me.rerere.rikkahub.linux.res.RikkaRes
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 上游取 AdaptiveIconDrawable 的 background/foreground 两层交给 View 层画（原文件已 exclude）。
 * 桌面端没有 adaptive icon 这个概念：android-res 任务在构建期已经把 adaptive-icon 的两层合成了
 * 一张 PNG（与 Android 端 AsyncImage(model = R.mipmap.ic_launcher) 看到的等价），这里读出位图，
 * 用同一套 108dp 溢出 + 外层 clip 的画法画出。单层实现复用 [Drawable] 接口占位，保证签名不变。
 */
internal class LogoLayerDrawable internal constructor(
    val bitmap: ImageBitmap,
) : Drawable

@Composable
fun rememberAppLogoLayers(): List<Drawable> {
    return remember {
        runCatching {
            RikkaRes.resourcePath(R.mipmap.ic_launcher)?.let { path ->
                RikkaRes.bytesOf(path)?.let { bytes ->
                    val bitmap = org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
                    listOf<Drawable>(LogoLayerDrawable(bitmap))
                }
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }
}

/** 把 logo 铺满当前区域，轮廓由外面的 clip 决定 */
fun Modifier.drawAppLogo(layers: List<Drawable>): Modifier = drawBehind {
    layers.fastForEach { layer ->
        if (layer is LogoLayerDrawable) {
            layer.bitmap.let { bitmap ->
                val bounds = logoLayerBounds(size)
                drawImage(
                    image = bitmap,
                    dstOffset = androidx.compose.ui.unit.IntOffset(bounds.left, bounds.top),
                    dstSize = androidx.compose.ui.unit.IntSize(
                        bounds.width().coerceAtLeast(1),
                        bounds.height().coerceAtLeast(1),
                    ),
                )
            }
        }
    }
}

/**
 * logo 的轮廓：[position] 的整数部分是第几段变形，小数部分是这段的进度，每段转 90 度，
 * [extraRotation] 叠加在上面（比如加载时的整体自转）。
 * 停在整数上时是一个完整的形状；弹簧过冲的部分落在下一段变形的开头。
 */
fun logoMorphShape(position: Float, extraRotation: Float = 0f): Shape {
    val segment = floor(position).toInt()
    return MorphShape(
        morph = LogoMorphs[segment.mod(LogoMorphs.size)],
        progress = position - segment,
        rotation = position * 90f + extraRotation,
    )
}

// 自适应图标的图层是 108dp，可见的只有中间 72dp，四周各多出四分之一
private fun logoLayerBounds(size: Size): Rect {
    val insetX = (size.width / 4f).roundToInt()
    val insetY = (size.height / 4f).roundToInt()
    return Rect(-insetX, -insetY, size.width.roundToInt() + insetX, size.height.roundToInt() + insetY)
}

// logo 依次变成的形状，最后一个接回第一个
private val LogoShapes = listOf(
    MaterialShapes.Cookie12Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Sunny,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Flower,
    MaterialShapes.Cookie4Sided,
)

private val LogoMorphs = LogoShapes.indices.map { index ->
    Morph(LogoShapes[index], LogoShapes[(index + 1) % LogoShapes.size])
}

// 停在两个形状之间某一帧的轮廓
private class MorphShape(
    private val morph: Morph,
    private val progress: Float,
    private val rotation: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress = progress)
        // MaterialShapes 是归一化到 1x1 的，先放大到实际尺寸，再绕中心旋转
        val matrix = Matrix().apply {
            translate(size.width / 2f, size.height / 2f)
            rotateZ(rotation)
            translate(-size.width / 2f, -size.height / 2f)
            scale(size.width, size.height)
        }
        path.transform(matrix)
        return Outline.Generic(path)
    }
}
