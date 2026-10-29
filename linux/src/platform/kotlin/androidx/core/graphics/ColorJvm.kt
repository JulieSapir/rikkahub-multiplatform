package androidx.core.graphics

/**
 * androidx.core.graphics.toColorInt 的桌面端版本。
 *
 * Android 返回 ARGB Int，这里返回 Compose 桌面端 Color(Long) 能直接用的 sRGB Long。
 */
fun String.toColorInt(): Long {
    val hex = removePrefix("#")
    val full = when (hex.length) {
        // #RGB 补满不透明 alpha，#ARGB 每位展开成两位，对齐 VectorDrawableParser 的解析
        3 -> "ff${hex.map { "$it$it" }.joinToString("")}"
        4 -> hex.map { "$it$it" }.joinToString("")
        6 -> "ff$hex"
        8 -> hex
        else -> error("无法解析颜色: $this")
    }
    return full.toULong(16).toLong()
}
