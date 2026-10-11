package me.rerere.ai.util

import me.rerere.ai.ui.UIMessagePart
import java.io.File
import java.net.URI
import java.util.Base64

/**
 * ai/util/FileEncoder.kt 的 JVM 实现，替代依赖 android.graphics / android.util.Base64 / ExifInterface
 * 的版本（那个文件在 linux/build.gradle.kts 里被 exclude）。
 *
 * 文件名带 Jvm 后缀是因为 exclude 的模式对两个 srcDir 都生效，同名会把自己的实现一起排掉。
 *
 * 与 Android 版本的差异：不解码、不缩放、不按 EXIF 方向旋转，原图直接 Base64，所以大图仍按原始体积发送。
 *
 * MPTODO: need full implementation
 */
data class EncodedImage(
    val base64: String,
    val mimeType: String,
)

fun UIMessagePart.Image.encodeBase64(withPrefix: Boolean = true): Result<EncodedImage> = runCatching {
    when {
        url.startsWith("file://") -> {
            val file = localFileOrThrow(url)
            val mimeType = file.guessMimeType()
            val encoded = file.encodeToBase64()
            EncodedImage(
                base64 = if (withPrefix) "data:$mimeType;base64,$encoded" else encoded,
                mimeType = mimeType,
            )
        }

        url.startsWith("data:") -> EncodedImage(
            base64 = url,
            mimeType = url.substringAfter("data:").substringBefore(";"),
        )

        // HTTP URL 无法确定 mime type，与 Android 版本一样默认按 image/png 处理
        url.startsWith("http") -> EncodedImage(base64 = url, mimeType = "image/png")

        else -> error("Unsupported URL format: $url")
    }
}

fun UIMessagePart.Video.encodeBase64(withPrefix: Boolean = true): Result<String> = runCatching {
    val encoded = localFileOrThrow(url).encodeToBase64()
    if (withPrefix) "data:video/mp4;base64,$encoded" else encoded
}

fun UIMessagePart.Audio.encodeBase64(withPrefix: Boolean = true): Result<String> = runCatching {
    val encoded = localFileOrThrow(url).encodeToBase64()
    if (withPrefix) "data:audio/mp3;base64,$encoded" else encoded
}

private fun localFileOrThrow(url: String): File {
    check(url.startsWith("file://")) { "Unsupported URL format: $url" }
    val path = URI(url).path ?: error("Invalid file URI: $url")
    val file = File(path)
    check(file.exists()) { "File does not exist: $url" }
    return file
}

private fun File.encodeToBase64(): String =
    inputStream().use { Base64.getEncoder().encodeToString(it.readBytes()) }

private fun File.guessMimeType(): String {
    val bytes = ByteArray(16)
    val read = inputStream().use { it.read(bytes) }
    check(read >= 12) { "File too short to determine MIME type" }

    // ISO-BMFF 容器：4..8 是 "ftyp"，8..12 是主品牌码
    if (bytes.copyOfRange(4, 8).toString(Charsets.US_ASCII) == "ftyp") {
        when (bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII)) {
            "heic", "heix", "heim", "heis", "hevc", "hevx", "hevm", "hevs", "mif1", "msf1", "heif" ->
                return "image/heic"

            "avif", "avis" -> return "image/avif"
        }
    }
    if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "image/jpeg"
    if (bytes.copyOfRange(0, 8).contentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        )
    ) {
        return "image/png"
    }
    if (bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
        bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP"
    ) {
        return "image/webp"
    }
    val header = bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII)
    check(header == "GIF89a" || header == "GIF87a") { "Failed to guess MIME type: $header" }
    return "image/gif"
}
