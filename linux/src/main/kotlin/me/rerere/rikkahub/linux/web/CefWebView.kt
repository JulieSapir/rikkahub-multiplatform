package me.rerere.rikkahub.linux.web

import android.content.Context
import android.webkit.JavascriptInterface
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBrowser
import dev.datlag.kcef.KCEFClient
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.common.android.appTempFolder
import me.rerere.ui.webview.ConsoleMessage
import me.rerere.ui.webview.WebContent
import me.rerere.ui.webview.WebViewController
import me.rerere.ui.webview.WebViewMode
import me.rerere.ui.webview.WebViewState
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.browser.CefRendering
import org.cef.callback.CefContextMenuParams
import org.cef.callback.CefMenuModel
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefContextMenuHandlerAdapter
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.cef.handler.CefRenderHandlerAdapter
import org.cef.handler.CefScreenInfo
import org.cef.network.CefRequest
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import org.jetbrains.skia.impl.BufferUtil
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 一个 [WebViewState] 对应一个 CEF 浏览器。运行时没装好时显示安装入口；装好后按 [mode] 渲染，
 * 两种模式的差别见 ui/webview/WebViewJvm.kt 的说明。
 */
@Composable
internal fun CefWebView(
    state: WebViewState,
    mode: WebViewMode,
    modifier: Modifier,
    visible: Boolean = true,
    onCreated: (WebViewController) -> Unit,
    onUpdated: (WebViewController) -> Unit,
) {
    val runtime by KcefRuntime.state.collectAsState()
    if (runtime != KcefRuntime.State.Ready) {
        LaunchedEffect(runtime) {
            if (runtime == KcefRuntime.State.Installed) KcefRuntime.start()
        }
        RuntimePlaceholder(runtime, state, modifier)
        return
    }

    val context = LocalContext.current
    val session = remember(state, mode) { CefSession(state, mode, context) }
    DisposableEffect(session) {
        state.webView = session
        onCreated(session)
        onDispose {
            if (state.webView === session) state.webView = null
            session.close()
        }
    }
    LaunchedEffect(session, state.content, state.forceReload) {
        session.load(state.content)
        state.forceReload = false
        onUpdated(session)
    }

    when (mode) {
        WebViewMode.Offscreen -> OffscreenSurface(session, modifier)
        WebViewMode.Native -> SwingPanel(
            factory = { JPanel(BorderLayout()).apply { add(session.browser.uiComponent, BorderLayout.CENTER) } },
            modifier = modifier,
            update = { panel -> panel.isVisible = visible },
        )
    }
}

@Composable
private fun RuntimePlaceholder(runtime: KcefRuntime.State, state: WebViewState, modifier: Modifier) {
    val context = LocalContext.current
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val text = when (runtime) {
                KcefRuntime.State.NotInstalled -> "网页预览需要先下载 Chromium 组件（约 ${KcefRuntime.downloadSizeHintMb} MB）"
                is KcefRuntime.State.Downloading -> "正在下载网页组件" +
                    (runtime.progress?.let { " ${(it * 100).roundToInt()}%" } ?: "")
                KcefRuntime.State.Installing -> "正在安装网页组件"
                KcefRuntime.State.Installed, KcefRuntime.State.Initializing -> "正在启动网页组件"
                is KcefRuntime.State.Failed -> runtime.message
                is KcefRuntime.State.Unsupported -> "当前架构（${runtime.arch}）没有可用的网页组件"
                KcefRuntime.State.Ready -> ""
            }
            Text(text, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            when (runtime) {
                is KcefRuntime.State.Downloading -> {
                    val progress = runtime.progress
                    if (progress != null) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(0.6f))
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.6f))
                    }
                }
                KcefRuntime.State.Installing, KcefRuntime.State.Installed, KcefRuntime.State.Initializing ->
                    CircularProgressIndicator()
                KcefRuntime.State.NotInstalled, is KcefRuntime.State.Failed ->
                    OutlinedButton(onClick = { KcefRuntime.install() }) {
                        Text(if (runtime is KcefRuntime.State.Failed) "重试" else "下载")
                    }
                else -> Unit
            }
            OutlinedButton(onClick = { openInSystemBrowser(context, state.content) }) {
                Text("在浏览器中打开")
            }
        }
    }
}

