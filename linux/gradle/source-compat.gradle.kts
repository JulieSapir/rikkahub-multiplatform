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
 * 处理方式：由同一份登记派生源文件排除清单与生成输入，改写到 build/generated/compat 参与编译。
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
            val candidates = sources.filter {
                it.absolutePath.replace('\\', '/').endsWith("/$packagePath/$fileName")
            }
            check(candidates.size == 1) {
                "compat 源文件必须唯一: $packagePath/$fileName，找到 ${candidates.size} 个"
            }
            val source = candidates.single()

            var text = source.readText(Charsets.UTF_8)
            replacements.forEach { (find, replace) ->
                if (!text.contains(find)) {
                    throw IllegalStateException("compat 规则失配: $fileName 里找不到 \"$find\"，上游改了这个文件")
                }
                text = text.replace(find, replace)
            }
            val target = File(File(out, packagePath), fileName.removeSuffix(".kt") + "Compat.kt")
            target.parentFile.mkdirs()
            // 上游源码都是 UTF-8，写出时显式指定，JDK17 的 C locale 默认字符集不是它
            target.writeText(
                buildString {
                    appendLine("// 由 linux/gradle/source-compat.gradle.kts 从 ${packagePath}/${fileName} 生成，请勿手改")
                    notes.forEach { appendLine("// $it") }
                    appendLine()
                    append(text)
                },
                Charsets.UTF_8,
            )
            logger.lifecycle("source-compat: $fileName -> ${target.name}")
        }
    }

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

val aboutPageRules: List<String> = listOf(
    "me/rerere/rikkahub/ui/pages/setting|SettingAboutPage.kt",
).map { spec ->
    "$spec|" +
        "关于页显示桌面图标、Linux 发行版、架构、内核与 Java 运行时版本|" +
        "SmartPhone01\u0001Computer" +
        "\u0002/ Android \${android.os.Build.VERSION.RELEASE} / SDK \${android.os.Build.VERSION.SDK_INT}" +
        "\u0001/ Linux \${android.os.Build.VERSION.RELEASE} / Java \${System.getProperty(\"java.runtime.version\")}"
}

// Retain upstream migration bodies; adapt only Room's connection callback type.
val migrationRules = listOf("6_7", "11_12", "13_14", "14_15", "15_16", "27_28", "30_31").map { version ->
    "me/rerere/rikkahub/data/db/migrations|Migration_$version.kt|Room JVM migration callback|" +
        "override fun migrate(db: SupportSQLiteDatabase) {\u0001" +
        "override fun migrate(connection: androidx.sqlite.SQLiteConnection) {\n" +
        "        val db = SupportSQLiteDatabase(connection)"
} + listOf(
    "me/rerere/rikkahub/data/db/migrations|Migration_28_29.kt|Room JVM auto-migration callback|" +
        "override fun onPostMigrate(db: SupportSQLiteDatabase) {\u0001" +
        "override fun onPostMigrate(connection: androidx.sqlite.SQLiteConnection) {\n" +
        "        val db = SupportSQLiteDatabase(connection)",
)

val databaseFactoryRules = listOf(
    "me/rerere/rikkahub/data/db|AppDatabaseFactory.kt|Upstream migrations and callbacks on the desktop driver|" +
        "import androidx.room.Room\n\u0001import androidx.room.Room\nimport androidx.room.databaseBuilder\n" +
        "\u0002internal object AppDatabaseFactory {\u0001internal object AppDatabaseFactory {\n" +
        "    fun createForRestore(path: String): AppDatabase = builder(path)\n\n" +
        "\u0002fun create(context: Context, name: String = SQLiteConfiguration.DATABASE_NAME): AppDatabase =\n" +
        "        Room.databaseBuilder(context, AppDatabase::class.java, name)\u0001" +
        "fun create(context: Context, name: String = SQLiteConfiguration.DATABASE_NAME): AppDatabase {\n" +
        "        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }\n" +
        "        return builder(file.absolutePath)\n    }\n\n" +
        "    private fun builder(path: String): AppDatabase =\n" +
        "        Room.databaseBuilder(AppDatabase::class.java, path)" +
        "\u0002override fun onOpen(db: SupportSQLiteDatabase) {\u0001" +
        "override fun onOpen(connection: androidx.sqlite.SQLiteConnection) {\n" +
        "                    val db = SupportSQLiteDatabase(connection)\n" +
        "                    migrateDesktopFts(db)" +
        "\u0002                    // 旧版本搜索用 jieba 分词，会把词典解压到这里（约 14MB），现在不再需要\n" +
        "                    File(context.filesDir, \"simple_dict\").takeIf { it.exists() }?.deleteRecursively()\n\u0001" +
        "\u0002.openHelperFactory(SQLiteConfiguration.openHelperFactory(context))\u0001.setDriver(DesktopSQLiteDriver.create())",
)

