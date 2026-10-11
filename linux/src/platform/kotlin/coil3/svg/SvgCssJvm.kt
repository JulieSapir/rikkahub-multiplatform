package coil3.svg

import coil3.request.ImageRequest

/**
 * Android 版 coil-svg 用这个扩展把一段 CSS 注入 SVG（上游拿它把提供商图标染成主题色）。
 * JVM 版的 SvgDecoder 走 Skia，没有 CSS 注入入口，这里先按“不改色”处理：
 * 图标以其自带配色显示，尺寸、位置与圆角容器完全一致。
 */
fun ImageRequest.Builder.css(css: String): ImageRequest.Builder = this

fun ImageRequest.Builder.style(css: String): ImageRequest.Builder = this