/** 运行时不可用时的退路：Data 内容写成临时文件交给系统浏览器 */
internal fun openInSystemBrowser(context: Context, content: WebContent) {
    val uri = when (content) {
        is WebContent.Url -> runCatching { java.net.URI(content.url) }.getOrNull()
        is WebContent.Data -> {
            val ext = if (content.mimeType?.contains("svg") == true) "svg" else "html"
            File(context.appTempFolder, "webview_preview_${System.nanoTime()}.$ext")
                .apply { writeText(content.data, Charsets.UTF_8) }
                .toURI()
        }
        WebContent.NavigatorOnly -> null
    } ?: return
    runCatching { java.awt.Desktop.getDesktop().browse(uri) }.onFailure { it.printStackTrace() }
}

/** 离屏模式：CEF 画进 [CefSession.frame]，这里按布局尺寸缩放画出来，并把鼠标/键盘事件转发回去 */
@Composable
private fun OffscreenSurface(session: CefSession, modifier: Modifier) {
    val density = LocalDensity.current.density
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val currentFocused by rememberUpdatedState(focused)
    session.density = density

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                session.screenOrigin = coordinates.positionOnScreen()
                session.resize(coordinates.size)
            }
            .pointerHoverIcon(session.cursor)
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                session.setFocus(it.isFocused)
            }
            .focusable()
            .onKeyEvent { event ->
                val awt = event.awtEventOrNull ?: return@onKeyEvent false
                session.browser.sendKeyEvent(awt)
                true
            }
            .pointerInput(session) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val position = event.changes.firstOrNull()?.position ?: continue
                        val awt = event.awtEventOrNull
                        when (event.type) {
                            PointerEventType.Press -> {
                                focusRequester.requestFocus()
                                session.sendMouse(MouseEvent.MOUSE_PRESSED, position, awt)
                            }
                            PointerEventType.Release -> session.sendMouse(MouseEvent.MOUSE_RELEASED, position, awt)
                            PointerEventType.Move -> session.sendMouse(
                                if (event.buttons.isPrimaryPressed || event.buttons.isSecondaryPressed) {
                                    MouseEvent.MOUSE_DRAGGED
                                } else {
                                    MouseEvent.MOUSE_MOVED
                                },
                                position,
                                awt,
                            )
                            PointerEventType.Exit -> session.sendMouse(MouseEvent.MOUSE_EXITED, position, awt)
                            PointerEventType.Scroll -> {
                                // 没点进来之前滚轮留给外层列表：消息里的小块不该劫持整页滚动
                                if (currentFocused && awt is MouseWheelEvent) {
                                    session.sendWheel(position, awt)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
                }
            }
            .drawBehind {
                session.frame // 订阅新帧
                drawIntoCanvas { canvas -> session.drawTo(canvas.nativeCanvas, size.width, size.height) }
            },
    )
}

/**
 * CEF 浏览器与 [WebViewState] 之间的胶水：加载内容、回填标题/地址/前进后退、控制台、JS 桥，
 * 离屏模式下另外管像素缓冲与输入转发。
 */
