import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 把各模块的 Android 资源（values 与 values-<locale> 下的 strings.xml，以及 font/drawable/raw/mipmap）
 * 桥接到 JVM：
 *
 * - 每个模块生成自己的 R，但共用一张全局 id 表（id 跨模块、跨 group 递增），这样 stringResource(id) /
 *   painterResource(id) 只凭一个 Int 就能定位到“哪个模块的哪个 key/文件”——Android 的 id 本来就是全局唯一的，
 *   分组各自从 1 编号会让 R.drawable.x 和 R.mipmap.x 撞上同一个数字
 * - 每种语言一张合并的 JSON 字符串表，key 带模块前缀
 * - 二进制资源文件拷进 resources，运行时按 id 反查路径
 *
 * 上游新增的 string key 和图标会自动编译。
 *
 * 生成逻辑都放在任务类里：脚本级函数会被闭包捕获，配置缓存不允许序列化脚本对象。
 */
/** 一个模块的 R 表内容，等所有 group 的 id 基准算出来才渲染 */
private class ModuleResTable(
    val packageName: String,
    val prefix: String,
    val stringOffset: Int,
    val strings: List<String>,
    val groups: Map<String, Pair<Int, List<String>>>,
)

abstract class GenerateRikkaResTask : DefaultTask() {

    /** 每项形如 "res 目录绝对路径|R 的包名|表前缀"；顺序决定 id，新增模块追加到末尾 */
    @get:Internal
    abstract val modules: ListProperty<String>

    /** 真正的输入跟踪用这个，避免把整个仓库根目录声明成输入 */
    @get:InputFiles
    abstract val resDirs: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private val binaryGroups = listOf("font", "drawable", "raw", "mipmap")

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        // 目录不镜像包名：避免被 build.gradle.kts 里针对 app 源码根的 exclude 模式命中
        val kotlinOut = File(out, "kotlin/res").apply { mkdirs() }
        val resOut = File(out, "resources/rikka-res").apply { mkdirs() }

        val assetsOut = linkedMapOf<File, String>()
        val assetIndex = mutableListOf<String>()
        val stringKeys = mutableListOf<String>()
        val groupNames = linkedMapOf<String, MutableList<String>>()
        val groupFiles = linkedMapOf<String, MutableList<String>>()
        val tables = linkedMapOf<String, MutableMap<String, String>>()
        // R 要等所有 group 的基准 id 定下来才能渲染，所以先记下每个模块的表
        val moduleTables = mutableListOf<ModuleResTable>()

        modules.get().forEach { spec ->
            val parts = spec.split("|")
            if (parts.size != 3) return@forEach
            val (resDirPath, packageName, prefix) = parts
            val resRoot = File(resDirPath)
            if (!resRoot.isDirectory) return@forEach

            val strings = parseStringsOrEmpty(File(resRoot, "values/strings.xml"))
            val moduleStrings = strings.keys.sorted()
            val stringOffset = stringKeys.size
            moduleStrings.forEach { stringKeys += "$prefix/$it" }
            mergeInto(tables, "default", prefix, strings)

            resRoot.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }
                .orEmpty().sortedBy { it.name }.forEach { dir ->
                    mergeInto(
                        tables,
                        localeTagOf(dir.name),
                        prefix,
                        parseStringsOrEmpty(File(dir, "strings.xml")),
                    )
                }

            copyAssets(File(resRoot.absoluteFile.parentFile, "assets"), assetsOut, assetIndex)

            val moduleGroups = linkedMapOf<String, Pair<Int, List<String>>>()
            binaryGroups.forEach { group ->
                val collected = collectGroupFiles(resRoot, group, File(resOut, "$group/$prefix"))
                val names = collected.keys.sorted()
                val list = groupNames.getOrPut(group) { mutableListOf() }
                val files = groupFiles.getOrPut(group) { mutableListOf() }
                moduleGroups[group] = list.size to names
                names.forEach { name ->
                    list += name
                    files += collected[name]?.let { "$prefix/$it" } ?: ""
                }
            }

