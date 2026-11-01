package me.rerere.rikkahub.linux.web

import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.rerere.rikkahub.linux.state.AppPaths
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.cef.CefApp
import java.io.File
import java.security.MessageDigest

/**
 * 内嵌网页用的 CEF 运行时（Chromium，约 200 MB 下载）。
 *
 * KCEF 默认从 GitHub API 找最新的 JetBrains Runtime 包：未登录有限流，而且“最新”会和 Maven 上 jcef 的
 * Java 层版本对不上。这里固定到与 KCEF 2025.03.23 配套的 JBR 构建，直接从 JetBrains 的 CDN 下载并校验
 * SHA-512，只解出 CEF 本身的文件（不要 JBR 里的 JDK：它的 libjawt/libawt 混进当前 JVM 会出问题），
 * 落到数据目录里，装好以后才交给 KCEF 初始化。
 *
 * 不在启动时初始化：CEF 会起 GPU/网络等子进程，只有真的要显示网页时才付这份开销。
 */
object KcefRuntime {
    sealed interface State {
        /** 当前 CPU 架构没有对应的运行时包 */
        data class Unsupported(val arch: String) : State
        data object NotInstalled : State

        /** [progress] 为 0..1，服务器没给长度时为 null */
        data class Downloading(val progress: Float?) : State
        data object Installing : State
        data object Installed : State
        data object Initializing : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    private const val JBR_VERSION = "17.0.14"
    private const val JBR_BUILD = "b1367.22"
    private const val DOWNLOAD_SIZE_HINT_MB = 200

    /** JBR 的架构名 -> 发布页给出的 SHA-512 */
    private val packages = mapOf(
        "x64" to "1c875b43de4d20deb429a686331e4ea5c7c34c519569d1a3b785867d761ed002941203f79b19a338e4a49fab48a6cd4d35408a7356f55781e95c3d60c6c5a8d1",
        "aarch64" to "1d8aa9594b80039b5c7aab31cb079e746c70f473bcbecf626378cd6897628e263161fbc2757f1b7300d1c7819e55d1b94e0a5d74ce1cce88c66201dcad18562b",
    )

    /** JBR 的 lib/ 目录里属于 CEF 的部分，其余是 JDK */
    private val cefFiles = setOf(
        "libcef.so", "libjcef.so", "jcef_helper", "chrome-sandbox",
        "icudtl.dat", "snapshot_blob.bin", "v8_context_snapshot.bin",
        "libEGL.so", "libGLESv2.so", "libvk_swiftshader.so", "vk_swiftshader_icd.json", "libvulkan.so.1",
    )
    private val requiredFiles = listOf("libcef.so", "libjcef.so", "jcef_helper", "icudtl.dat", "resources.pak")

    val downloadSizeHintMb: Int get() = DOWNLOAD_SIZE_HINT_MB

    private val arch: String? = when (System.getProperty("os.arch").lowercase()) {
        "amd64", "x86_64" -> "x64"
        "aarch64", "arm64" -> "aarch64"
        else -> null
    }

    /** 按构建号分目录：换版本时旧目录不会被误当成已安装 */
    val installDir: File = File(AppPaths.dataDir, "kcef/$JBR_BUILD")
    private val lockFile get() = File(installDir, "install.lock")

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private fun initialState(): State = when {
        arch == null -> State.Unsupported(System.getProperty("os.arch"))
        isInstalled() -> State.Installed
        else -> State.NotInstalled
    }

    private fun isInstalled(): Boolean = lockFile.isFile && requiredFiles.all { File(installDir, it).isFile }

    /** 下载并安装（已装好时直接初始化）。重复调用是安全的 */
    fun install() {
        scope.launch {
            mutex.withLock {
                when (_state.value) {
                    State.NotInstalled, is State.Failed -> Unit
                    else -> return@withLock
                }
                runCatching {
                    if (!isInstalled()) download()
                }.onFailure {
                    it.printStackTrace()
                    _state.value = State.Failed("下载失败：${it.message ?: it.javaClass.simpleName}")
                    return@withLock
                }
                _state.value = State.Installed
            }
            start()
        }
    }

