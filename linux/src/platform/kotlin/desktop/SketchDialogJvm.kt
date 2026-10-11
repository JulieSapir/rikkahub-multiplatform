package me.rerere.ui.sketch

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Android 端的手绘涂鸦板用 androidx.ink 做笔刷渲染（只有 Android 产物），桌面端先只提供
 * “带图确认”这一步：图片照常显示，确认时原样交回，用户仍能把图作为创作参考图提交。
 *
 * 上游 ui/sketch 整个目录在 linux/build.gradle.kts 里 exclude。
 *
 * MPTODO: use compose-stylus or port androidx.ink (currently available in x86_64 linux headless)
 */
class SketchResult internal constructor(
    val bitmap: Bitmap,
    private val photo: Boolean,
) {
    val extension: String get() = if (photo) "jpg" else "png"
    val mimeType: String get() = if (photo) "image/jpeg" else "image/png"

    fun encode(): ByteArray = java.io.ByteArrayOutputStream().also { out ->
        bitmap.compress(
            if (photo) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG,
            92,
            out,
        )
    }.toByteArray()
}

@Composable
fun SketchDialog(
    onDismiss: () -> Unit,
    onConfirm: (SketchResult) -> Unit,
    image: Uri? = null,
    aspectRatio: Float? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手绘") },
        text = {
            Box(modifier = Modifier.fillMaxSize()) {
                if (image != null) {
                    AsyncImage(
                        model = image.toString(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text(
                        text = "桌面端暂不支持在图上涂鸦",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val bitmap = image?.let { loadSketchBitmap(context, it) }
                    ?: Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                onConfirm(SketchResult(bitmap, photo = image != null))
            }) { Text("确认") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun loadSketchBitmap(context: Context, uri: Uri): Bitmap? =
    me.rerere.rikkahub.utils.ImageUtils.loadOptimizedBitmap(context, uri, 2048)
