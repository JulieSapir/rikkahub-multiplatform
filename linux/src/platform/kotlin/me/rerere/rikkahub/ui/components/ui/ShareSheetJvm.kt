package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.serialization.encodeToString
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.saveTextFile
import me.rerere.rikkahub.utils.writeClipboardText
import kotlin.io.encoding.Base64

/**
 * ShareSheet 的桌面端版本：Android 用 ModalBottomSheet + 系统分享/二维码，
 * 桌面端改成对话框，提供“复制到剪贴板”和“保存成文件”。
 *
 * 编码格式与 Android 端完全一致（ai-provider:v1: + base64(JSON)），所以两端可以互传配置。
 */
class ShareSheetState {
    private var show by mutableStateOf(false)
    val isShow get() = show

    private var provider by mutableStateOf<ProviderSetting?>(null)
    val currentProvider get() = provider

    fun show(provider: ProviderSetting) {
        this.show = true
        this.provider = provider
    }

    fun dismiss() {
        this.show = false
    }
}

@Composable
fun rememberShareSheetState(): ShareSheetState = remember { ShareSheetState() }

@Composable
fun ShareSheet(state: ShareSheetState) {
    val context = LocalContext.current
    if (!state.isShow) return
    val provider = state.currentProvider ?: return
    val encoded = remember(provider) { provider.encodeForShare() }

    AlertDialog(
        onDismissRequest = { state.dismiss() },
        title = { Text("分享提供商配置") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    text = encoded,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "同一份格式在 Android 端可以扫码或粘贴导入。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { context.writeClipboardText(encoded) }) {
                Text("复制")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = {
                context.saveTextFile(encoded, "${provider.name}.rikka")
            }) { Text("保存为文件") }
        },
    )
}

/** 与 Android 端同一套编码：ai-provider:v1: + base64(去掉 models 的 JSON) */
fun ProviderSetting.encodeForShare(): String = buildString {
    append("ai-provider:")
    append("v1:")
    val value = JsonInstant.encodeToString(this@encodeForShare.copyProvider(models = emptyList()))
    append(Base64.encode(value.encodeToByteArray()))
}

fun decodeProviderSetting(value: String): ProviderSetting {
    require(value.startsWith("ai-provider:v1:")) { "Invalid provider setting string" }
    val base64Str = value.removePrefix("ai-provider:v1:")
    val jsonStr = Base64.decode(base64Str).decodeToString()
    return JsonInstant.decodeFromString<ProviderSetting>(jsonStr)
}
