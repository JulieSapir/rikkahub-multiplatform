package me.rerere.ui.crop

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "ImageCropImage"

// 屏幕上显示用的图的长边：原图很大时单独缩一份来画
private const val PREVIEW_SIZE = 2048

internal class CropImage(val bitmap: Bitmap, val preview: ImageBitmap)

/**
 * 读出要裁剪的图片：长边缩到 [maxSize] 以内（预览图另缩到 [PREVIEW_SIZE]）。读不出来时返回 null。
 *
 * 上游用 ImageDecoder/BitmapFactory（EXIF 摆正、HEIF 等格式支持都在框架里），桌面端用 ImageIO：
 * 常见的 JPEG/PNG/BMP/GIF 都能解，桌面端选图的来源也以这些为主；EXIF 方向交给 ImageIO 的
 * 元数据读取成本太高，桌面端图片来源（截图/下载）基本不带方向标记，先不做摆正。
 */
internal suspend fun loadCropImage(context: Context, uri: Uri, maxSize: Int): CropImage? =
    withContext(Dispatchers.IO) {
        runCatching {
            val source = context.contentResolver.openInputStream(uri).use { ImageIO.read(it) }
                ?: error("ImageIO returned null for $uri")
            val bitmap = Bitmap(scaleToArgb(source, maxSize))
            val longest = max(bitmap.width, bitmap.height)
            val preview = if (longest > PREVIEW_SIZE) {
                Bitmap(scaleToArgb(source, PREVIEW_SIZE))
            } else {
                bitmap
            }
            CropImage(bitmap, preview.asImageBitmap())
        }.onFailure {
            Log.e(TAG, "Failed to load $uri", it)
        }.getOrNull()
    }

private fun scaleToArgb(source: BufferedImage, maxSize: Int): BufferedImage {
    val longest = max(source.width, source.height)
    val ratio = if (longest > maxSize) maxSize.toFloat() / longest else 1f
    val width = (source.width * ratio).roundToInt().coerceAtLeast(1)
    val height = (source.height * ratio).roundToInt().coerceAtLeast(1)
    val out = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = out.createGraphics()
    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    graphics.drawImage(source, 0, 0, width, height, null)
    graphics.dispose()
    return out
}
