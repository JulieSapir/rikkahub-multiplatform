package me.rerere.bettertext.highlight

import me.rerere.highlight.CodeHighlighter

/**
 * 上游把语法高亮抽成了 bettertext 库（PropertyEditor / SettingSearchDetailPage 的 JSON 编辑器
 * 从这里拿 LocalCodeHighlighter）。桌面端没有 bettertext（android.text 渲染，无 JVM 产物），保留的
 * 旧 highlight 模块有同一套 CodeHighlighter 实现，这里把 CompositionLocal 桥接过去。
 * 其余 bettertext 符号只被新渲染管线用到，已随管线一起 exclude，不需要兼容层。
 */
val LocalCodeHighlighter = me.rerere.highlight.LocalCodeHighlighter
