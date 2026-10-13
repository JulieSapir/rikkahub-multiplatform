package me.rerere.rikkahub.linux.desktop

import android.util.Log
import androidx.activity.result.contract.buildFileChooser
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.rikkahub.web.WebServerManager
import org.koin.core.context.GlobalContext

/**
 * GraalVM 的可达性元数据由 agent 记录「真正跑过的代码」，而采集只画到首屏，于是首屏之外的两条
 * 路径在原生包里缺项：
 *  - 文件选择框：Swing 的 UIManager 按类名反射实例化 Metal*UI，缺反射项时构造 JFileChooser 直接抛
 *    「no ComponentUI class for javax.swing.JFileChooser」；
 *  - 内嵌 Web 服务：前端产物在 static 目录下，运行期按路径查 classloader，没进镜像就是 404。
 *
 * 所以采集模式（aot/build.sh 传 -Drikka.aot.warmup=true）下把这两条路径各走一遍，让 agent 记全，
 * 同时把实测结论打进日志。正常运行时这个开关不存在，本文件等于不存在。
 */
object AotWarmup {
    private const val TAG = "AotWarmup"
    private const val PORT = 18099

    val enabled: Boolean get() = System.getProperty("rikka.aot.warmup").toBoolean()

    suspend fun exercise() {
        runCatching { exerciseFileChooser() }.onFailure { Log.e(TAG, "文件选择框预热失败", it) }
        runCatching { exerciseClipboard() }.onFailure { Log.e(TAG, "剪贴板预热失败", it) }
        runCatching { exerciseWebServer() }.onFailure { Log.e(TAG, "Web 服务预热失败", it) }
    }

    /**
     * X11 上剪贴板是把 Transferable 的数据用 java.io 序列化后塞进 selection 里传给别人的，
     * 所以「写进剪贴板」这一步本身就会牵动一批只有走一遍才会被 agent 记下来的类
     * （不记的话原生包里复制看似成功、别的应用来取就什么也拿不到）。
     * 采集跑在独占的 Xvfb 上，覆盖的是那台机器的剪贴板，不影响用户会话。
     */
    private suspend fun exerciseClipboard() = withContext(Dispatchers.Swing) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val marker = "rikka-aot-warmup"
        clipboard.setContents(StringSelection(marker), null)
        val back = runCatching { clipboard.getData(DataFlavor.stringFlavor) }.getOrNull()
        Log.i(TAG, "clipboard round-trip = ${back == marker}")
    }

    private suspend fun exerciseFileChooser() = withContext(Dispatchers.Swing) {
        val chooser = buildFileChooser(multiple = true, extensions = setOf("png", "jpg", "jpeg", "webp"))
        Log.i(TAG, "file-chooser ui = ${chooser.ui.javaClass.name}")
    }

    private suspend fun exerciseWebServer() {
        val manager = GlobalContext.get().get<WebServerManager>()
        if (!manager.state.value.isRunning) {
            // localhostOnly：构建机上跑采集，不该往局域网广播 mDNS
            manager.start(port = PORT, localhostOnly = true)
        }
        val started = withTimeoutOrNull(15_000) {
            while (!manager.state.value.isRunning) delay(200)
            true
        } ?: false
        val base = "http://127.0.0.1:${manager.state.value.port}"
        val index = fetch(base, "/")
        val assets = Regex("""(?:src|href)="(/[^"]+)"""").findAll(index.body).map { it.groupValues[1] }.toList()
        val assetBytes = assets.sumOf { fetch(base, it).bytes }
        Log.i(TAG, "web running=$started index=${index.body.length}B assets=${assets.size} fetched=${assetBytes}B")
        // 只读接口逐个走一遍：SPA 的每个页面都靠它们取数据，采集时不跑到的话运行期才炸。
        // {id} 用 paged 响应里的真实会话 id，让详情/消息那条链路也进元数据。
        var conversationId: String? = null
        for (path in API_PROBES) {
            val probe = path.replace("{id}", conversationId ?: "")
            if (path.contains("{id}") && conversationId == null) continue
            val res = fetch(base, probe)
            Log.i(TAG, "api GET $probe -> ${res.status} ${res.bytes}B")
            if (path.startsWith("/api/conversations/paged")) {
                conversationId = CONVERSATION_ID.find(res.body)?.groupValues?.get(1)
            }
        }
        manager.stop()
    }

    private data class Response(val status: Int, val body: String, val bytes: Int)

    private fun fetch(base: String, path: String): Response = runCatching {
        val conn = URL(base + path).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 3000
        conn.readTimeout = 15000
        val bytes = conn.inputStream.use { it.readBytes() }
        Response(conn.responseCode, String(bytes, Charsets.UTF_8), bytes.size)
    }.getOrElse { Response(-1, "", 0) }

    private val CONVERSATION_ID = """"id"\s*:\s*"([^"]+)"""".toRegex()

    /** SPA 首屏与各页面用到的只读接口（写接口会改数据，采集不碰）。 */
    private val API_PROBES = listOf(
        "/api/conversations/paged?offset=0&limit=5&folderId=none",
        "/api/conversations/{id}",
        "/api/folders",
        "/api/settings",
        "/api/ai-icon?name=deepseek",
    )
}
