import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * 少数上游文件用到了 Android 版 Compose 特有的 API 形状（具名参数 `clipData =`、
 * `decorFitsSystemWindows =` 这类 JB 端没有的成员），整个换掉会有冲突
 *
 * 处理方式：重写一份到 build/generated/compat 下参与编译，原文件在 linux/build.gradle.kts
 * 里 exclude
 *
 * 规则用竖线分隔的字符串编码，避免嵌套 data class 在配置缓存里无法序列化：
 * `包路径|文件名|说明|查找\u0001替换(\u0002查找\u0001替换)*`
 * 注意：竖线是字段分隔符，替换文本里不能出现 `||`（会被切掉），写等价的其它表达式即可。
 */
@CacheableTask
abstract class RewriteCompatSourcesTask : DefaultTask() {

    @get:Input
    abstract val rules: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun rewrite() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        val sources = sourceFiles.files.toList()
        // 同一个文件可能有多条规则（不同改动点），按文件合并后一次改写完
        data class Plan(val notes: MutableList<String> = mutableListOf(), val replacements: MutableList<Pair<String, String>> = mutableListOf())
        val plans = LinkedHashMap<Pair<String, String>, Plan>()
        rules.get().forEach { spec ->
            val parts = spec.split("|")
            val packagePath = parts[0]
            val fileName = parts[1]
            val note = parts[2]
            val plan = plans.getOrPut(packagePath to fileName) { Plan() }
            plan.notes += note
            parts[3].split("\u0002").forEach { pair ->
                plan.replacements += pair.substringBefore("\u0001") to pair.substringAfter("\u0001")
            }
        }
        plans.forEach { (target, plan) ->
            val (packagePath, fileName) = target
            val notes = plan.notes
            val replacements = plan.replacements
            val source = sources.firstOrNull {
                it.name == fileName && it.absolutePath.replace('\\', '/').contains(packagePath)
            } ?: throw IllegalStateException("compat 规则没找到源文件: $packagePath/$fileName")

            var text = source.readText()
            replacements.forEach { (find, replace) ->
                if (!text.contains(find)) {
                    throw IllegalStateException("compat 规则失配: $fileName 里找不到 \"$find\"，上游改了这个文件")
                }
                text = text.replace(find, replace)
            }
            val target = File(File(out, packagePath), fileName.removeSuffix(".kt") + "Compat.kt")
            target.parentFile.mkdirs()
            target.writeText(
                buildString {
                    appendLine("// 由 linux/gradle/source-compat.gradle.kts 从 ${relativeNameOf(source)} 生成，请勿手改")
                    notes.forEach { appendLine("// $it") }
                    appendLine()
                    append(text)
                },
            )
            logger.lifecycle("source-compat: $fileName -> ${target.name}")
        }
    }

    private fun relativeNameOf(file: java.io.File): String =
        runCatching { project.rootDir.toPath().relativize(file.toPath()).toString() }.getOrDefault(file.name)
}

/**
 * 每条规则：包路径|文件名|说明|查找\u0001替换（多条用 \u0002 分隔），包路径相对 app/src/main/java。
 *
 * 第一组只是把 Android 版 Coil 的 `ImageRequest.Builder(context)` 换成 JB 端的全局 PlatformContext。
 * 逐个文件列出来、不做全局扫描，是为了上游新增调用点时直接编译报错，而不是被悄悄改写。
 */
val contextBuilderRules: List<String> = listOf(
    "me/rerere/rikkahub/ui/components/ui|AIIcon.kt",
    "me/rerere/rikkahub/ui/pages/chat|ConversationExport.kt",
    "me/rerere/rikkahub/ui/pages/extensions/workspace|WorkspaceDetailPage.kt",
).map { spec ->
    "$spec|" +
        "Android 版 Coil 用调用方的 Context 构造 ImageRequest，JB 端只有一个全局 PlatformContext|" +
        "ImageRequest.Builder(context)\u0001ImageRequest.Builder(coil3.PlatformContext.INSTANCE)"
}

val assetIconRules: List<String> = listOf(
    "me/rerere/rikkahub/ui/components/ui|AIIcon.kt",
).map { spec ->
    "$spec|" +
        "提供商图标的 asset:// 路径在桌面端要先释放到数据目录，Coil 才认这个 file://|" +
        "\"file:///android_asset/icons/\$path\"\u0001me.rerere.rikkahub.linux.res.DesktopAssets.uriOf(\"icons/\$path\")"
}

val fontLoaderRules: List<String> = listOf(
    "me/rerere/rikkahub/ui/theme|ChatFont.kt",
).map { spec ->
    "$spec|" +
        "JB 端没有 Font(File)，改成先注册进 AWT 字体库再按家族名取 FontFamily|" +
        "FontFamily(Font(file))\u0001me.rerere.rikkahub.linux.font.desktopFontFamily(file)"
}

val clipboardRules: List<String> = listOf(
    "me/rerere/rikkahub/ui/pages/translator|TranslatorPage.kt",
).map { spec ->
    "$spec|" +
        "Android 的 Clipboard.getClipEntry().clipData 在 JB 端没有 clipData 成员，改读系统剪贴板|" +
        "clipboard.getClipEntry()?.clipData?.getText()\u0001me.rerere.rikkahub.linux.clip.DesktopClipboard.readText()"
}

