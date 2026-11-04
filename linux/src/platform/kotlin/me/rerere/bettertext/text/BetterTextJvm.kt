package me.rerere.bettertext.text

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * bettertext 的 text 模块在 Android 上用 StaticLayout + Span 自己排版、自己做选择；桌面端没有这套，
 * 改由 Compose 的 BasicText 排版：span 样式、LinkAnnotation 点击、行高都是它原生支持的，
 * 内嵌内容走 InlineTextContent 占位，选择交给 Compose 的 SelectionContainer。
 *
 * 对外 API（BetterText / BetterSelectionContainer / appendInline / DrawnInline / ComposableInline）
 * 与上游一致，Inline.kt 与 InlineExpansion.kt 原样编译；被 exclude 的是 BetterText.kt、
 * SelectionContainer.kt、Selection.kt、Spans.kt、TextLayout.kt。
 *
 * 与 Android 版的差异：
 * - 占位只能对齐文字中线（PlaceholderVerticalAlign.TextCenter），所以占位框做成以文字中线为中心、
 *   上下对称地包住内容，再在框里把内容的基线摆到文字基线上，效果等同于基线对齐
 * - DrawnInline 按整段文字的基础字号量，span 里改了字号的位置不跟随
 */

/** 一个占位：DrawnInline 的一段，或一个 ComposableInline */
private class InlineSlot(val id: String, val content: BetterInline, val segment: Int)

private class ConvertedText(val text: AnnotatedString, val slots: List<InlineSlot>)

/**
 * 把 appendInline 打的标注换成 Compose 的 inline content：每段一个占位。第一段占住原 alt text
 * （复制时得到的仍是它），多出来的段与空 alt text 的装饰用不可见的 U+2060 占位。
 */
private fun convert(text: AnnotatedString, inlineContent: Map<String, BetterInline>): ConvertedText {
    if (inlineContent.isEmpty()) return ConvertedText(text, emptyList())
    val annotations = text.getStringAnnotations(INLINE_TAG, 0, text.length).sortedBy { it.start }
    if (annotations.isEmpty()) return ConvertedText(text, emptyList())
    val slots = ArrayList<InlineSlot>()
    val builder = AnnotatedString.Builder(text.length + annotations.size)
    var pos = 0
    for (annotation in annotations) {
        // 嵌套或重叠的标注保留 alt text，与 InlineExpansion 一致
        if (annotation.start < pos || annotation.start > annotation.end) continue
        val content = inlineContent[annotation.item] ?: continue
        val segments = when (content) {
            is DrawnInline -> content.segmentCount
            is ComposableInline -> 1
        }
        builder.append(text.subSequence(pos, annotation.start))
        pos = annotation.end
        repeat(segments) { segment ->
            val id = "bettertext-inline-${slots.size}"
            slots += InlineSlot(id, content, segment)
            val alt = if (segment == 0 && annotation.end > annotation.start) {
                text.text.substring(annotation.start, annotation.end)
            } else {
                "⁠"
            }
            builder.appendInlineContent(id, alt)
        }
    }
    builder.append(text.subSequence(pos, text.length))
    return ConvertedText(builder.toAnnotatedString(), slots)
}

/** 以基线为 0：内容在基线上方 [ascent]、下方 [descent] 像素；[textCenter] 是文字中线（基线上方为负） */
private class SlotBox(val width: Float, ascent: Float, descent: Float, textCenter: Float) {
    /** 以文字中线为中心的半高，保证内容整个落在框里 */
    private val half = max(ascent + textCenter, descent - textCenter).coerceAtLeast(0f)
    val height: Float get() = 2 * half

    /** 框顶到文字基线的距离 */
    val baseline: Float = half - textCenter

    fun placeholder(density: Density): Placeholder = with(density) {
        Placeholder(
            width = max(width, 0f).toSp(),
            height = max(height, 1f).toSp(),
            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
        )
    }
}

private fun baseFontSizePx(style: TextStyle, density: Density): Float = with(density) {
    when {
        style.fontSize.isSp -> style.fontSize.toPx()
        style.fontSize.isEm -> 14.sp.toPx() * style.fontSize.value
        else -> 14.sp.toPx()
    }
}

/** 量 DrawnInline 与求文字中线用的画笔，字号是整段文字的基础字号 */
private class InlinePaints(fontSize: Float, private val color: Int) {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = fontSize }
    val textCenter: Float = (paint.ascent() + paint.descent()) / 2f

    /** DrawnInline 可能改画笔，每次交出去之前还原 */
    fun fresh(): android.graphics.Paint = paint.apply {
        color = this@InlinePaints.color
        style = android.graphics.Paint.Style.FILL
        typeface = android.graphics.Typeface.DEFAULT
    }
}

private class LayoutHolder {
    var result: TextLayoutResult? = null
    var composableContent: Map<String, InlineTextContent> = emptyMap()
}

/**
 * Displays [text] with inline content.
 *
 * Inline items are added with [appendInline] and resolved through [inlineContent].
 * Inside a [BetterSelectionContainer] the text is selectable. Tapping a
 * [androidx.compose.ui.text.LinkAnnotation] calls its listener, or opens its URL when it has none.
 *
 * @param maxLines lines beyond this are cut off with an ellipsis.
 */
