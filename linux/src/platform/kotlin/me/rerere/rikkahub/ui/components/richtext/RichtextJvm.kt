package me.rerere.rikkahub.ui.components.richtext

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.ui.text.font.FontFamily
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import org.jsoup.Jsoup

/**
 * richtext 里几个用到 Android 专属实现的组件（WebView 画 mermaid、jlatexmath-android 画公式、
 * image-viewer 缩放图片）的桌面端版本；原文件在 linux/build.gradle.kts 里 exclude。
 * 代码块那一份（HighlightCodeBlock.kt）用的 me.rerere.highlight 是跨平台的，已直接复用上游实现。
 * 公式与 HTML 暂时按原文显示，不解析。
 */
/**
 * jlatexmath-android 的替身：桌面端不排版公式，只保留“一段公式 + 估算尺寸”的形状，
 * 让上游 Markdown 的表格/公式布局代码原样编译，公式按源码文本显示。
 */
class LatexImage(val latex: String, val textSizePx: Float) {
    val bounds: Rect = Rect(0, 0, estimateWidth(), estimateHeight())

    private fun estimateWidth(): Int = (latex.length * textSizePx * 0.55f).toInt().coerceAtMost(4096)
    private fun estimateHeight(): Int = (textSizePx * 1.6f).toInt()
}

fun assumeLatexSize(latex: String, fontSize: Float): Rect = LatexImage(latex, fontSize).bounds

fun splitLatex(
    latex: String,
    maxWidthPx: Float,
    fontSize: Float,
    color: Int,
): List<LatexImage> = listOf(LatexImage(latex, fontSize))

@Composable
fun LatexDrawable(
    drawable: LatexImage,
    modifier: Modifier = Modifier,
) {
    Text(
        text = drawable.latex,
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        modifier = modifier.padding(2.dp),
    )
}

@Composable
fun MathInline(
    latex: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
) {
    // 没有 jlatexmath 的 JVM 版本，先原样显示公式源码
    Text(
        text = latex,
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        modifier = modifier,
    )
}

@Composable
fun MathBlock(
    latex: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(10.dp),
    ) {
        MathInline(latex = latex, fontSize = fontSize)
    }
}

@Composable
fun SimpleHtmlBlock(
    html: String,
    modifier: Modifier = Modifier,
) {
    val text = runCatching { Jsoup.parse(html).text() }.getOrDefault(html)
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalContentColor.current,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
fun ZoomableAsyncImage(
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f,
) {
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier.fillMaxWidth(),
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
    )
}