    /** 已安装时初始化 CEF；第一个网页控件出现时调用 */
    fun start() {
        scope.launch {
            mutex.withLock {
                if (_state.value != State.Installed) return@withLock
                _state.value = State.Initializing
                runCatching { initialize() }.onFailure {
                    it.printStackTrace()
                    _state.value = State.Failed("网页组件启动失败：${it.message ?: it.javaClass.simpleName}")
                }
            }
        }
    }

    /** 退出时调用；CEF 没启动过就什么都不做 */
    fun shutdown() {
        if (_state.value == State.Ready) runCatching { KCEF.disposeBlocking() }
    }

    private suspend fun initialize() {
        var failure: Throwable? = null
        KCEF.init(
            builder = {
                installDir(installDir)
                // JCefAppConfig 的默认路径指向当前 JDK 的 lib/，必须显式指到安装目录
                settings {
                    browserSubProcessPath = File(installDir, "jcef_helper").path
                    resourcesDirPath = installDir.path
                    localesDirPath = File(installDir, "locales").path
                    cachePath = File(AppPaths.cacheDir, "kcef").path
                    windowlessRenderingEnabled = true
                    noSandbox = true
                    logSeverity = KCEFBuilder.Settings.LogSeverity.Error
                }
                progress {
                    onInitialized { _state.value = State.Ready }
                }
            },
            onError = { failure = it },
            onRestartRequired = { failure = IllegalStateException("需要重启应用") },
        )
        failure?.let { throw it }
        // onInitialized 来自 CefApp 的异步回调；init 返回时可能已经错过，按当前状态补一次
        if (CefApp.getState() == CefApp.CefAppState.INITIALIZED) _state.value = State.Ready
    }

    private fun download() {
        val arch = requireNotNull(arch)
        val name = "jbr_jcef-$JBR_VERSION-linux-$arch-$JBR_BUILD.tar.gz"
        val expected = requireNotNull(packages[arch])
        val parent = installDir.parentFile.apply { mkdirs() }
        val archive = File(parent, "$name.part")
        val staging = File(parent, "$JBR_BUILD.staging")
        try {
            _state.value = State.Downloading(0f)
            val digest = MessageDigest.getInstance("SHA-512")
            val request = Request.Builder().url("https://cache-redirector.jetbrains.com/intellij-jbr/$name").build()
            OkHttpClient().newCall(request).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                val body = requireNotNull(response.body)
                val total = body.contentLength().takeIf { it > 0 }
                body.byteStream().use { input ->
                    archive.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        var reported = -1
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            done += n
                            val percent = total?.let { (done * 100 / it).toInt() } ?: -1
                            if (percent != reported) {
                                reported = percent
                                _state.value = State.Downloading(total?.let { done.toFloat() / it })
                            }
                        }
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            check(actual == expected) { "校验和不匹配" }

            _state.value = State.Installing
            staging.deleteRecursively()
            extractCef(archive, staging)
            requiredFiles.forEach { check(File(staging, it).isFile) { "运行时包里缺少 $it" } }
            installDir.deleteRecursively()
            check(staging.renameTo(installDir)) { "无法写入 ${installDir.path}" }
            lockFile.createNewFile()
        } finally {
            archive.delete()
            staging.deleteRecursively()
        }
    }

    /** 只解出 `<根目录>/lib/` 下属于 CEF 的文件，保留可执行位 */
    internal fun extractCef(archive: File, target: File) {
        val root = target.canonicalFile
        TarArchiveInputStream(GzipCompressorInputStream(archive.inputStream().buffered())).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                val relative = entry.name.substringAfter('/').removePrefix("lib/")
                if (relative == entry.name.substringAfter('/')) continue // 不在 lib/ 下
                val top = relative.substringBefore('/')
                val keep = top in cefFiles || top == "locales" || (top == relative && top.endsWith(".pak"))
                if (!keep || entry.isSymbolicLink || entry.isLink) continue
                val file = File(root, relative).canonicalFile
                check(file.path.startsWith(root.path + File.separator)) { "非法路径 ${entry.name}" }
                if (entry.isDirectory) {
                    file.mkdirs()
                    continue
                }
                file.parentFile.mkdirs()
                file.outputStream().use { tar.copyTo(it) }
                if (entry.mode and 0b001_001_001 != 0) file.setExecutable(true, false)
            }
        }
    }
}
