package me.rerere.rikkahub.linux.font

import androidx.compose.ui.text.font.FontFamily
import java.io.File

/**
 * 自定义聊天字体：Android 端用 Font(File) 直接加载 TTF，JB 端只能按家族名取系统字体。
 * 所以先把字体注册进 AWT 的字体库（进程内全局可见），再用它报告的家族名构造 FontFamily。
 */
fun desktopFontFamily(file: File): FontFamily? = runCatching {
    val awt = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, file)
    java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(awt)
    FontFamily(awt.family)
}.getOrNull()
