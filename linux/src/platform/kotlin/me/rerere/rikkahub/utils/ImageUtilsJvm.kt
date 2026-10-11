package me.rerere.rikkahub.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import com.drew.imaging.ImageMetadataReader
import com.drew.imaging.png.PngChunkType
import com.drew.metadata.png.PngDirectory
import java.io.File

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

    /** 与 Android 版一致：先看 MIME，再嗅探 ftyp 头，桌面端图片来源是文件，两条都走得通。 */
    fun isHeifImage(context: Context, uri: Uri): Boolean {
        context.contentResolver.getType(uri)?.lowercase()?.let { mime ->
            if ("heic" in mime || "heif" in mime) return true
        }
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(12)
                if (input.read(header) < 12) return@use false
                if (header.copyOfRange(4, 8).toString(Charsets.US_ASCII) != "ftyp") return@use false
                header.copyOfRange(8, 12).toString(Charsets.US_ASCII) in HEIF_BRANDS
            } ?: false
        }.getOrDefault(false)
    }

    /** JDK 的 ImageIO 解不了 HEIF，解码失败就返回 false，调用方按“原图直接用”处理。 */
    fun convertHeifToJpeg(
        context: Context,
        uri: Uri,
        target: File,
        maxSize: Int = 4096,
        quality: Int = 95,
    ): Boolean = runCatching {
        val decoded = loadOptimizedBitmap(context, uri, maxSize) ?: return@runCatching false
        try {
            target.outputStream().use { out -> decoded.compress(Bitmap.CompressFormat.JPEG, quality, out) }
            true
        } finally {
            decoded.recycle()
        }
    }.getOrDefault(false)

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
     * 上游用 zxing 的 Android 解码路径识别二维码；桌面端没有可用的 QR 解码器，
     * 返回 null，提供商页因此提示“图里没找到二维码”，用户改用粘贴 Key。
     */
    fun decodeQRCodeFromUri(
        context: Context,
        uri: Uri,
        maxSize: Int = 2048,
    ): String? = null

    private val HEIF_BRANDS = setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1", "heim", "hema", "hevm", "hevs")

    fun saveBitmap(bitmap: Bitmap, file: File, asPng: Boolean = true): Boolean {
        file.parentFile?.mkdirs()
        return file.outputStream().use { out ->
            bitmap.compress(
                if (asPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                92,
                out,
            )
        }
    }
}
