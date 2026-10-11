package android.content.res

import java.io.File
import me.rerere.rikkahub.linux.res.DesktopAssets
import java.io.InputStream

/**
 * Android 的 assets 目录在桌面端对应 jar 里的 /assets 资源（由资源桥接任务拷进来）。
 * 上游用它读内置技能包、emoji 表与 jieba 词典。
 */
class AssetManager(private val root: File) {

    /** jar 内列不出目录，用生成任务写好的索引还原层级 */
    private val index: List<String> by lazy {
        val fromClasspath = DesktopAssets.readClasspath(ASSET_INDEX)
            ?.decodeToString()?.lineSequence()?.filter { it.isNotBlank() }?.toList()
        fromClasspath?.takeIf { it.isNotEmpty() }
            ?: runCatching { File(root, "assets-index.txt").readLines().filter { it.isNotBlank() } }
                .getOrDefault(emptyList())
    }

    fun list(path: String): Array<String>? {
        val normalized = path.trimEnd('/')
        val local = if (normalized.isEmpty()) root else File(root, normalized)
        if (local.isDirectory) {
            return local.list()?.filterNot { it.startsWith(".") }?.toTypedArray()
        }
        val prefix = if (normalized.isEmpty()) "" else "$normalized/"
        val children = index.mapNotNull { entry ->
            if (!entry.startsWith("$prefix")) return@mapNotNull null
            entry.removePrefix(prefix).substringBefore('/')
        }.distinct()
        return children.takeIf { it.isNotEmpty() }?.toTypedArray()
    }

    fun open(path: String): InputStream {
        val file = File(root, path)
        if (file.isFile) return file.inputStream()
        return readBytes("assets/$path")?.inputStream() ?: throw java.io.FileNotFoundException(path)
    }

    fun close() = Unit

    private fun readBytes(path: String): ByteArray? = DesktopAssets.readClasspath(path)

    companion object {
        private const val ASSET_INDEX = "rikka-res/assets-index.txt"
    }
}