val ftsRules = listOf(
    "me/rerere/rikkahub/data/db/fts|MessageFtsManager.kt|Run upstream FTS on Room JVM connections|" +
        "    private val db get() = database.openHelper.writableDatabase\u0001" +
        "\u0002database.withTransaction {\u0001database.withFtsWriter { db ->" +
        "\u0002= withContext(Dispatchers.IO) {\u0001= database.withFtsReader { db ->" +
        "\u0002return@withContext\u0001return@withFtsReader" +
        "\u0002buildFtsMatchQuery(keyword, ::simpleQuery)\u0001buildFtsMatchQuery(keyword) { simpleQuery(db, it) }" +
        "\u0002private fun simpleQuery(text: String)\u0001private suspend fun simpleQuery(db: DesktopFtsDatabase, text: String)" +
        "\u0002internal fun buildFtsMatchQuery(keyword: String, asciiQuery: (String) -> String)\u0001" +
        "internal suspend fun buildFtsMatchQuery(keyword: String, asciiQuery: suspend (String) -> String)" +
        "\u0002QUERY_RUN.findAll(keyword).mapNotNull\u0001QUERY_RUN.findAll(keyword).toList().mapNotNull" +
        "\u0002private fun UIMessage.extractFtsText()\u0001internal fun UIMessage.extractFtsText()",
)

val backupRules = listOf(
    "me/rerere/rikkahub/data/sync|BackupManager.kt|Upstream archive format and restore lifecycle on Room JVM|" +
        "DatabaseBackup.createSnapshot(database.openHelper.writableDatabase, snapshot)\u0001" +
        "DatabaseBackup.createSnapshot(database, snapshot)" +
        "\u0002DatabaseBackup.normalize(context, stagedDatabase)\u0001DatabaseBackup.normalize(stagedDatabase)" +
        "\u0002AppDatabaseFactory.create(context, stagedDatabase.absolutePath)\u0001" +
        "AppDatabaseFactory.createForRestore(stagedDatabase.absolutePath)" +
        "\u0002DatabaseBackup.checkpoint(room.openHelper.writableDatabase)\u0001DatabaseBackup.checkpoint(room)",
)

val compatRules: List<String> = migrationRules + databaseFactoryRules + ftsRules + backupRules + contextBuilderRules + assetIconRules + fontLoaderRules + clipboardRules + routeActivityRules + receiveContentRules + emojiBurstRules + aboutPageRules + listOf(
    "me/rerere/rikkahub/ui/components/ui|ErrorCard.kt|" +
        "Android 的 ClipEntry(clipData = ...) 具名参数在 JB 端是 nativeClipEntry，位置传参即可|" +
        "clipData =\u0001",
    "me/rerere/rikkahub/ui/components/ai|ChatInput.kt|" +
        "JB 桌面端的 DialogProperties 没有 decorFitsSystemWindows（Android 的窗口 inset 概念）|" +
        ", decorFitsSystemWindows = false\u0001",
)

// 同一文件可登记多个改写点；原文件只排除一次，输入也只登记一次。
val compatSourcePaths = compatRules.map { spec ->
    val parts = spec.split("|", limit = 4)
    require(parts.size == 4) { "Invalid compat rule: $spec" }
    "${parts[0]}/${parts[1]}"
}.distinct()
extra["compatSourcePaths"] = compatSourcePaths

val compatSourceFiles = compatSourcePaths.map { path ->
    val candidates = listOf("app", "ui").map { project.file("../$it/src/main/java/$path") }.filter { it.isFile }
    require(candidates.size == 1) { "compat 源文件必须唯一: $path，找到 ${candidates.size} 个" }
    candidates.single()
}

tasks.register("rewriteCompatSources", RewriteCompatSourcesTask::class.java) {
    rules.set(compatRules)
    sourceFiles.from(compatSourceFiles)
    outputDir.set(project.layout.buildDirectory.dir("generated/compat"))
}
