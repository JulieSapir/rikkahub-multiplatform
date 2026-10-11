package android.webkit

import java.net.URLConnection

/**
 * android.webkit.MimeTypeMap 的 JVM 版：JDK 自带的按扩展名猜测 + 一张常用表。
 *
 * Android 上它由系统提供，桌面端没有 webkit。
 */
class MimeTypeMap private constructor() {

    fun getMimeTypeFromExtension(extension: String): String? {
        val ext = extension.removePrefix(".").lowercase()
        return TYPES_BY_EXT[ext] ?: URLConnection.guessContentTypeFromName("file.$ext")
    }

    fun getExtensionFromMimeType(mimeType: String): String? =
        TYPES_BY_EXT.entries.firstOrNull { it.value == mimeType.lowercase() }?.key

    companion object {
        private val singleton = MimeTypeMap()

        @JvmStatic
        fun getSingleton(): MimeTypeMap = singleton

        private val TYPES_BY_EXT = mapOf(
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "png" to "image/png",
            "gif" to "image/gif",
            "webp" to "image/webp",
            "svg" to "image/svg+xml",
            "bmp" to "image/bmp",
            "mp4" to "video/mp4",
            "mov" to "video/quicktime",
            "webm" to "video/webm",
            "mkv" to "video/x-matroska",
            "avi" to "video/x-msvideo",
            "mp3" to "audio/mpeg",
            "wav" to "audio/wav",
            "ogg" to "audio/ogg",
            "m4a" to "audio/mp4",
            "flac" to "audio/flac",
            "pdf" to "application/pdf",
            "doc" to "application/msword",
            "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xls" to "application/vnd.ms-excel",
            "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "ppt" to "application/vnd.ms-powerpoint",
            "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "epub" to "application/epub+zip",
            "txt" to "text/plain",
            "md" to "text/markdown",
            "csv" to "text/csv",
            "json" to "application/json",
            "html" to "text/html",
            "htm" to "text/html",
            "xml" to "text/xml",
            "zip" to "application/zip",
        )
    }
}
