package androidx.compose.ui.graphics

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Android 的 Bitmap.asImageBitmap() 在桌面端不存在，这里桥到 skiko：
 * 位图替身内部是 BufferedImage，重新编码成 PNG 交给 skia 解码即可
 * （只用于图片预览/缩略图，不在热路径上）。
 */
fun Bitmap.asImageBitmap(): ImageBitmap = runCatching {
    val bytes = ByteArrayOutputStream()
    ImageIO.write(image, "png", bytes)
    org.jetbrains.skia.Image.makeFromEncoded(bytes.toByteArray()).toComposeImageBitmap()
}.getOrElse {
    ImageBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1))
}
