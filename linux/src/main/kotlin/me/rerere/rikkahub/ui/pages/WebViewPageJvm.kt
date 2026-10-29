package me.rerere.rikkahub.ui.pages.webview

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.rerere.common.android.appTempFolder
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.ui.webview.WebViewContentCache
import java.awt.Desktop
import java.io.File
import java.net.URI

/**
 * 上游这个页面内嵌 Android WebView 渲染 Markdown 预览（marked + katex + mermaid + highlight.js）。
 * 桌面端没有 WebView 组件，改成把同一份 HTML 落到缓存目录并用系统默认浏览器打开：
 * 数学公式、mermaid 图与高亮在浏览器里都是完整渲染的，比在 Compose 里重新实现一套更准。
 */
@Composable
fun WebViewPage(url: String, contentId: String) {
    val context = LocalContext.current
    val file = remember(contentId, url) {
        when {
            contentId.isNotEmpty() -> WebViewContentCache.load(context.cacheDir, contentId)
                ?.let { writeHtmlFile(context, it) }
            else -> null
        }
    }

    Scaffold(
        topBar = { BackButton() },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (url.isNotEmpty() || file != null) "在系统浏览器中打开预览" else "没有可预览的内容",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = file?.absolutePath ?: url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { openExternally(context, url, file) }) {
                Text("打开")
            }
        }
    }
}

private fun writeHtmlFile(context: Context, html: String): File? = runCatching {
    // 预览页是一次性的，落进 appTempFolder（启动时清空），不能在 cacheDir 只写不清
    val target = File(context.appTempFolder, "preview_${System.nanoTime()}.html")
    target.writeText(html, Charsets.UTF_8)
    target
}.getOrNull()

private fun openExternally(context: Context, url: String, file: File?) {
    val uri = when {
        url.isNotEmpty() -> runCatching { URI(url) }.getOrNull()
        else -> file?.toURI()
    } ?: return
    val opened = runCatching { Desktop.getDesktop().browse(uri) }.isSuccess
    if (!opened) context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri.toString())))
}
