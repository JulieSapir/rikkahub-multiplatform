package me.rerere.rikkahub.ui.components.richtext

import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import org.scilab.forge.jlatexmath.TeXIcon
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.jsoup.Jsoup

/**
 * richtext 里几个用到 Android 专属实现的组件（WebView 画 mermaid、jlatexmath-android 画公式、
 * image-viewer 缩放图片）的桌面端版本；原文件在 linux/build.gradle.kts 里 exclude。
 * 代码块那一份（HighlightCodeBlock.kt）用的 me.rerere.highlight 是跨平台的，已直接复用上游实现。
 * 公式走纯 Java 的 jlatexmath 排版（与 Android 端同源的引擎），HTML 暂时按原文显示。
 */
/**
 * jlatexmath-android 的替身：用 jlatexmath 实际排版出 TeXIcon，bounds 是实测尺寸；
 * 公式非法（TeXFormula 抛 ParseException）时退回旧 stub 的长度估算，让调用方按原文本兜底。
 * colorArgb 是 ARGB 前景色（与上游 splitLatex/getLatexDrawable 的 color 参数一致），null 时用引擎默认黑色。
 */
class LatexImage(val latex: String, val textSizePx: Float, val colorArgb: Int? = null) {
    // 排版一次：bounds 供 Markdown 的占位测量在组合期读，位图留到真正绘制时再渲染
    internal val icon: TeXIcon? = renderLatexIcon(processLatex(latex), textSizePx, colorArgb)

    val bounds: Rect = icon?.let { Rect(0, 0, it.iconWidth, it.iconHeight) }
        ?: Rect(0, 0, estimateWidth(), estimateHeight())

    private fun estimateWidth(): Int = (latex.length * textSizePx * 0.55f).toInt().coerceAtMost(4096)
    private fun estimateHeight(): Int = (textSizePx * 1.6f).toInt()

    internal val bitmap: ImageBitmap by lazy {
        val rendered = requireNotNull(icon) { "latex 解析失败，没有可绘制的位图" }
        val awtImage = java.awt.image.BufferedImage(
            rendered.iconWidth.coerceAtLeast(1),
            rendered.iconHeight.coerceAtLeast(1),
            java.awt.image.BufferedImage.TYPE_INT_ARGB,
        )
        val g = awtImage.createGraphics()
        rendered.paintIcon(null, g, 0, 0)
        g.dispose()
        // 走 PNG 编解码交给 skia，透明通道与抗锯齿都能保留（与 asImageBitmap 的桥接同一条路）
        val bytes = ByteArrayOutputStream().also { ImageIO.write(awtImage, "png", it) }.toByteArray()
        org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }
}

private fun renderLatexIcon(latex: String, fontSizePx: Float, colorArgb: Int?): TeXIcon? = runCatching {
    val icon = TeXFormula(latex).createTeXIcon(TeXConstants.STYLE_DISPLAY, fontSizePx)
    if (colorArgb != null) icon.setForeground(java.awt.Color(colorArgb, true))
    icon
}.getOrNull()

fun assumeLatexSize(latex: String, fontSize: Float): Rect = LatexImage(latex, fontSize).bounds

/**
 * 上游 fork 的 JLatexMathSplitter 语义的桌面移植：整体渲染不超宽就返回单元素；
 * 超宽时按顶层二元运算符/关系符（括号、\left..\right、\begin..\end 内不算）贪心切分成多段，
 * 每段实测宽度不超 maxWidth；任何一段解析失败返回空列表，由调用方回退为单体渲染。
 */
