package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.text.selection.SelectionContainer

private val LocalInSelectionContainer = staticCompositionLocalOf { false }

/**
 * 上游用 bettertext 的 BetterSelectionContainer 让 BetterText 可长按选择（原文件已 exclude）。
 * 旧管线的文本是普通 Compose Text / AnnotatedString，这里换成 foundation 的 SelectionContainer，
 * 语义一致：已在一个选择容器里时直接并入外层（如消息里的代码块，代码能和正文连着选），
 * 不在时自己建一个。不要在这里面再套 SelectionContainer，里层的容器会把文字从外层拿走。
 */
@Composable
fun SelectableContent(content: @Composable () -> Unit) {
    if (LocalInSelectionContainer.current) {
        content()
    } else {
        SelectionContainer {
            CompositionLocalProvider(LocalInSelectionContainer provides true, content)
        }
    }
}
