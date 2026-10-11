package me.rerere.rikkahub.ui.hooks

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Shape
import kotlin.math.roundToInt

/**
 * 上游版本无条件建 infiniteTransition，即使 loading=false 也照样每帧写一次动画值，
 * 于是帧时钟永远不空转，整个窗口按 vsync 不停重绘（静止页面也吃满一个核的一部分）。
 * 桌面端每帧都是真的重画并 present，代价比 Android 上明显，所以这里只在 loading 时才开动画。
 *
 * 上游的 AvatarShape.kt 在 linux/build.gradle.kts 里 exclude。
 */
@Composable
fun rememberAvatarShape(loading: Boolean): Shape {
    if (!loading) return CircleShape
    val infiniteTransition = rememberInfiniteTransition()
    val rotateAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 3000,
                easing = LinearEasing
            ),
        )
    )
    return MaterialShapes.Cookie6Sided.toShape(rotateAngle.roundToInt())
}
