package me.rerere.ui.webview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import me.rerere.rikkahub.linux.web.CefWebView

/*
 * Android WebView 的桌面端实现，底层是 KCEF（JetBrains 的 JCEF）。对外 API 与上游 ui/webview/WebView.kt 一致，
 * Mermaid、代码块 HTML 预览、工作区网页预览与 WebViewPage 都不用改。
 *
 * 渲染分两种（[WebViewMode]）：
 * - Offscreen：CEF 离屏绘制，像素拷进 Compose 的图片里画。和别的 Compose 内容一样参与裁剪、圆角、
 *   滚动和弹层遮挡，适合嵌在消息列表里的小块。代价是每帧一次像素拷贝
 * - Native：CEF 自己的原生窗口嵌进 SwingPanel，GPU 直出，没有拷贝，适合全屏页面。原生窗口总在
 *   Compose 内容上面，所以不能用在会被滚动或遮挡的位置
 *
 * 运行时首次使用时下载（见 linux/web/KcefRuntime.kt），未安装时控件里显示下载入口。
 */

enum class WebViewMode { Offscreen, Native }

class WebSettings {
    var javaScriptEnabled: Boolean = true
    var domStorageEnabled: Boolean = true
    var builtInZoomControls: Boolean = false
    var displayZoomControls: Boolean = false
    var useWideViewPort: Boolean = false
    var loadWithOverviewMode: Boolean = false
    var allowFileAccess: Boolean = false
    var allowContentAccess: Boolean = false
    var allowUniversalAccessFromFileURLs: Boolean = false
}

sealed class WebContent {
    data class Url(
        val url: String,
        val additionalHttpHeaders: Map<String, String> = emptyMap(),
        val clearHistory: Boolean = false,
    ) : WebContent()

    data class Data(
        val data: String,
        val baseUrl: String? = null,
        val encoding: String = "utf-8",
        val mimeType: String? = null,
        val historyUrl: String? = null,
    ) : WebContent()

    data object NavigatorOnly : WebContent()
}

/** 与 android.webkit.ConsoleMessage 同形，WebViewPage 的控制台面板直接读它 */
class ConsoleMessage(
    private val level: MessageLevel,
    private val message: String,
    private val sourceId: String,
    private val lineNumber: Int,
) {
    enum class MessageLevel { TIP, LOG, WARNING, ERROR, DEBUG }

    fun messageLevel(): MessageLevel = level
    fun message(): String = message
    fun sourceId(): String = sourceId
    fun lineNumber(): Int = lineNumber
}

/** 浏览器一侧的操作，由 [CefWebView] 实现 */
interface WebViewController {
    fun goBack()
    fun goForward()
    fun reload()
    fun stopLoading()

    /** 与 Android 的 evaluateJavascript 一致：[callback] 收到 JSON 编码的结果 */
    fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?)
}

@Stable
class WebViewState(
    initialContent: WebContent = WebContent.NavigatorOnly,
    val interfaces: Map<String, Any> = emptyMap(),
    val settings: WebSettings.() -> Unit = {},
) {
    var content: WebContent by mutableStateOf(initialContent)
    internal var forceReload: Boolean by mutableStateOf(false)

    var isLoading: Boolean by mutableStateOf(false)
        internal set
    var loadingProgress: Float by mutableFloatStateOf(0f)
        internal set

    var pageTitle: String? by mutableStateOf(null)
        internal set
    var currentUrl: String? by mutableStateOf(null)
        internal set

    var canGoBack: Boolean by mutableStateOf(false)
        internal set
    var canGoForward: Boolean by mutableStateOf(false)
        internal set

    var consoleMessages: List<ConsoleMessage> by mutableStateOf(emptyList())
        internal set

    var javaScriptEnabled: Boolean by mutableStateOf(true)

    /** 上游 Mermaid 用 `webView?.evaluateJavascript(...)` 调页面里的导出函数，名字保持一致 */
    var webView: WebViewController? by mutableStateOf(null)
        internal set

    fun loadUrl(url: String, additionalHttpHeaders: Map<String, String> = emptyMap()) {
        forceReload = (content as? WebContent.Url)?.url == url || forceReload
        content = WebContent.Url(url, additionalHttpHeaders)
    }

    fun loadData(
        data: String,
        baseUrl: String? = null,
        encoding: String = "utf-8",
        mimeType: String? = null,
        historyUrl: String? = null,
    ) {
        content = WebContent.Data(data, baseUrl, encoding, mimeType, historyUrl)
    }

    fun goBack() {
        webView?.goBack()
    }

    fun goForward() {
        webView?.goForward()
    }

    fun reload() {
        webView?.reload()
    }

    fun stopLoading() {
        webView?.stopLoading()
    }

    fun clearHistory() = Unit

    internal fun pushConsoleMessage(message: ConsoleMessage) {
        consoleMessages = (consoleMessages + message).takeLast(64)
    }
}

@Composable
fun rememberWebViewState(
    url: String = "about:blank",
    additionalHttpHeaders: Map<String, String> = emptyMap(),
    interfaces: Map<String, Any> = emptyMap(),
    settings: WebSettings.() -> Unit = {},
): WebViewState = remember(url, additionalHttpHeaders) {
    WebViewState(WebContent.Url(url, additionalHttpHeaders), interfaces, settings)
}

@Composable
fun rememberWebViewState(
    data: String,
    baseUrl: String? = null,
    encoding: String = "utf-8",
    mimeType: String? = null,
    historyUrl: String? = null,
    interfaces: Map<String, Any> = emptyMap(),
    settings: WebSettings.() -> Unit = {},
): WebViewState = remember(data, baseUrl, encoding, mimeType, historyUrl) {
    WebViewState(WebContent.Data(data, baseUrl, encoding, mimeType, historyUrl), interfaces, settings)
}

/**
 * 默认离屏渲染：上游的调用点（消息里的 Mermaid、代码块预览、编辑器预览）都在可滚动或可被遮挡的位置。
 * 全屏的 WebViewPage 由桌面端那份页面显式传 [WebViewMode.Native]。
 */
@Composable
fun WebView(
    state: WebViewState,
    modifier: Modifier = Modifier,
    mode: WebViewMode = WebViewMode.Offscreen,
    onCreated: (WebViewController) -> Unit = {},
    onUpdated: (WebViewController) -> Unit = {},
) {
    Box(modifier = modifier) {
        CefWebView(
            state = state,
            mode = mode,
            modifier = Modifier.fillMaxSize(),
            onCreated = onCreated,
            onUpdated = onUpdated
        )
        if (state.isLoading) {
            LinearProgressIndicator(
                progress = { state.loadingProgress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
