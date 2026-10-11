package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * Android 端用 media3/ExoPlayer 内嵌播放，桌面端没有对应组件：
 * 同一个对话框入口，确认后用系统默认播放器打开文件（解码与倍速都交给播放器）。
 */
@Composable
fun VideoPlayerDialog(
    file: File,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("播放视频") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(file.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "桌面端交给系统播放器打开，播放进度与倍速由播放器提供。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching { java.awt.Desktop.getDesktop().open(file) }.onFailure { it.printStackTrace() }
            }) { Text("播放") }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text("关闭") }
        },
    )
}
