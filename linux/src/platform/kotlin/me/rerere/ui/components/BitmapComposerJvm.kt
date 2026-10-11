package me.rerere.ui.components

import android.app.Activity
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope

/**
 * Android 端把任意 Composable 画成位图靠 ComposeView + decorView 离屏绘制，桌面端没有这套视图树。
 * 上游唯一的调用处（会话导出为图片）用 runCatching 包着，这里抛明确异常，其它导出格式不受影响。
 */
val LocalExportContext = staticCompositionLocalOf { false }

class BitmapComposer(private val mainScope: CoroutineScope) {
    suspend fun composableToBitmap(
        activity: Activity,
        width: Dp? = null,
        height: Dp? = null,
        screenDensity: Density,
        content: @Composable () -> Unit,
    ): Bitmap = throw UnsupportedOperationException("桌面端暂不支持把界面渲染成图片")
}