fun splitLatex(
    latex: String,
    maxWidthPx: Float,
    fontSize: Float,
    color: Int,
): List<LatexImage> = runCatching {
    val processed = processLatex(latex)
    if (processed.isBlank()) return emptyList()
    val splitPositions = findTopLevelSplitPositions(processed)
    if (splitPositions.isEmpty()) return listOf(buildLatexImage(processed, fontSize, color))

    // 分段起点永远落在运算符上（运算符开新行，符合排版惯例），所以切割边界就是这些位置
    val boundaries = splitPositions + processed.length
    val segments = mutableListOf<String>()
    var segmentStart = 0
    var lastGoodEnd = -1
    for (boundary in boundaries) {
        if (boundary <= segmentStart) continue
        val candidate = processed.substring(segmentStart, boundary).trim()
        if (candidate.isEmpty()) continue
        if (measureLatexWidth(candidate, fontSize) <= maxWidthPx) {
            lastGoodEnd = boundary
        } else if (lastGoodEnd > segmentStart) {
            // 冲出去之前把最后一段放行的内容落袋，再从新起点重新量
            segments += processed.substring(segmentStart, lastGoodEnd).trim()
            segmentStart = lastGoodEnd
            lastGoodEnd = -1
            val restarted = processed.substring(segmentStart, boundary).trim()
            if (restarted.isNotEmpty() && measureLatexWidth(restarted, fontSize) <= maxWidthPx) {
                lastGoodEnd = boundary
            }
        } else {
            // 起点之后没有能切的位置，这段再宽也原样保留
            segments += candidate
            segmentStart = boundary
            lastGoodEnd = -1
        }
    }
    if (segmentStart < processed.length) {
        processed.substring(segmentStart).trim().takeIf { it.isNotEmpty() }?.let { segments += it }
    }

    val images = segments.map { buildLatexImage(it, fontSize, color) }
    // 段落是整体式子的子串，整体能解析不代表每段都能解析，只要有一段失败就走调用方的回退路径
    if (images.isEmpty() || images.any { it.icon == null }) emptyList() else images
}.getOrElse { emptyList() }

private fun buildLatexImage(latex: String, fontSize: Float, color: Int): LatexImage {
    val image = LatexImage(latex, fontSize, color)
    if (image.icon == null) error("latex 解析失败: $latex")
    return image
}

private fun measureLatexWidth(latex: String, fontSize: Float): Float =
    renderLatexIcon(latex, fontSize, null)?.iconWidth?.toFloat() ?: Float.MAX_VALUE

private fun findTopLevelSplitPositions(latex: String): List<Int> {
    val positions = mutableListOf<Int>()
    var depth = 0
    var i = 0
    while (i < latex.length) {
        val c = latex[i]
        when {
            c == '{' || c == '(' || c == '[' -> depth++
            (c == '}' || c == ')' || c == ']') && depth > 0 -> depth--
            c == '\\' -> {
                val cmdEnd = findCommandEnd(latex, i)
                val cmd = latex.substring(i, cmdEnd)
                when {
                    cmd == "\\left" || cmd == "\\begin" -> depth++
                    cmd == "\\right" || cmd == "\\end" -> if (depth > 0) depth--
                    depth == 0 && cmd in SPLIT_COMMANDS -> positions.add(i)
                }
                i = cmdEnd
                continue
            }
            depth == 0 && (c == '+' || c == '-' || c == '=' || c == '<' || c == '>') -> positions.add(i)
        }
        i++
    }
    return positions
}

private fun findCommandEnd(latex: String, start: Int): Int {
    var i = start + 1
    if (i >= latex.length) return i
    if (!latex[i].isLetter()) return i + 1
    while (i < latex.length && latex[i].isLetter()) i++
    return i
}

