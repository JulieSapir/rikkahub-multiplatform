package me.rerere.rikkahub.linux.web

import android.content.Context
import android.net.Uri
import me.rerere.ui.webview.WebViewLocalAssets
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefCallback
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceHandler
import org.cef.handler.CefResourceHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import java.io.InputStream

/**
 * 给 CEF 的请求拦截，两件事：
 * - [pages] 里登记的 URL 直接返回给定内容，相当于 Android 的 `loadDataWithBaseURL`：页面拿到真实的 origin，
 *   相对路径和 rikkahub.local 资源都能解析
 * - 其余请求交给上游的 [WebViewLocalAssets]：`https://rikkahub.local/assets/...` 映射到 assets，
 *   mermaid.min.js 等脚本走本地文件
 */
internal class LocalContentRequestHandler(
    private val context: Context,
    private val pages: Map<String, Pair<String, String>>,
) : CefRequestHandlerAdapter() {
    override fun getResourceRequestHandler(
        browser: CefBrowser?,
        frame: CefFrame?,
        request: CefRequest,
        isNavigation: Boolean,
        isDownload: Boolean,
        requestInitiator: String?,
        disableDefaultHandling: BoolRef?,
    ): CefResourceRequestHandler? {
        val url = request.url ?: return null
        pages[pageKey(url)]?.let { (mime, body) ->
            return handlerOf(StreamResponse(mime, "utf-8", body.toByteArray(Charsets.UTF_8).inputStream()))
        }
        val local = WebViewLocalAssets.intercept(context, Uri.parse(url)) ?: return null
        return handlerOf(StreamResponse(local.mimeType, local.encoding, local.data))
    }

    private fun handlerOf(response: CefResourceHandler) = object : CefResourceRequestHandlerAdapter() {
        override fun getResourceHandler(browser: CefBrowser?, frame: CefFrame?, request: CefRequest?) = response
    }
}

/** 页面登记与查找用同一个键：去掉 #片段，补上空路径的 `/`（CEF 请求的是规范化后的地址） */
internal fun pageKey(url: String): String {
    val base = url.substringBefore('#')
    val uri = runCatching { java.net.URI(base) }.getOrNull() ?: return base
    return if (uri.host != null && uri.rawPath.isNullOrEmpty()) "$base/" else base
}

/** 一次性的响应体，按 CEF 的要求分块读 */
private class StreamResponse(
    private val mime: String,
    private val encoding: String?,
    private val input: InputStream,
) : CefResourceHandlerAdapter() {
    override fun processRequest(request: CefRequest?, callback: CefCallback): Boolean {
        callback.Continue()
        return true
    }

    override fun getResponseHeaders(response: CefResponse, responseLength: IntRef, redirectUrl: StringRef?) {
        response.mimeType = mime
        response.status = 200
        encoding?.let { response.setHeaderByName("Content-Type", "$mime; charset=$it", true) }
        responseLength.set(-1)
    }

    override fun readResponse(dataOut: ByteArray, bytesToRead: Int, bytesRead: IntRef, callback: CefCallback?): Boolean {
        val n = input.read(dataOut, 0, bytesToRead)
        if (n <= 0) {
            input.close()
            bytesRead.set(0)
            return false
        }
        bytesRead.set(n)
        return true
    }

    override fun cancel() {
        input.close()
    }
}
