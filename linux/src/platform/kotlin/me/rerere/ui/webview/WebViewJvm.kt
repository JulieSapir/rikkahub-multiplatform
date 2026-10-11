package me.rerere.ui.webview

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import java.io.File
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Android WebView 的桌面端替身。
 *
 * 上游只有两处内嵌预览用到它：代码块的 HTML/SVG 预览，与工作区文件的网页预览。
 * 桌面端按内容类型分流——SVG 直接交给 Coil 的 SVG 解码器画出来；HTML 没有渲染引擎，
 * 就显示源码并给一个“在浏览器中打开”的按钮（写临时文件后用系统默认浏览器打开，
 * marked/katex/mermaid 那套预览页在浏览器里是完整的）。
 */
class WebSettings {
    var javaScriptEnabled: Boolean = false
    var domStorageEnabled: Boolean = false
    var builtInZoomControls: Boolean = false
    var displayZoomControls: Boolean = false
    var useWideViewPort: Boolean = false
    var loadWithOverviewMode: Boolean = false
    var allowFileAccess: Boolean = false
    var allowContentAccess: Boolean = false
    var allowUniversalAccessFromFileURLs: Boolean = false
}

sealed class WebContent {
    data object NavigatorOnly : WebContent()

    data class Url(
        val url: String,
        val additionalHttpHeaders: Map<String, String> = emptyMap(),
    ) : WebContent()

    data class Data(
        val data: String,
        val baseUrl: String? = null,
        val encoding: String = "utf-8",
        val mimeType: String? = null,
        val historyUrl: String? = null,
    ) : WebContent()
}

class WebViewState(
    initialContent: WebContent = WebContent.NavigatorOnly,
    val interfaces: Map<String, Any> = emptyMap(),
    val settings: WebSettings.() -> Unit = {},
) {
    var content: WebContent by mutableStateOf(initialContent)

    val canGoBack: Boolean get() = false

    fun reload() = Unit
}

@Composable
fun rememberWebViewState(
    url: String = "about:blank",
    additionalHttpHeaders: Map<String, String> = emptyMap(),
    interfaces: Map<String, Any> = emptyMap(),
    settings: WebSettings.() -> Unit = {},
): WebViewState = remember(url) {
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
): WebViewState = remember(data, baseUrl, mimeType) {
    WebViewState(WebContent.Data(data, baseUrl, encoding, mimeType, historyUrl), interfaces, settings)
}

@Composable
fun WebView(
    state: WebViewState,
    modifier: Modifier = Modifier,
    onCreated: (WebViewState) -> Unit = {},
    onUpdated: (WebViewState) -> Unit = {},
) {
    val context = LocalContext.current
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp)) {
        when (val content = state.content) {
            is WebContent.Data -> {
                val svg = content.mimeType?.contains("svg") == true
                if (svg) {
                    AsyncImage(
                        model = "data:image/svg+xml;base64," + java.util.Base64.getEncoder().encodeToString(content.data.encodeToByteArray()),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = content.data,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        OutlinedButton(onClick = { openInBrowser(context, content.data) }) {
                            Text("在浏览器中打开")
                        }
                    }
                }
            }

            is WebContent.Url -> Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(content.url, style = MaterialTheme.typography.bodySmall)
                }
            }

            WebContent.NavigatorOnly -> Unit
        }
    }
}

private fun base64(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)

private fun openInBrowser(context: android.content.Context, html: String) {
    val file = File(context.cacheDir, "webview_preview_${html.hashCode()}.html").apply { writeText(html) }
    val opened = runCatching { java.awt.Desktop.getDesktop().browse(file.toURI()) }.isSuccess
    if (!opened) {
        context.startActivity(
            android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                Uri.fromFile(file),
            ),
        )
    }
}

private fun Modifier.clipNoop(): Modifier = this
