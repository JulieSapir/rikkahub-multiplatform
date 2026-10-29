import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Linux 桌面端。
 *
 * 这里不修改 app / ai / common / highlight 里的任何文件，而是把其中平台无关的 Kotlin 源码直接
 * 纳入本模块的编译单元（AGP 库无法被纯 JVM 模块依赖，所以只能重新编译一次源码）。上游文件因此
 * 保持逐字节不变，rebase 时不会产生冲突；只有下面 exclude 掉的、真正依赖 Android 框架的文件
 * 才在 src/platform/kotlin 里提供 JVM 版本。
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

// 从 app/src/main/res 生成 R 与字符串表，上游加的资源会自动出现
apply(from = "gradle/android-res.gradle.kts")
// 少数上游文件用了 Android 版 Compose 特有的 API 形状，重写一份参与编译（原文件在下面 exclude）
apply(from = "gradle/source-compat.gradle.kts")

kotlin {
    sourceSets.named("main") {
        kotlin.apply {
            // android-res.gradle.kts 生成的 R 与查表代码
            srcDir(layout.buildDirectory.dir("generated/rikkaRes/kotlin"))
            // gradle/source-compat.gradle.kts 重写出来的那几个文件
            srcDir(layout.buildDirectory.dir("generated/compat"))
            srcDir("src/platform/kotlin")

            // 平台无关的库模块：直接编译它们的源码（AGP 库不能被纯 JVM 模块依赖）。
            // srcDir 一律用模块根的 java 目录，这样下面的 exclude 模式带包路径，不会因为
            // 根目录不同而失效。
            srcDir("../common/src/main/java")
            srcDir("../ai/src/main/java")
            srcDir("../highlight/src/main/java")
            srcDir("../material3/src/main/java")
            srcDir("../material3/material-color-utilities/kotlin")
            srcDir("../mediagen/src/main/java")
            srcDir("../search/src/main/java")
            srcDir("../speech/src/main/java")
            srcDir("../ui/src/main/java")
            srcDir("../workspace/src/main/java")
            // 文档解析（docx/pptx/epub 是纯 xmlpull+zip，pdf 用仓库里 vendor 的 mupdf Java 层）
            srcDir("../document/src/main/java")
            // MCP 的 OAuth 客户端与回调服务，只有 Custom Tabs 那一层要换掉
            srcDir("../oauth/src/main/java")
            // 内嵌 Web 服务（Ktor CIO 是纯 JVM 引擎），前端静态页需要另跑 web-ui 构建
            srcDir("../web/src/main/java")
            srcDir("../app/src/main/java")

            // ---- 排除：依赖 Android 框架或本端不启用的功能 ----
            // 这几个只是 API 形状不同（具名参数、Coil 的 Context 参数），
            // 由 rewriteCompatSources 生成桌面可用版本，规则见 gradle/source-compat.gradle.kts
            exclude("me/rerere/rikkahub/ui/components/ui/ErrorCard.kt")
            exclude("me/rerere/rikkahub/ui/components/ai/ChatInput.kt")
            exclude("me/rerere/rikkahub/ui/components/ui/AIIcon.kt")
            exclude("me/rerere/rikkahub/ui/pages/chat/ConversationExport.kt")
            exclude("me/rerere/rikkahub/ui/pages/extensions/workspace/WorkspaceDetailPage.kt")
            exclude("me/rerere/rikkahub/ui/theme/ChatFont.kt")
            exclude("me/rerere/rikkahub/ui/pages/translator/TranslatorPage.kt")
            exclude("me/rerere/rikkahub/RouteActivity.kt")
            // 终端：Android 那份用 proot + rootfs + Termux 的 View，桌面端由 *Jvm.kt 起本地 shell
            exclude("**/ui/pages/extensions/workspace/WorkspaceTerminalSession.kt")
            exclude("**/ui/pages/extensions/workspace/WorkspaceTerminalPage.kt")
            // 由 src/platform/kotlin 下的 *Jvm.kt 替身实现
            exclude("**/ai/util/FileEncoder.kt")
            exclude("**/common/android/ContextUtil.kt")
            exclude("**/utils/ContextUtil.kt")
            // 只给 Android Studio 预览用
            exclude("**/highlight/HighlighterPreview.kt")
            // 云端 ASR 的 5 个 provider 与 hooks/ASR.kt 平台无关（okhttp websocket），原样编译：
            // 唯一的 Android 依赖 AudioRecord 由 android/media/AudioRecordJvm.kt 的 TargetDataLine 顶替
            // 系统 TTS 引擎是 Android 的 TextToSpeech，桌面端由 SystemTTSProviderJvm.kt 顶位；
            // 其余云端 provider 全是 okhttp，直接复用
            exclude("**/tts/provider/providers/SystemTTSProvider.kt")
            exclude("**/tts/controller/**")
            exclude("**/ui/hooks/TTS.kt")
            // 头像形变的无限动画在 loading=false 时也照跑，会让桌面端静止页面一直重绘，
            // 由 AvatarShapeJvm.kt 顶位成「只有生成中才动画」
            exclude("**/ui/hooks/AvatarShape.kt")
            // 搜索实现只用 okhttp + compose + 已 shim 的 Log/Context，整体可用
            // （脚本搜索用 quickjs 的 JVM 产物，见 dependencies 里的 quickjs-jvm）
            // ui 模块里的位图/画板/WebView 是 Android 专属
            exclude("**/ui/sketch/**")
            // WebView 控件是 Android 的，WebViewContentCache 只是往缓存目录写 HTML，跨平台可用
            // WebView 控件与它的本地资源拦截都是 Android 的，桌面端由 webview/WebViewJvm.kt 顶位
            exclude("**/ui/webview/WebView.kt")
            exclude("**/ui/webview/WebViewLocalAssets.kt")
            // 关于页彩蛋的粒子循环原本没有粒子时也每帧 withFrameNanos，会让静止页面一直重绘；
            // 由 source-compat 规则改成跑完就退出
            exclude("**/ui/easteregg/EmojiBurst.kt")
            // Android 系统能力：图片处理、SAF、更新检查、崩溃上报、音效播放
            // 通知走 androidx/core/app/NotificationCompatJvm.kt 的托盘壳，上游 NotificationUtil 原样编译
            exclude("**/utils/CrashHandler.kt")
            exclude("**/utils/SoundEffectPlayer.kt")
            exclude("**/utils/DatabaseUtil.kt")
            // 图表组件用了 ui 模块自己的 Android 资源
            // 整个 app 的界面与数据层还没接完，先只放开表现层
            // ---- Room 数据层（下一轮的入口，已验证过的门槛）----
            // KSP 会重新 glob 源码目录、不认这里的 exclude，所以 data/db 只能整目录开关。
            // 已跑通：room-runtime / room-paging / paging-common 都有 jvm 变体；配上
            //   ksp { arg("room.schemaLocation", "app/schemas") } 与一个
            //   androidx.sqlite.db.SupportSQLiteQuery 替身后，Room 能生成 DAO_Impl。
            // 还差：migrations / fts / AppDatabaseFactory / SQLiteConfiguration 用的是 Android 的
            //   SupportSQLiteDatabase API，data/files 整层是 SAF/MediaStore —— 都要写 JVM 版。
            // 建库/迁移/FTS 用 linux 侧的 JVM 版本（上游那份绑 requery 与 SupportSQLiteDatabase）
            exclude("**/data/db/AppDatabaseFactory.kt")
            exclude("**/data/db/SQLiteConfiguration.kt")
            // 只有用了 Android SupportSQLiteDatabase 的手写迁移要换掉，
            // Migration_8_9 / 16_17 / 22_23 是 AutoMigrationSpec 标记类，跨平台可用
            exclude("**/data/db/migrations/Migration_6_7.kt")
            exclude("**/data/db/migrations/Migration_11_12.kt")
            exclude("**/data/db/migrations/Migration_13_14.kt")
            exclude("**/data/db/migrations/Migration_14_15.kt")
            exclude("**/data/db/migrations/Migration_15_16.kt")
            exclude("**/data/db/migrations/MigrationUtils.kt")
            // fts 用 linux 侧的 JVM 版本（上游那份依赖 jieba 扩展与 openHelper）
            exclude("**/data/db/fts/MessageFtsManager.kt")
            exclude("**/data/db/fts/SimpleDictManager.kt")
            // ImageUtils 用 linux 侧的 JVM 版本（原文件绑 exifinterface/zxing 的 Android 解码）
            exclude("**/utils/ImageUtils.kt")
            exclude("**/ui/pages/webview/**")
            // 这些设置页绑的是 Android 能力：前台服务/通知/相册/剪贴板权限/TTS-ASR 播放
            exclude("**/ui/activity/**")
            // 这两组组件绑的是 ChatService / 仓储 / Koin / navigation3 的 Screen，数据层接好后再放开
            // 公式(jlatexmath-android)、mermaid(WebView)、图片缩放(image-viewer AAR)、HTML 渲染
            // 都用了 Android 专属实现，桌面端由 richtext/RichtextJvm.kt 给出对应实现
            exclude("**/ui/components/richtext/MathBlock.kt")
            exclude("**/ui/components/richtext/LatexText.kt")
            exclude("**/ui/components/richtext/Mermaid.kt")
            exclude("**/ui/components/richtext/SimpleHtmlBlock.kt")
            exclude("**/ui/components/richtext/ZoomableAsyncImage.kt")
            // 这些组件用了 Android 权限/相册/播放器/FloatingX 等能力
            // 图片预览与离屏位图渲染绑 Android 视图树，桌面端由 *Jvm.kt 顶位
            exclude("**/ui/components/ui/ImagePreviewDialog.kt")
            exclude("**/ui/components/BitmapComposer.kt")
            exclude("**/ui/components/ui/VideoPlayerDialog.kt")
            exclude("**/ui/components/ui/FloatingWindow.kt")
            // TTS 播放控制器依赖 Android 悬浮窗，桌面端朗读整体未接
            exclude("**/ui/components/ui/TTSController.kt")
            // QRCode 只用 zxing core 的纯 Java API，配合 androidx.core.graphics 的位图 shim 直接编译
            // Rabbit 加载动画是 AnimatedVectorDrawable + AndroidView，桌面端由 RabbitLoadingJvm.kt 顶位
            exclude("**/ui/components/ui/RabbitLoading.kt")
            exclude("**/ui/components/ui/ShareSheet.kt")
            // data/ai 与 data/sync 里只保留可移植的数据类（提示词、MCP 配置、S3 配置），
            // 生成循环 / 工具调用 / 备份同步都依赖 Android 侧的仓储与前台服务
            // 聊天编排（ChatService / ConversationSession / MessageQueue）是平台无关的，放开；
            // Android DocumentsProvider（工作区文件对外暴露）桌面端不需要，直接排除
            exclude("**/data/provider/WorkspaceDocumentsProvider.kt")
            // 备份/导出与设备信息占位符依赖 Android 的 SQLite 与 FileProvider，用 JVM 版本替代
            exclude("**/data/sync/DatabaseBackup.kt")
            exclude("**/data/sync/BackupManager.kt")
            exclude("**/data/export/ExportHooks.kt")
            // 设备类本地工具（日历、屏幕时间）走 Android ContentProvider，桌面端没有对应能力
            exclude("**/data/ai/tools/local/CalendarTool.kt")
            exclude("**/data/ai/tools/local/ScreenTimeTool.kt")
            // 只有 Android 前台服务与通知壳用 JVM 版本替代
            exclude("**/service/ChatGenerationForegroundService.kt")
            exclude("**/service/MediaCreationForegroundService.kt")
            exclude("**/service/WebServerService.kt")
            // OAuth：Custom Tabs 拉起浏览器与前台服务保活这两壳换成 *Jvm.kt 顶位
            exclude("**/OAuthAuthorizationLauncher.kt")
            exclude("**/OAuthCallbackForegroundService.kt")
            // app 根目录下的 Application/Activity 等入口文件
            exclude("me/rerere/rikkahub/RikkaHubApp.kt")
            exclude("me/rerere/rikkahub/MainActivity.kt")
            // RouteActivity 里带着整棵导航图（AppRoutes），桌面端要它；
            // 只有 RikkaHubApp（Android Application 壳）继续排除
            // 这几个文件是给 DataStore 版 SettingsStore 用的扩展，桌面端换成本地实现
            // 内置提供商预设的说明文案用了 richtext 组件，桌面端用 linux/.../state/AppConfig 的默认值
            // Settings 的默认值用到 DEFAULT_PROVIDERS，所以这个文件要一起编译；
            // 它里面的说明文案用 richtext，先由 MarkdownBlockJvm.kt 占位渲染成纯文本
            // 这几个 hooks 绑的是 Android 的 SettingsStore / SharedPreferences，桌面端由 *Jvm.kt 提供
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.optIn.addAll(
            "kotlin.uuid.ExperimentalUuidApi",
            "kotlin.time.ExperimentalTime",
            "kotlinx.coroutines.ExperimentalCoroutinesApi",
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.animation.ExperimentalAnimationApi",
            "androidx.compose.animation.ExperimentalSharedTransitionApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi",
            "androidx.compose.ui.ExperimentalComposeUiApi",
            "androidx.compose.ui.text.ExperimentalTextApi",
        )
    }
}

tasks.withType<Test>().configureEach {
    // 测试里会构造 AppScope(Dispatchers.Main=Swing EDT)，没有显示器时必须走 headless AWT
    systemProperty("java.awt.headless", "true")
}

sourceSets.named("main") {
    resources.srcDir(layout.buildDirectory.dir("generated/rikkaRes/resources"))
    // 内嵌前端的产物（web-ui 构建后由 copy.ts 落到这里），Ktor 的 staticResources("/", "static") 就查这份
    resources.srcDir("../web/src/main/resources")
}

// Room 的 schema 直接复用 app 模块导出的那一份，AutoMigration 才能读到历史版本
ksp {
    arg("room.schemaLocation", "${rootProject.projectDir}/app/schemas")
    arg("room.generateKotlin", "true")
}

// 生成的 R / 字符串表要先产出（KSP 也会读同一批源码）
tasks.matching { it.name == "processResources" || it.name.startsWith("compileKotlin") || it.name.startsWith("ksp") }
    .configureEach {
        dependsOn("generateRikkaRes", "rewriteCompatSources")
    }

// hugeicons-compose 只发 Android AAR，纯 JVM 模块没法直接依赖，这里把它的 classes.jar 抽出来用。
// 上游图标库升级时只改 catalog 版本即可。
val hugeiconsAar = configurations.create("hugeiconsAar") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

val hugeiconsJar = layout.buildDirectory.file("hugeicons/hugeicons-compose.jar")

// termux 的 terminal-emulator 是 VT100 解析器（纯 Java），但同一个 aar 里的 TerminalSession
// 靠 JNI + Android 的 FileDescriptor 起进程，桌面端要换成 pty4j：这里把 jar 里那几个类剔掉再用。
val termuxAar = configurations.create("termuxAar") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

val termuxEmulatorJar = layout.buildDirectory.file("termux/terminal-emulator.jar")

val extractTermuxEmulator = tasks.register<Copy>("extractTermuxEmulator") {
    group = "build"
    description = "Unpack termux terminal-emulator classes.jar"
    from(termuxAar.map { zipTree(it).matching { include("classes.jar") } })
    into(termuxEmulatorJar.map { requireNotNull(it).asFile.parent })
    eachFile { name = "terminal-emulator.jar" }
    includeEmptyDirs = false
}

val stripTermuxSession = tasks.register<Zip>("stripTermuxSession") {
    group = "build"
    description = "剔除 TerminalSession/JNI，剩下的 TerminalEmulator/TerminalBuffer/KeyHandler 是纯 JVM 的"
    from(zipTree(termuxEmulatorJar)) {
        exclude(
            // TerminalSessionClient 接口要留着，TerminalEmulator 依赖它；只剔掉起进程的那部分
            "com/termux/terminal/TerminalSession.class",
            "com/termux/terminal/TerminalSession$*.class",
            "com/termux/terminal/JNI*.class",
            "com/termux/terminal/BuildConfig.class",
        )
    }
    destinationDirectory.set(layout.buildDirectory.dir("termux"))
    archiveFileName.set("terminal-emulator-jvm.jar")
    dependsOn(extractTermuxEmulator)
}

val extractHugeicons = tasks.register<Copy>("extractHugeicons") {
    group = "build"
    description = "Unpack hugeicons-compose AAR classes.jar for the JVM module"
    from(hugeiconsAar.map { zipTree(it) })
    include("classes.jar")
    into(hugeiconsJar.map { requireNotNull(it).asFile.parent })
    eachFile { name = "hugeicons-compose.jar" }
    includeEmptyDirs = false
}

dependencies {
    hugeiconsAar("com.github.rikkahub:hugeicons-compose:${libs.versions.huge.icons.get()}")
    implementation(files(hugeiconsJar).builtBy(extractHugeicons))

    termuxAar("com.termux.termux-app:terminal-emulator:${libs.versions.termux.get()}")
    implementation(files(layout.buildDirectory.file("termux/terminal-emulator-jvm.jar")).builtBy(stripTermuxSession))
    // JetBrains 的纯 JVM 伪终端库，工作区终端用它起 shell
    implementation("org.jetbrains.pty4j:pty4j:0.13.1")

    // 上游 markdown 渲染用的 AST 库（KMP jar）与 toast 组件
    implementation(libs.jetbrains.markdown)
    implementation(libs.sonner)

    // foundation / ui / animation 由 desktop 运行时带进来，这里只声明直接用的
    implementation(compose.desktop.currentOs)

    // 插件自带的 material3 还没公开 MaterialExpressiveTheme，且少了 Android 端 1.5.0-alpha 里的
    // ToggleButtonDefaults.colors / SliderState(trackRange, isVertical) 等表现型 API。
    // 固定到同一release线里带这些 API 的版本，上游组件才能一行不改地编译。
    implementation("org.jetbrains.compose.material3:material3:1.13.0-alpha02")
    // 插件里的图标 accessor 已停止更新，直接写死它固定的版本
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")

    // 图片加载：Coil3 本身就是多平台的，桌面端有 jvm 产物
    implementation(libs.coil.compose)

    // 与 Android 端共用版本，避免这里的依赖和 catalog 漂移
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.datetime)
    implementation(libs.commons.text)
    // 设置存储：androidx DataStore 的 KMP(jvm) 产物，Google Maven 上有
    implementation("androidx.datastore:datastore-core:${libs.versions.datastore.get()}")
    implementation("androidx.datastore:datastore-preferences-core:${libs.versions.datastore.get()}")

    // ViewModel / 生命周期：JetBrains 维护的 androidx KMP 移植，包名与 Android 端一致
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.11.0")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-savedstate:2.11.0")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.11.0")
    implementation("org.jetbrains.androidx.savedstate:savedstate:1.4.0")
    implementation("org.jetbrains.androidx.navigation3:navigation3-ui:1.2.0-rc01")

    // 上游页面用 koinViewModel()/koinInject() 取依赖
    implementation("io.insert-koin:koin-core:${libs.versions.koin.bom.get()}")
    implementation("io.insert-koin:koin-compose:${libs.versions.koin.bom.get()}")
    implementation("io.insert-koin:koin-compose-viewmodel:${libs.versions.koin.bom.get()}")

    // Room 与自带 SQLite 驱动的 KMP(jvm) 产物
    implementation("androidx.room:room-runtime:${libs.versions.room.get()}")
    implementation("androidx.room:room-paging:${libs.versions.room.get()}")
    implementation("androidx.paging:paging-common:3.5.1")
    implementation("androidx.sqlite:sqlite:2.8.0-alpha01")
    implementation("androidx.sqlite:sqlite-bundled:2.8.0-alpha01")
    ksp("androidx.room:room-compiler:${libs.versions.room.get()}")
    implementation(libs.reorderable)
    implementation(libs.coil.svg)
    implementation(libs.coil.okhttp)
    implementation(libs.coil.cache.control)
    implementation("xmlpull:xmlpull:1.1.3.1")
    implementation("net.sf.kxml:kxml2:2.3.0")
    // S3 / WebDAV 走 Ktor client（与 Android 端同一套）
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    // MCP 客户端与内嵌 Web 服务都用 ktor：客户端要内容协商+SSE，服务端要 CIO 引擎
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.sse)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.server.caching.headers)
    implementation(libs.ktor.server.conditional.headers)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    // Compose Multiplatform 自己的返回手势/按键处理，androidx.activity.compose.BackHandler 由它转接
    implementation("org.jetbrains.compose.ui:ui-backhandler:1.12.1")
    // 上游 UI 用到的三个跨平台库：Haze 背景模糊、Lucide 图标、Paging 3 的 compose 绑定
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.haze.blur.material3)
    implementation(libs.haze.glass)
    implementation(libs.haze.glass.material3)
    implementation(libs.lucide.icons)
    implementation(libs.androidx.paging.compose)
    // RabbitLoadingIndicator 用的 MD3 表现型形状库
    implementation("androidx.graphics:graphics-shapes:1.1.0")
    // 聊天页用它按窗口宽度决定布局（androidx.compose.material3.adaptive 有 desktop 产物）
    implementation(libs.androidx.material3.adaptive.layout)
    // RouteActivity 的导航图用 lifecycle 的 navigation3 entry decorator（Google 已发 desktop 产物）
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.snakeyaml)
    implementation(libs.metadata.extractor)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization.json)
    implementation(libs.okhttp.logging)
    implementation(libs.modelcontextprotocol.kotlin.sdk)
    implementation(libs.pebble)
    // web/NsdServiceRegistrar 用纯 JVM 的 jmdns 做 mDNS 注册，Android 端也是同一个库
    implementation(libs.jmdns)
    // Android 框架自带 org.json，speech 的云端 provider 用它拼请求体；桌面端要显式依赖
    implementation("org.json:json:20240303")
    implementation(libs.xz)
    implementation(libs.jsoup)
    // 脚本搜索服务：search/SearchService.kt 的工厂会引用它，用 quickjs 的 JVM 产物
    implementation("io.github.dokar3:quickjs-kt-jvm:${libs.versions.quickjs.get()}")
    implementation(libs.diffutils)

    // LaTeX 公式排版：jlatexmath 是纯 Java 的 AWT 渲染引擎，顶替 Android 端 jlatexmath-android
    implementation("org.scilab.forge:jlatexmath:1.0.7")
    // 图片缩放：首选的 telephoto 只有 Android 产物（Maven Central 全系没有 jvm 变体），
    // 换 panpf zoomimage 的 coil3 版：KMP 发布，1.7.0 按 JetBrains Compose 1.12.0 编译，与本端 1.12.1 对齐
    implementation("io.github.panpf.zoomimage:zoomimage-compose-coil3:1.7.0")
    // 二维码识别：zxing core 是纯 Java 的，QRCode.kt 的生成与 ImageUtils 的解码共用它
    implementation(libs.zxing.core)
    // 相机扫码：webcam-capture 自带 v4l2/DSHOW 原生驱动做取流，顶替 quickie 的 CameraX 链路
    implementation("com.github.sarxos:webcam-capture:0.3.12")
    // 音效播放：mp3spi 是 JavaSound 的 MP3 解码 SPI，注册后 AudioSystem 能直接解上游 raw 里的 mp3
    implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")
    // 全文搜索分词：与 Android 端 jieba 扩展同源的纯 Java 实现，中文按词切分后走 fts5 unicode61
    implementation("com.huaban:jieba-analysis:1.0.2")

    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "me.rerere.rikkahub.linux.MainKt"

        // 静止页实测：默认参数 RSS 362MB；下面这几项把它压到 300MB。
        // 堆只用几十 MB，但默认 MaxHeap 是物理内存的 1/4，G1 的 remembered set 按堆预留大小分配（单占 57MB），
        // 换 SerialGC 后这项几乎归零。
        //
        // AppCDS（AutoCreateSharedArchive）实测把「进程起来→窗口可见」从 911ms 降到 306ms，
        // RSS 再降到 243MB。归档必须落在可写目录，且 JVM 不会替你把中间目录建出来，
        // 所以直接放在一定存在的 build/ 下（run 任务的工作目录就是本模块目录，build/ 已在 .gitignore 里）。
        // 注意：打包分发时这里不会展开 $HOME，若目标机器的 CWD 不可写，JVM 只会告警并照常启动。
        jvmArgs(
            "-Xmx512m",
            "-Xms32m",
            "-XX:+UseSerialGC",
            "-XX:MaxMetaspaceSize=128m",
            "-XX:+AutoCreateSharedArchive",
            "-XX:SharedArchiveFile=build/rikka-linux.jsa",
        )

        nativeDistributions {
            targetFormats(TargetFormat.AppImage, TargetFormat.Deb)
            packageName = "RikkaHub"
            packageVersion = "2.5.6"
            description = "RikkaHub on Linux"
            vendor = "rerere"
            modules("java.sql", "jdk.unsupported")
        }
    }
}
