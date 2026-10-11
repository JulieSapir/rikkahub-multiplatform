package android.net

import java.io.File
import java.net.URI

/**
 * android.net.Uri 的最小替代：只处理 file:// 与 http(s)://，content:// 一律不支持。
 *
 * 上游大量代码用 Uri 走 SAF（系统文件选择器），桌面端直接用文件路径，所以 content:// 分支在
 * 桌面端不会走到；真走到了就是没排除干净的功能，会在运行时明确报错。
 */
class Uri private constructor(private val uri: URI) {

    val scheme: String? get() = uri.scheme
    val host: String? get() = uri.host
    val port: Int get() = if (uri.port > 0) uri.port else -1
    val path: String? get() = uri.path
    val query: String? get() = uri.query?.takeIf { it.isNotEmpty() }
    val fragment: String? get() = uri.fragment?.takeIf { it.isNotEmpty() }
    val userInfo: String? get() = uri.userInfo
    val lastPathSegment: String? get() = uri.path?.trimEnd('/')?.substringAfterLast('/')

    val isFile: Boolean get() = uri.scheme == "file"

    fun toFile(): File = File(path ?: error("Uri 没有路径: $uri"))

    override fun toString(): String = uri.toString()

    companion object {
        fun parse(text: String): Uri = Uri(encodeIfNeeded(text))

        fun fromFile(file: File): Uri = Uri(file.toURI())

        fun fromParts(scheme: String, ssp: String, fragment: String?): Uri =
            Uri(URI(scheme, ssp, fragment))

        fun urlEncode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

        fun encode(value: String, allowed: String? = null): String = urlEncode(value)

        private fun encodeIfNeeded(text: String): URI =
            runCatching { URI(text) }.getOrElse { URI(null, null, text, null) }
    }
}

fun String.toAndroidUri(): Uri = Uri.parse(this)
