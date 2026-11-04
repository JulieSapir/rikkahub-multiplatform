package me.rerere.rikkahub.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.drew.imaging.ImageMetadataReader
import com.drew.imaging.png.PngChunkType
import com.drew.metadata.png.PngDirectory
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * utils/ImageUtils.kt 的桌面端版本（原文件绑 androidx.exifinterface 与 zxing 的 Android 解码路径，
 * 在 linux/build.gradle.kts 里 exclude）。
 *
 * 保留同名 API，行为上的差别只有：不按 EXIF 旋转（桌面端图片来源是文件与网络，不带相机 EXIF），
 * 以及缩放用 ImageIO 的等比采样。
 */
object ImageUtils {

    fun loadOptimizedBitmap(
        context: Context,
        uri: Uri,
        maxSize: Int = 1024,
    ): Bitmap? = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSize, maxSize)
        BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }.getOrNull()

    fun calculateInSampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        requestWidth: Int,
        requestHeight: Int,
    ): Int {
        if (sourceWidth <= 0 || sourceHeight <= 0) return 1
        var inSampleSize = 1
        while (sourceHeight / inSampleSize >= requestHeight * 2 && sourceWidth / inSampleSize >= requestWidth * 2) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    fun correctImageOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap = bitmap

    /** 酒馆角色卡把 JSON 放在 PNG 的 tEXt 块里，metadata-extractor 是纯 Java 的，与 Android 版实现相同。 */
    fun getTavernCharacterMeta(context: Context, uri: Uri): Result<String> = runCatching {
        val metadata = context.contentResolver.openInputStream(uri)?.use {
            ImageMetadataReader.readMetadata(it)
        } ?: error("Metadata is null, please check if the image is a character card")
        if (!metadata.containsDirectoryOfType(PngDirectory::class.java)) {
            error("No PNG directory found, please check if the image is a character card")
        }
        val pngDirectory = metadata.getDirectoriesOfType(PngDirectory::class.java)
            .firstOrNull { directory ->
                directory.pngChunkType == PngChunkType.tEXt &&
                    directory.getString(PngDirectory.TAG_TEXTUAL_DATA).startsWith("[chara:")
            } ?: error("No tEXt chunk found, please check if the image is a character card")

        val value = pngDirectory.getString(PngDirectory.TAG_TEXTUAL_DATA)
        val regex = Regex("""\[chara:\s*(.+?)]""")
        regex.find(value)?.groupValues?.get(1) ?: error("No character data found")
    }

    /**
     * 与上游 decodeQRCodeFromBitmap 同一条 zxing 链路：像素数组 → RGBLuminanceSource →
     * HybridBinarizer → MultiFormatReader。桌面端 Bitmap 内部是 BufferedImage，直接取像素。
     */
    fun decodeQRCodeFromBitmap(bitmap: Bitmap): String? = runCatching {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null
        val pixels = IntArray(width * height)
        bitmap.image.getRGB(0, 0, width, height, pixels, 0, width)

        val reader = MultiFormatReader()
        reader.setHints(mapOf(DecodeHintType.TRY_HARDER to true))
        val result = reader.decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels))))
        result.text
    }.onFailure {
        it.printStackTrace()
    }.getOrNull()

    /** 与 Android 版一致：loadOptimizedBitmap 解码后交给 zxing 识别，识别不出返回 null。 */
    fun decodeQRCodeFromUri(
        context: Context,
        uri: Uri,
        maxSize: Int = 2048,
    ): String? {
        val bitmap = loadOptimizedBitmap(context, uri, maxSize) ?: return null
        return try {
            decodeQRCodeFromBitmap(bitmap)
        } finally {
            bitmap.recycle()
        }
    }
}