val routeActivityRules: List<String> = listOf(
    "me/rerere/rikkahub|RouteActivity.kt",
).map { spec ->
    "$spec|" +
        "navigation3 的 desktop 变体只有带 SavedStateConfiguration 的重载，桌面端不需要恢复导航栈；" +
        "JVM 版 Coil 没有 asset:// 与资源 id 的内置映射，往那条 components 链里补两个 Mapper；" +
        "Coil 的内存缓存默认按 maxMemory 的 25% 取，桌面端堆被封顶到 512m，这里显式限死|" +
        "rememberNavBackStack(startScreen)\u0001me.rerere.rikkahub.linux.nav.desktopRememberNavBackStack(startScreen)" +
        "\u0002add(SvgDecoder.Factory(scaleToDensity = true))\u0001add(SvgDecoder.Factory(scaleToDensity = true))\n" +
        "                            add(me.rerere.rikkahub.linux.image.DesktopAssetUriMapper())\n" +
        "                            add(me.rerere.rikkahub.linux.image.DesktopResourceIdMapper())" +
        "\u0002.crossfade(true)\u0001.crossfade(true)\n" +
        "                        .memoryCache { coil3.memory.MemoryCache.Builder().maxSizeBytes(64L * 1024 * 1024).build() }"
}

val receiveContentRules: List<String> = listOf(
    "me/rerere/rikkahub/ui/components/ai|ChatInput.kt",
).map { spec ->
    "$spec|" +
        "JB 桌面端的 Modifier.contentReceiver 尚未实现，会直接抛异常，所以拖放先不挂|" +
        "import androidx.compose.foundation.content.contentReceiver\u0001import androidx.compose.foundation.content.desktopContentReceiver" +
        "\u0002.contentReceiver(receiveContentListener)\u0001.desktopContentReceiver(receiveContentListener)"
}

/**
 * 关于页的 emoji 彩蛋：上游的粒子循环是 `while(true) { withFrameNanos… }`，没有粒子时也每帧订阅帧
 * 时钟，等于让静止页面永远按 vsync 重绘整窗。
 *
 * 只在「没有待生成也没有粒子」时改成短延时轮询，有粒子照旧逐帧跑 —— 协程结构与 LaunchedEffect 的
 * key 都不动，所以不会出现取消/重启导致的掉帧。
 */
val emojiBurstRules: List<String> = listOf(
    "me/rerere/ui/easteregg|EmojiBurst.kt",
).map { spec ->
    "$spec|" +
        "空闲时不订阅帧时钟，改 16ms 轮询；有粒子时仍是逐帧物理模拟|" +
        "while (true) {\u0001while (true) {\n" +
        "                if (pendingBursts.isEmpty() && particles.isEmpty()) {\n" +
        "                    kotlinx.coroutines.delay(16)\n" +
        "                    continue\n" +
        "                }\n"
}

/**
 * 备份恢复后的「重启应用」：Android 上 exitProcess(0) 之后有系统/用户重新拉起，
 * 桌面端进程一退就什么都没了，所以换成自己按 /proc/self/cmdline 另起一个进程。
 */
val restartAppRules: List<String> = listOf(
    "me/rerere/rikkahub/ui/pages/backup/components|BackupDialog.kt",
).map { spec ->
    "$spec|" +
        "桌面端没有系统帮忙重新拉起进程，exitProcess 等于直接把应用关掉|" +
        "exitProcess(0)\u0001me.rerere.rikkahub.linux.desktop.DesktopProcess.restart()"
}

val compatRules: List<String> = contextBuilderRules + assetIconRules + fontLoaderRules + clipboardRules + routeActivityRules + receiveContentRules + emojiBurstRules + restartAppRules + listOf(
    "me/rerere/rikkahub/ui/components/ui|ErrorCard.kt|" +
        "Android 的 ClipEntry(clipData = ...) 具名参数在 JB 端是 nativeClipEntry，位置传参即可|" +
        "clipData =\u0001",
    "me/rerere/rikkahub/ui/components/ai|ChatInput.kt|" +
        "JB 桌面端的 DialogProperties 没有 decorFitsSystemWindows（Android 的窗口 inset 概念）|" +
        ", decorFitsSystemWindows = false\u0001",
)

tasks.register("rewriteCompatSources", RewriteCompatSourcesTask::class.java) {
    rules.set(compatRules)
    sourceFiles.from(
        compatRules.map { spec ->
            val parts = spec.split("|")
            // 上游 UI 组件不只在 app 模块里（easter egg 在 ui 模块），按源码根找第一个存在的
            listOf("app", "ui").map { project.rootProject.file("$it/src/main/java/${parts[0]}/${parts[1]}") }
                .firstOrNull { it.isFile }
                ?: project.rootProject.file("app/src/main/java/${parts[0]}/${parts[1]}")
        },
    )
    outputDir.set(project.layout.buildDirectory.dir("generated/compat"))
}