private val SPLIT_COMMANDS = setOf(
    "\\leq", "\\le", "\\geq", "\\ge",
    "\\neq", "\\ne", "\\approx", "\\equiv",
    "\\sim", "\\simeq", "\\cong",
    "\\subset", "\\supset", "\\subseteq", "\\supseteq",
    "\\sqsubset", "\\sqsupset", "\\sqsubseteq", "\\sqsupseteq",
    "\\in", "\\notin", "\\ni",
    "\\to", "\\gets", "\\rightarrow", "\\leftarrow",
    "\\Rightarrow", "\\Leftarrow", "\\Leftrightarrow", "\\leftrightarrow",
    "\\longrightarrow", "\\longleftarrow",
    "\\Longrightarrow", "\\Longleftarrow", "\\Longleftrightarrow",
    "\\implies", "\\iff",
    "\\propto", "\\perp", "\\parallel",
    "\\vdash", "\\dashv", "\\models",
    "\\asymp", "\\bowtie", "\\smile", "\\frown",
    "\\pm", "\\mp",
    "\\times", "\\div", "\\cdot",
    "\\cup", "\\cap", "\\sqcup", "\\sqcap",
    "\\oplus", "\\ominus", "\\otimes", "\\oslash", "\\odot",
    "\\wedge", "\\vee",
    "\\setminus", "\\circ", "\\bullet",
    "\\star", "\\ast", "\\dagger", "\\ddagger",
    "\\amalg", "\\uplus",
)

@Composable
private fun LatexText(
    latex: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
) {
    val style = style.merge(fontSize = fontSize, color = color)
    val density = LocalDensity.current

    val drawable = remember(latex, fontSize, style) {
        runCatching {
            with(density) {
                LatexImage(
                    latex = processLatex(latex),
                    textSizePx = fontSize.takeOrElse { style.fontSize }.toPx(),
                    colorArgb = style.color.takeOrElse { Color.Black }.toArgb(),
                )
            }
        }.onFailure {
            it.printStackTrace()
        }.getOrNull()
    }

    if (drawable != null) {
        LatexDrawable(drawable = drawable, modifier = modifier)
    } else {
        Text(text = latex, style = style, modifier = modifier)
    }
}

@Composable
fun LatexDrawable(
    drawable: LatexImage,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    if (drawable.icon != null) {
        val bitmap = remember(drawable) { drawable.bitmap }
        with(density) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = modifier.size(
                    width = drawable.bounds.width().toDp(),
                    height = drawable.bounds.height().toDp(),
                ),
            )
        }
    } else {
        // 解析失败回退按源码文本显示（与上游 LatexText 的回退一致）
        Text(
            text = drawable.latex,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = modifier.padding(2.dp),
        )
    }
}

@Composable
fun MathInline(
    latex: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
) {
    LatexText(
        latex = latex,
        color = LocalContentColor.current,
        fontSize = fontSize.takeOrElse { LocalTextStyle.current.fontSize },
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
        modifier = modifier.padding(8.dp),
    ) {
        LatexText(
            latex = latex,
            color = LocalContentColor.current,
            fontSize = fontSize.takeOrElse { LocalTextStyle.current.fontSize },
            modifier = Modifier
                .align(Alignment.Center)
                .horizontalScroll(rememberScrollState()),
        )
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
    CoilZoomAsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier.fillMaxWidth(),
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
    )
}

private val inlineDollarRegex = Regex("""^\$(.*?)\$""", RegexOption.DOT_MATCHES_ALL)
private val displayDollarRegex = Regex("""^\$\$(.*?)\$\$""", RegexOption.DOT_MATCHES_ALL)
private val inlineParenRegex = Regex("""^\\\((.*?)\\\)""", RegexOption.DOT_MATCHES_ALL)
private val displayBracketRegex = Regex("""^\\\[(.*?)\\\]""", RegexOption.DOT_MATCHES_ALL)

private fun processLatex(latex: String): String {
    val trimmed = latex.trim()
    return when {
        displayDollarRegex.matches(trimmed) ->
            displayDollarRegex.find(trimmed)?.groupValues?.get(1)?.trim() ?: trimmed

        inlineDollarRegex.matches(trimmed) ->
            inlineDollarRegex.find(trimmed)?.groupValues?.get(1)?.trim() ?: trimmed

        displayBracketRegex.matches(trimmed) ->
            displayBracketRegex.find(trimmed)?.groupValues?.get(1)?.trim() ?: trimmed

        inlineParenRegex.matches(trimmed) ->
            inlineParenRegex.find(trimmed)?.groupValues?.get(1)?.trim() ?: trimmed

        else -> trimmed
    }
}
