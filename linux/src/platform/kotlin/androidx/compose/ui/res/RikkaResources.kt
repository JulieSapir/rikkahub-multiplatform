package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalLocale
import me.rerere.rikkahub.linux.res.RikkaRes
import me.rerere.rikkahub.linux.res.VectorDrawableParser
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import java.util.Locale

/**
 * Android 端 androidx.compose.ui.res 的 JVM 版本，让上游 UI 代码里的 stringResource / painterResource
 * 原样编译。
 *
 * 数据来自 build/generated/rikkaRes（由 app/src/main/res 生成）。位图直接解码成 BitmapPainter，
 * VectorDrawable 的 xml 交给 VectorDrawableParser 翻成 ImageVector；两者都拿不到时返回透明画笔，
 * 调用处退化成没有图标。
 */
@Composable
fun stringResource(id: Int): String = RikkaRes.string(id, currentLocale())

@Composable
fun stringResource(id: Int, vararg formatArgs: Any?): String {
    val locale = currentLocale()
    val raw = RikkaRes.string(id, locale)
    return if (formatArgs.isEmpty()) raw else String.format(locale, raw, *formatArgs)
}

@Composable
fun painterResource(id: Int): Painter = painterResource(id, null)

@Composable
fun painterResource(id: Int, contentDescription: String?): Painter =
    rasterResource(id) ?: vectorResource(id) ?: ColorPainter(Color.Transparent)

@Composable
private fun rasterResource(id: Int): Painter? {
    val path = RikkaRes.resourcePath(id) ?: return null
    val bytes = RikkaRes.bytesOf(path) ?: return null
    return runCatching { BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap()) }.getOrNull()
}

/** res/drawable 下的矢量图先翻成 ImageVector，Icon 的 tint 才会和 Android 端一致 */
@Composable
private fun vectorResource(id: Int): Painter? {
    val path = RikkaRes.vectorResourcePath(id) ?: return null
    val xml = RikkaRes.textOf(path) ?: return null
    val image = remember(path) { VectorDrawableParser.parseText(xml) } ?: return null
    return rememberVectorPainter(image)
}

/** 桌面端的 LocalLocale 是 compose 自己的类型，换成 java.util.Locale 才能查表和做格式化 */
@Composable
private fun currentLocale(): Locale = Locale(LocalLocale.current.language, LocalLocale.current.region)