            moduleTables += ModuleResTable(packageName, prefix, stringOffset, moduleStrings, moduleGroups)
        }

        // 每个 group 占一段连续的 id，group 内的下标 + 基准 = 全局 id
        val groupBases = LinkedHashMap<String, Int>()
        val binaryFiles = mutableListOf("")
        binaryGroups.forEach { group ->
            val files = groupFiles[group].orEmpty()
            groupBases[group] = binaryFiles.size - 1
            files.forEach { binaryFiles += if (it.isEmpty()) "" else "$group/$it" }
        }

        val resourcesRoot = File(out, "resources")
        File(resourcesRoot, "assets").deleteRecursively()
        assetsOut.forEach { (from, relative) ->
            val target = File(File(resourcesRoot, "assets"), relative)
            target.parentFile?.mkdirs()
            from.copyTo(target, overwrite = true)
        }
        // AssetManager.list 需要目录列表，jar 里没有列目录的 API，所以额外写一份索引
        File(resOut, "assets-index.txt").writeText(assetIndex.sorted().joinToString("\n"), Charsets.UTF_8)

        tables.forEach { (tag, table) -> writeJson(File(resOut, "strings/$tag.json"), table) }
        moduleTables.forEach { table ->
            File(kotlinOut, "R-${table.prefix}.kt").writeText(renderR(table, groupBases), Charsets.UTF_8)
        }
        File(kotlinOut, "RikkaResLookup.kt").writeText(renderLookup(stringKeys, binaryFiles), Charsets.UTF_8)
        logger.lifecycle("rikka-res: ${stringKeys.size} 条字符串, ${tables.size} 种语言")
    }

    private fun mergeInto(
        tables: MutableMap<String, MutableMap<String, String>>,
        tag: String,
        prefix: String,
        source: Map<String, String>,
    ) {
        val table = tables.getOrPut(tag) { mutableMapOf() }
        source.forEach { (key, value) -> table["$prefix/$key"] = value }
    }

    /**
     * 收集 group 与 group-<qualifier> 目录里的文件，同名时取质量最高的一份：
     * 桌面端不按屏幕 dpi 选小图，一律用最大密度；night/dark 变体排最后（默认要浅色）。
     *
     * Android 8+ 的 `<name>.xml`（adaptive-icon）是背景层 + 前景层合成的，上游
     * `AsyncImage(model = R.mipmap.ic_launcher)` 在手机上拿到的就是合成结果；这里在构建期按同样的
     * 方式合成一张 PNG 顶位，否则只能退回带圆角底和投影的旧版 ic_launcher.png，和 Android 端对不上。
     */
    private fun collectGroupFiles(resRoot: File, group: String, target: File): Map<String, String> {
        val dirs = resRoot.listFiles { f ->
            f.isDirectory && (f.name == group || f.name.startsWith("$group-"))
        }.orEmpty().toList()

        val found = linkedMapOf<String, String>()
        dirs.filter { it.name.contains("anydpi") }.sortedBy { it.name }.forEach { dir ->
            dir.listFiles().orEmpty().filter { it.extension == "xml" }.sortedBy { it.name }.forEach { xml ->
                val name = xml.nameWithoutExtension
                val layers = adaptiveLayers(xml) ?: return@forEach
                val out = File(target, "$name.png")
                if (composeLayers(dirs, layers, out)) found[name] = out.name
            }
        }

        dirs.sortedWith(compareByDescending<File> { densityRank(it.name) }.thenBy { it.name }).forEach { dir ->
            dir.listFiles().orEmpty().sortedBy { it.name }.forEach { src ->
                if (!src.isFile || !src.name.contains('.')) return@forEach
                val name = src.name.substringBeforeLast('.')
                if (name in found) return@forEach
                target.mkdirs()
                src.copyTo(File(target, src.name), overwrite = true)
                found[name] = src.name
            }
        }
        return found
    }

    /** adaptive-icon 的背景层与前景层引用（@mipmap/xxx），解析不出来就不合成 */
    private fun adaptiveLayers(xml: File): List<String>? {
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        val root = runCatching { factory.newDocumentBuilder().parse(xml).documentElement }.getOrNull()
            ?: return null
        if (root.tagName != "adaptive-icon") return null
        return listOf("background", "foreground").mapNotNull { tag ->
            val element = root.getElementsByTagName(tag).item(0) as? Element ?: return@mapNotNull null
            element.getAttribute("android:drawable")
                .removePrefix("@mipmap/").removePrefix("@drawable/")
                .takeIf { it.isNotBlank() && !it.startsWith("@") }
        }.takeIf { it.size == 2 }
    }

    /** 两层各自按最高密度取图，等比铺满同一张画布后叠起来 */
    private fun composeLayers(dirs: List<File>, layerNames: List<String>, out: File): Boolean {
        val layers = layerNames.mapNotNull { name ->
            bestDensityFile(dirs, name)?.let { runCatching { ImageIO.read(it) }.getOrNull() }
        }
        if (layers.size != layerNames.size) return false
        val size = layers.maxOf { maxOf(it.width, it.height) }
        val canvas = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val graphics = canvas.createGraphics()
        graphics.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR
        )
        layers.forEach { layer -> graphics.drawImage(layer, 0, 0, size, size, null) }
        graphics.dispose()
        out.parentFile.mkdirs()
        return runCatching { ImageIO.write(canvas, "png", out) }.getOrDefault(false)
    }

    private fun bestDensityFile(dirs: List<File>, name: String): File? = dirs
        .map { File(it, "$name.png") }
        .filter { it.isFile }
        .maxByOrNull { densityRank(it.parentFile.name) }

    /** 目录名里的密度限定符排序，长的先判（xxxhdpi 里也含 "hdpi"） */
    private fun densityRank(dirName: String): Int = when {
        dirName.contains("night") || dirName.contains("dark") -> -1
        dirName.contains("anydpi") -> 100
        dirName.contains("nodpi") -> 90
        dirName.contains("xxxhdpi") -> 40
        dirName.contains("xxhdpi") -> 30
        dirName.contains("xhdpi") -> 20
        dirName.contains("hdpi") -> 15
        dirName.contains("mdpi") -> 10
        dirName.contains("ldpi") -> 5
        else -> 0
    }

    /** Android 的 assets 目录原样落到 classpath 的 /assets，供 AssetManager 读取 */
    private fun copyAssets(assetsRoot: File, sink: MutableMap<File, String>, index: MutableList<String>) {
        if (!assetsRoot.isDirectory) return
        assetsRoot.walkTopDown().filter { it.isFile }.forEach { file ->
            val relative = file.relativeTo(assetsRoot).invariantSeparatorsPath
            if (relative.startsWith(".") || file.name.startsWith(".")) return@forEach
            sink[file] = relative
            index += relative
        }
    }

    /** values-zh-rTW -> zh-TW，values-ko-rKR -> ko-KR */
    private fun localeTagOf(dirName: String): String {
        val qualifier = dirName.removePrefix("values-")
        val parts = qualifier.split("-")
        return if (parts.size >= 2) "${parts[0]}-${parts[1].removePrefix("r").uppercase()}" else qualifier
    }

    private fun parseStringsOrEmpty(file: File): Map<String, String> =
        if (file.exists()) parseStrings(file) else emptyMap()

    private fun parseStrings(file: File): Map<String, String> {
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.isXIncludeAware = false
        factory.setValidating(false)
        val nodes = factory.newDocumentBuilder().parse(file).getElementsByTagName("string")
        val out = linkedMapOf<String, String>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            val name = el.getAttribute("name")
            if (name.isNotEmpty()) out[name] = unescapeAndroid(el.textContent)
        }
        return out
    }

    /** Android 字符串里的 \n \' \" \uXXXX 转义与外层引号 */
    private fun unescapeAndroid(raw: String): String {
        val text = raw.trim().removeSurrounding("\"")
        return buildString {
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c != '\\' || i + 1 >= text.length) {
                    append(c)
                    i++
                    continue
                }
                when (val n = text[++i]) {
                    'n' -> append('\n')
                    't' -> append('\t')
                    'r' -> append('\r')
                    'u' -> {
                        append(text.substring(i + 1, minOf(i + 5, text.length)).toInt(16).toChar())
                        i += 4
                    }

                    else -> append(n)
                }
                i++
            }
        }
    }

    private fun writeJson(file: File, map: Map<String, String>) {
        file.parentFile.mkdirs()
        file.writeText(buildString {
            append('{')
            map.entries.forEachIndexed { index, (k, v) ->
                if (index > 0) append(',')
                append(jsonString(k)).append(':').append(jsonString(v))
            }
            append('}')
        }, Charsets.UTF_8)
    }

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }

    private fun renderR(table: ModuleResTable, groupBases: Map<String, Int>): String = buildString {
        appendLine("// Generated by linux/gradle/android-res.gradle.kts")
        appendLine("@file:Suppress(\"ClassName\")")
        appendLine("package ${table.packageName}")
        appendLine()
        appendLine("object R {")
        appendLine("    object string {")
        table.strings.forEachIndexed { index, name ->
            appendLine("        const val ${identifier(name)}: Int = ${table.stringOffset + index + 1}")
        }
        appendLine("    }")
        table.groups.forEach { (group, offsetAndNames) ->
            val (offset, names) = offsetAndNames
            val base = groupBases[group] ?: 0
            appendLine("    object $group {")
            names.forEachIndexed { index, name ->
                appendLine("        const val ${identifier(name)}: Int = ${base + offset + index + 1}")
            }
            appendLine("    }")
        }
        appendLine("}")
    }

    /** 全局查表：下标就是 R 里的 id，0 是空串表示“查不到” */
    private fun renderLookup(stringKeys: List<String>, binaryFiles: List<String>): String = buildString {
        appendLine("// Generated by linux/gradle/android-res.gradle.kts")
        appendLine("package me.rerere.rikkahub")
        appendLine()
        appendLine("object RikkaResLookup {")
        append("    val stringNames: List<String> = listOf(\"\"")
        stringKeys.forEach { append(", \"${escape(it)}\"") }
        appendLine(")")
        append("    val binaryFiles: List<String> = listOf(\"\"")
        binaryFiles.drop(1).forEach { append(", \"${escape(it)}\"") }
        appendLine(")")
        appendLine("}")
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    private fun identifier(name: String): String = name.replace('.', '_').replace('-', '_')
}

val generateRikkaRes = tasks.register("generateRikkaRes", GenerateRikkaResTask::class.java) {
    val resModules = listOf(
        "app/src/main/res" to "me.rerere.rikkahub|app",
        "search/src/main/res" to "me.rerere.search|search",
        "oauth/src/main/res" to "me.rerere.oauth|oauth",
        "ui/src/main/res" to "me.rerere.ui|ui",
    )
    modules.set(resModules.map { (dir, rest) -> "${rootProject.file(dir)}|$rest" })
    resDirs.from(resModules.map { (dir, _) -> rootProject.file(dir) })
    outputDir.set(layout.buildDirectory.dir("generated/rikkaRes"))
}