@Composable
fun BetterText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    inlineContent: Map<String, BetterInline> = emptyMap(),
    maxLines: Int = Int.MAX_VALUE,
) {
    require(maxLines > 0) { "maxLines must be positive" }
    val density = LocalDensity.current
    val converted = remember(text, inlineContent) { convert(text, inlineContent) }
    val textColor = (if (style.color.isSpecified) style.color else Color.Black).toArgb()
    val paints = remember(style, density, textColor) { InlinePaints(baseFontSizePx(style, density), textColor) }
    val overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis

    // DrawnInline 的尺寸不依赖布局，组合期就能定下来
    val drawnContent = remember(converted, paints, density) {
        converted.slots.filter { it.content is DrawnInline }.associate { slot ->
            val inline = slot.content as DrawnInline
            val metrics = InlineMetrics()
            inline.measure(slot.segment, paints.fresh(), metrics)
            val box = SlotBox(metrics.width, metrics.ascent, metrics.descent, paints.textCenter)
            slot.id to InlineTextContent(box.placeholder(density)) {
                Spacer(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawIntoCanvas { canvas ->
                                inline.draw(
                                    slot.segment,
                                    android.graphics.Canvas(canvas.nativeCanvas),
                                    0f,
                                    box.baseline,
                                    paints.fresh(),
                                )
                            }
                        },
                )
            }
        }
    }
    val composableSlots =
        remember(converted) { converted.slots.withIndex().filter { it.value.content is ComposableInline } }
    val holder = remember { LayoutHolder() }

    if (composableSlots.isEmpty()) {
        BasicText(
            text = converted.text,
            modifier = modifier,
            style = style,
            overflow = overflow,
            maxLines = maxLines,
            inlineContent = drawnContent,
        )
        return
    }

    // ComposableInline 要先量出尺寸才能给占位，量好的 placeable 再按占位位置直接摆上去
    SubcomposeLayout(modifier) { constraints ->
        val loose = Constraints(maxWidth = constraints.maxWidth)
        val measured = composableSlots.map { (_, slot) ->
            subcompose(slot.id) { (slot.content as ComposableInline).content() }.map { it.measure(loose) }
        }
        val sizes = measured.flatMap { group ->
            val height = group.maxOfOrNull { it.height } ?: 0
            val baseline = group.firstOrNull()?.get(FirstBaseline)?.takeIf { it != AlignmentLine.Unspecified } ?: height
            listOf(group.maxOfOrNull { it.width } ?: 0, height, baseline)
        }
        // 每个内容在占位框里的上边距（框顶到内容顶）
        val offsets = FloatArray(composableSlots.size)
        holder.composableContent = composableSlots.withIndex().associate { (i, indexed) ->
            val slot = indexed.value
            val width = sizes[i * 3].toFloat()
            val height = sizes[i * 3 + 1].toFloat()
            val box = when ((slot.content as ComposableInline).align) {
                // 居中：内容中线对文字中线，框就是内容本身
                InlineAlign.Center -> SlotBox(width, height / 2f, height / 2f, 0f).also { offsets[i] = 0f }
                InlineAlign.Baseline -> {
                    val baseline = sizes[i * 3 + 2].toFloat()
                    SlotBox(width, baseline, height - baseline, paints.textCenter).also {
                        offsets[i] =
                            it.baseline - baseline
                    }
                }
            }
            slot.id to InlineTextContent(box.placeholder(this)) {}
        }
        val allContent = drawnContent + holder.composableContent
        val textPlaceable = subcompose("text") {
            BasicText(
                text = converted.text,
                style = style,
                overflow = overflow,
                maxLines = maxLines,
                inlineContent = allContent,
                onTextLayout = { holder.result = it },
            )
        }.first().measure(constraints)

        val rects = holder.result?.placeholderRects.orEmpty()
        layout(
            textPlaceable.width,
            textPlaceable.height,
            mapOf(FirstBaseline to textPlaceable[FirstBaseline], LastBaseline to textPlaceable[LastBaseline]),
        ) {
            textPlaceable.place(0, 0)
            composableSlots.forEachIndexed { i, (slotIndex, _) ->
                // 被 maxLines 截掉的占位没有位置
                val rect = rects.getOrNull(slotIndex) ?: return@forEachIndexed
                val y = (rect.top + offsets[i]).roundToInt()
                measured[i].forEach { it.place(rect.left.roundToInt(), y) }
            }
        }
    }
}

/** Displays plain [text]; see the [AnnotatedString] overload. */
@Composable
fun BetterText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    maxLines: Int = Int.MAX_VALUE,
) {
    BetterText(remember(text) { AnnotatedString(text) }, modifier, style, maxLines = maxLines)
}

/**
 * Makes the [BetterText]s in [content] selectable together. 桌面端直接用 Compose 的 SelectionContainer：
 * 鼠标拖选、Ctrl+C 与右键菜单都是它自带的。[obscuredPadding] 用于 Android 上隐藏被遮住的选择手柄，桌面端没有手柄。
 */
@Composable
fun BetterSelectionContainer(
    modifier: Modifier = Modifier,
    obscuredPadding: PaddingValues = PaddingValues(),
    content: @Composable () -> Unit,
) {
    SelectionContainer(modifier = modifier, content = content)
}
