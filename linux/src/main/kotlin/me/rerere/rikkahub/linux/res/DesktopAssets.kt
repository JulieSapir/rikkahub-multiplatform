package me.rerere.rikkahub.linux.res

import me.rerere.rikkahub.linux.state.AppPaths
import java.io.File

/**
 * 把打包进 jar 的资源按需释放到磁盘上，Coil 的解码器只认文件路径。
 *
 * 逐个文件释放比整目录拷贝省得多（icons 只是几十个小 SVG，simple_dict 有 14M）。
 * 已释放过的文件按大小判断跳过。
 */
object DesktopAssets {

    /** Android 端的 `file:///android_asset/<path>` 落到 `<dataDir>/assets/<path>`。 */
    fun extract(relativePath: String): File? =
        extractTo(File(File(AppPaths.dataDir, ASSETS_DIR), relativePath), "$ASSETS_DIR/$relativePath")

    /** 给 Coil 当数据源用的绝对 URI（file://）：上游图标路径的兼容改写就调这个。 */
    fun uriOf(relativePath: String): String? = extract(relativePath)?.toURI()?.toString()

    /**
     * `AsyncImage(model = R.mipmap.ic_launcher)` 这类资源 id 指向的条目在 classpath 的 rikka-res
     * 目录下，同样解到 `<dataDir>/extracted` 下。
     */
    fun extractResource(resourcePath: String): File? {
        val relative = resourcePath.removePrefix("/")
        return extractTo(File(File(AppPaths.dataDir, EXTRACTED_DIR), relative), relative)
    }

    private fun extractTo(target: File, classpathPath: String): File? {
        // 只判断“存在且非空”会让旧版本解出来的文件永远留在磁盘上（图标换过一版后仍然显示旧的），
        // 所以拿 jar 条目的大小比一下，不一致就重新解
        val size = resourceUrl(classpathPath)?.let { url ->
            runCatching { url.openConnection().contentLengthLong.takeIf { it > 0 } }.getOrNull()
        }
        if (target.isFile && target.length() > 0 && (size == null || target.length() == size)) return target
        val bytes = readClasspath(classpathPath) ?: return null
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
        return target
    }

    private fun resourceUrl(path: String): java.net.URL? =
        DesktopAssets::class.java.classLoader.getResource(path)
            ?: DesktopAssets::class.java.getResource("/$path")

    fun readClasspath(path: String): ByteArray? =
        resourceUrl(path)?.openStream()?.use { it.readBytes() }

    private const val ASSETS_DIR = "assets"
    private const val EXTRACTED_DIR = "extracted"
}
