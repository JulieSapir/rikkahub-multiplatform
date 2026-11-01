package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.github.panpf.zoomimage.CoilZoomAsyncImage

/**
 * 上游 ZoomableAsyncImage 用的 image-viewer 只有 Android AAR，桌面端换成 panpf zoomimage 的 coil3 版；
 * 原文件在 linux/build.gradle.kts 里 exclude。
 */
@Composable
fun ZoomableAsyncImage(
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f,
) {
    CoilZoomAsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier.fillMaxWidth(),
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
    )
}