private class CefSession(
    private val state: WebViewState,
    private val mode: WebViewMode,
    private val context: Context,
) : WebViewController {
    private val client: KCEFClient = KCEF.newClientBlocking()

    /** loadDataWithBaseURL 登记的页面：URL -> (mime, 内容) */
    private val pages = ConcurrentHashMap<String, Pair<String, String>>()
    private val evalCallbacks = ConcurrentHashMap<Int, (String?) -> Unit>()
    private val evalIds = AtomicInteger()
    private val router = CefMessageRouter.create(CefMessageRouter.CefMessageRouterConfig(QUERY_FN, QUERY_CANCEL_FN))

    // ---- 离屏渲染 ----
    var frame by mutableIntStateOf(0)
        private set
    var cursor: PointerIcon by mutableStateOf(PointerIcon(Cursor.getDefaultCursor()))
        private set
    @Volatile var density: Float = 1f
    @Volatile var screenOrigin: Offset = Offset.Zero
    @Volatile private var viewSize = IntSize(1, 1) // DIP
    @Volatile private var created = false
    private val pixelLock = Any()
    private var view: Bitmap? = null
    private var popup: Bitmap? = null
    private var popupRect: Rectangle? = null
    private val eventSource = java.awt.Canvas()

    private val renderHandler = object : CefRenderHandlerAdapter() {
        override fun getViewRect(browser: CefBrowser?) = Rectangle(0, 0, viewSize.width, viewSize.height)

        override fun getDeviceScaleFactor(browser: CefBrowser?): Double = density.toDouble()

        override fun getScreenInfo(browser: CefBrowser?, screenInfo: CefScreenInfo): Boolean {
            val rect = Rectangle(0, 0, viewSize.width, viewSize.height)
            screenInfo.Set(density.toDouble(), 32, 8, false, rect, rect)
            return true
        }

        override fun getScreenPoint(browser: CefBrowser?, viewPoint: Point): Point = Point(
            (screenOrigin.x / density).roundToInt() + viewPoint.x,
            (screenOrigin.y / density).roundToInt() + viewPoint.y,
        )

        override fun onPopupShow(browser: CefBrowser?, show: Boolean) {
            if (!show) {
                synchronized(pixelLock) {
                    popup?.close()
                    popup = null
                    popupRect = null
                }
                frame++
            }
        }

        override fun onPopupSize(browser: CefBrowser?, size: Rectangle) {
            synchronized(pixelLock) { popupRect = Rectangle(size) }
        }

        override fun onPaint(
            browser: CefBrowser?,
            isPopup: Boolean,
            dirtyRects: Array<out Rectangle>?,
            buffer: ByteBuffer,
            width: Int,
            height: Int,
        ) {
            synchronized(pixelLock) {
                val target = if (isPopup) popup else view
                val bitmap = if (target != null && target.width == width && target.height == height) {
                    target
                } else {
                    target?.close()
                    Bitmap().apply {
                        allocPixels(ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL))
                    }.also { if (isPopup) popup = it else view = it }
                }
                val pixmap = bitmap.peekPixels() ?: return
                val dst = BufferUtil.getByteBufferFromPointer(pixmap.addr, width * height * 4)
                buffer.rewind()
                dst.put(buffer)
                pixmap.close()
                bitmap.notifyPixelsChanged()
            }
            SwingUtilities.invokeLater { frame++ }
        }

        override fun onCursorChange(browser: CefBrowser?, cursorType: Int): Boolean {
            SwingUtilities.invokeLater {
                cursor = PointerIcon(runCatching { Cursor.getPredefinedCursor(cursorType) }.getOrDefault(Cursor.getDefaultCursor()))
            }
            return true
        }
    }

    val browser: KCEFBrowser = when (mode) {
        WebViewMode.Offscreen -> client.createBrowser(
            null,
            CefRendering.CefRenderingWithHandler(renderHandler, null),
            false,
        )
        WebViewMode.Native -> client.createBrowser(null, CefRendering.DEFAULT, false)
    }

    init {
        client.addRequestHandler(LocalContentRequestHandler(context, pages))
        router.addHandler(BridgeHandler(), true)
        client.addMessageRouter(router)
        client.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onTitleChange(browser: CefBrowser?, title: String?) = onEdt { state.pageTitle = title }
            override fun onAddressChange(browser: CefBrowser?, frame: CefFrame?, url: String?) {
                if (frame?.isMain != false) onEdt { state.currentUrl = url }
            }

            override fun onConsoleMessage(
                browser: CefBrowser?,
                level: CefSettings.LogSeverity?,
                message: String?,
                source: String?,
                line: Int,
            ): Boolean {
                val mapped = when (level) {
                    CefSettings.LogSeverity.LOGSEVERITY_ERROR, CefSettings.LogSeverity.LOGSEVERITY_FATAL -> ConsoleMessage.MessageLevel.ERROR
                    CefSettings.LogSeverity.LOGSEVERITY_WARNING -> ConsoleMessage.MessageLevel.WARNING
                    CefSettings.LogSeverity.LOGSEVERITY_VERBOSE -> ConsoleMessage.MessageLevel.DEBUG
                    else -> ConsoleMessage.MessageLevel.LOG
                }
                onEdt { state.pushConsoleMessage(ConsoleMessage(mapped, message.orEmpty(), source.orEmpty(), line)) }
                return false
            }
        })
        client.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadingStateChange(browser: CefBrowser?, isLoading: Boolean, canGoBack: Boolean, canGoForward: Boolean) =
                onEdt {
                    state.isLoading = isLoading
                    state.loadingProgress = if (isLoading) 0.1f else 0f
                    state.canGoBack = canGoBack
                    state.canGoForward = canGoForward
                }

            override fun onLoadStart(browser: CefBrowser?, frame: CefFrame?, transitionType: CefRequest.TransitionType?) {
                if (frame?.isMain == true) onEdt { state.loadingProgress = 0.5f }
            }

            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                frame?.executeJavaScript(bridgeScript(), frame.url, 0)
                if (frame?.isMain == true && overviewMode) frame.executeJavaScript(OVERVIEW_SCRIPT, frame.url, 0)
            }

            override fun onLoadError(
                browser: CefBrowser?,
                frame: CefFrame?,
                errorCode: CefLoadHandler.ErrorCode?,
                errorText: String?,
                failedUrl: String?,
            ) {
                if (frame?.isMain == true && errorCode != CefLoadHandler.ErrorCode.ERR_ABORTED) {
                    onEdt {
                        state.pushConsoleMessage(
                            ConsoleMessage(ConsoleMessage.MessageLevel.ERROR, "$errorText ($errorCode)", failedUrl.orEmpty(), 0),
                        )
                    }
                }
            }
        })
        client.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onAfterCreated(browser: CefBrowser?) {
                created = true
                if (mode == WebViewMode.Offscreen) browser?.wasResized(viewSize.width, viewSize.height)
                // 原生浏览器建好之前 loadURL 会被 CEF 丢掉，先存着的内容这时补上
                pending?.let { onEdt { load(it) } }
                pending = null
            }

            // 与 Android WebView 默认一致：新窗口在当前视图里打开
            override fun onBeforePopup(browser: CefBrowser?, frame: CefFrame?, targetUrl: String?, targetFrameName: String?): Boolean {
                if (!targetUrl.isNullOrBlank()) browser?.loadURL(targetUrl)
                return true
            }
        })
        if (mode == WebViewMode.Offscreen) {
            // 离屏模式下 CEF 自带的右键菜单没有地方画
            client.addContextMenuHandler(object : CefContextMenuHandlerAdapter() {
                override fun onBeforeContextMenu(browser: CefBrowser?, frame: CefFrame?, params: CefContextMenuParams?, model: CefMenuModel) {
                    model.clear()
                }
            })
            browser.createImmediately()
        }
    }

    @Volatile private var pending: WebContent? = null

    /**
     * Android 上 useWideViewPort + loadWithOverviewMode 会按 `<meta name=viewport content="width=N">` 排版、
     * 再整体缩小到控件宽度（Mermaid 就靠它把 1024 宽的图塞进消息里）。桌面 Chromium 不认 viewport，
     * 在页面加载完以后用 CSS zoom 做同样的事
     */
    private val overviewMode = me.rerere.ui.webview.WebSettings().apply(state.settings)
        .let { it.useWideViewPort && it.loadWithOverviewMode }

    fun load(content: WebContent) {
        if (!created) {
            pending = content
            return
        }
        when (content) {
            is WebContent.Url -> {
                if (content.url.isEmpty()) return
                if (content.additionalHttpHeaders.isEmpty()) {
                    browser.loadURL(content.url)
                } else {
                    browser.loadRequest(
                        CefRequest.create().apply {
                            url = content.url
                            setHeaderMap(content.additionalHttpHeaders)
                        },
                    )
                }
            }
            is WebContent.Data -> {
                val url = content.baseUrl?.takeIf { it.startsWith("http") } ?: DEFAULT_BASE_URL
                val key = pageKey(url)
                pages[key] = (content.mimeType ?: "text/html") to content.data
                // loadURL 同一个地址时 CEF 也会重新请求，内容换了能刷新
                browser.loadURL(key)
            }
            WebContent.NavigatorOnly -> Unit
        }
    }

    fun close() {
        evalCallbacks.clear()
        runCatching { browser.dispose() }
        runCatching { client.removeMessageRouter(router) }
        runCatching { router.dispose() }
        runCatching { client.dispose() }
        synchronized(pixelLock) {
            view?.close()
            popup?.close()
            view = null
            popup = null
        }
    }

    // ---- WebViewController ----
    override fun goBack() = browser.goBack()
    override fun goForward() = browser.goForward()
    override fun reload() = browser.reload()
    override fun stopLoading() = browser.stopLoad()

    override fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?) {
        if (callback == null) {
            browser.executeJavaScript(script, browser.url, 0)
            return
        }
        val id = evalIds.incrementAndGet()
        evalCallbacks[id] = callback
        val literal = Json.encodeToString(String.serializer(), script)
        browser.executeJavaScript(
            """
            (function(){var r;try{r=(0,eval)($literal);}catch(e){r=null;}
            var s;try{s=JSON.stringify(r===undefined?null:r);}catch(e){s='null';}
            window.$QUERY_FN({request:JSON.stringify({e:$id,r:s})});})();
            """.trimIndent(),
            browser.url,
            0,
        )
    }

    // ---- 离屏模式的尺寸与输入 ----
    fun resize(sizePx: IntSize) {
        val dip = IntSize(
            max(1, (sizePx.width / density).roundToInt()),
            max(1, (sizePx.height / density).roundToInt()),
        )
        if (dip == viewSize) return
        viewSize = dip
        if (created && mode == WebViewMode.Offscreen) browser.wasResized(dip.width, dip.height)
    }

    fun setFocus(focused: Boolean) {
        if (created) browser.setFocus(focused)
    }

    fun sendMouse(id: Int, position: Offset, awt: MouseEvent?) {
        if (!created) return
        val button = awt?.button?.takeIf { id == MouseEvent.MOUSE_PRESSED || id == MouseEvent.MOUSE_RELEASED } ?: MouseEvent.NOBUTTON
        browser.sendMouseEvent(
            MouseEvent(
                eventSource,
                id,
                awt?.`when` ?: System.currentTimeMillis(),
                awt?.modifiersEx ?: 0,
                (position.x / density).roundToInt(),
                (position.y / density).roundToInt(),
                awt?.clickCount ?: if (button != MouseEvent.NOBUTTON) 1 else 0,
                false,
                button,
            ),
        )
    }

    fun sendWheel(position: Offset, awt: MouseWheelEvent) {
        if (!created) return
        browser.sendMouseWheelEvent(
            MouseWheelEvent(
                eventSource,
                MouseEvent.MOUSE_WHEEL,
                awt.`when`,
                awt.modifiersEx,
                (position.x / density).roundToInt(),
                (position.y / density).roundToInt(),
                0,
                0,
                0,
                false,
                awt.scrollType,
                awt.scrollAmount,
                awt.wheelRotation,
                awt.preciseWheelRotation,
            ),
        )
    }

    fun drawTo(canvas: org.jetbrains.skia.Canvas, width: Float, height: Float) {
        val (viewImage, popupImage, rect) = synchronized(pixelLock) {
            Triple(view?.let(Image::makeFromBitmap), popup?.let(Image::makeFromBitmap), popupRect)
        }
        viewImage?.use { canvas.drawImageRect(it, Rect.makeWH(width, height)) }
        popupImage?.use { image ->
            val scale = density
            rect?.let {
                canvas.drawImageRect(
                    image,
                    Rect.makeXYWH(it.x * scale, it.y * scale, it.width * scale, it.height * scale),
                )
            }
        }
    }

    // ---- JS 桥：把 state.interfaces 里带 @JavascriptInterface 的方法暴露成 window.<名字>.<方法> ----
    private val bridgeMethods: Map<String, Map<String, java.lang.reflect.Method>> = state.interfaces.mapValues { (_, target) ->
        target.javaClass.methods
            .filter { it.isAnnotationPresent(JavascriptInterface::class.java) }
            // 上游的接口对象常是 private class（如 MermaidInterface），Android WebView 不在乎可见性，这里也放开
            .onEach { it.isAccessible = true }
            .associateBy { it.name }
    }

    private fun bridgeScript(): String = buildString {
        append("(function(){var q=window.$QUERY_FN;if(!q)return;")
        bridgeMethods.forEach { (name, methods) ->
            append("window.$name={")
            methods.keys.forEachIndexed { i, method ->
                if (i > 0) append(',')
                append("$method:function(){q({request:JSON.stringify({o:'$name',m:'$method',")
                append("a:Array.prototype.slice.call(arguments).map(function(x){return x==null?null:String(x)})})});}")
            }
            append("};")
        }
        append("})();")
    }

    private inner class BridgeHandler : CefMessageRouterHandlerAdapter() {
        override fun onQuery(
            browser: CefBrowser?,
            frame: CefFrame?,
            queryId: Long,
            request: String?,
            persistent: Boolean,
            callback: CefQueryCallback?,
        ): Boolean {
            val message = runCatching { Json.parseToJsonElement(request.orEmpty()) as JsonObject }.getOrNull()
                ?: return false
            message["e"]?.let { id ->
                val result = (message["r"] as? JsonPrimitive)?.contentOrNull
                evalCallbacks.remove(id.jsonPrimitive.int)?.let { cb -> onEdt { cb(result) } }
                callback?.success("")
                return true
            }
            val name = (message["o"] as? JsonPrimitive)?.contentOrNull
            val methodName = (message["m"] as? JsonPrimitive)?.contentOrNull
            val method = bridgeMethods[name]?.get(methodName)
            val target = state.interfaces[name]
            if (method == null || target == null) {
                callback?.failure(404, "no such interface method")
                return true
            }
            val args = (message["a"] as? JsonArray)?.map { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            if (args.size != method.parameterCount) {
                callback?.failure(400, "argument count mismatch")
                return true
            }
            onEdt { runCatching { method.invoke(target, *args.toTypedArray()) }.onFailure { it.printStackTrace() } }
            callback?.success("")
            return true
        }
    }

    private companion object {
        const val QUERY_FN = "rikkaQuery"
        const val QUERY_CANCEL_FN = "rikkaQueryCancel"
        const val DEFAULT_BASE_URL = "https://rikkahub.local/"

        val OVERVIEW_SCRIPT = """
            (function(){var m=document.querySelector('meta[name=viewport]');if(!m)return;
            var w=/width=(\d+)/.exec(m.getAttribute('content')||'');if(!w)return;w=+w[1];
            function fit(){document.documentElement.style.zoom=Math.min(1,window.innerWidth/w);}
            fit();window.addEventListener('resize',fit);})();
        """.trimIndent()

        fun onEdt(block: () -> Unit) {
            if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
        }
    }
}
