package me.rerere.rikkahub.linux

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.richtext.SelectableContent
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.Navigator
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 离屏渲染上游的 MarkdownBlock（bettertext 管线）：代码高亮、行内/块级公式、引用角标、颜色色块都走一遍，
 * 确认桌面端的 BetterText / Canvas / 字体替身能真正画出东西而不是只编得过。
 * 渲染结果写到 build/markdown-render.png 方便目测。
 */
class MarkdownRenderTest {
    @Test
    fun rendersUpstreamMarkdown() {
        val markdown = """
            # 标题 Heading
            普通文字 **加粗** *斜体* `code` 与链接 [rikka](https://rikka-ai.com)，颜色 #FF5722 前有色块。

            行内公式 ${'$'}E = mc^2${'$'} 和引用 [citation,example.com](abc123)。

            ${'$'}${'$'}\int_0^1 x^2\,dx = \frac{1}{3}${'$'}${'$'}

            ```kotlin
            fun main() = println("hello")
            ```

            - 列表一
            - 列表二

            | a | b |
            |---|---|
            | 1 | 2 |
        """.trimIndent()

        val image = ImageComposeScene(width = 900, height = 900, density = Density(1.5f)) {
            CompositionLocalProvider(
                LocalContext provides Context(),
                LocalSettings provides Settings(),
                LocalNavController provides Navigator(mutableListOf()),
            ) {
                MaterialTheme {
                    Surface(Modifier.background(Color.White)) {
                        SelectableContent {
                            MarkdownBlock(content = markdown, modifier = Modifier.width(560.dp).padding(12.dp))
                        }
                    }
                }
            }
        }.use { scene -> scene.render() }

        val png = requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes
        File("build/markdown-render.png").writeBytes(png)

        // 至少要有一定比例的非白像素：空白或崩溃回退都过不了
        val bitmap = org.jetbrains.skia.Bitmap.makeFromImage(image)
        var inked = 0
        for (y in 0 until bitmap.height step 3) {
            for (x in 0 until bitmap.width step 3) {
                if (bitmap.getColor(x, y) != -1) inked++
            }
        }
        assertTrue("渲染结果几乎是空白: $inked", inked > 2000)
    }
}
