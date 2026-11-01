package me.rerere.rikkahub.ui.components.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 上游的扫码页是 CameraX + ML Kit 的 Compose 取景器（原文件在 build.gradle.kts 里 exclude）。
 * 桌面端复用 fork 已有的 webcam-capture 契约（QuickieJvm 的 ScanQRCode，Swing 实时取景 + zxing 识别）：
 * 进入组合即打开扫码窗口，拿到结果后按语义分流——识别成功给 [onResult]，
 * 用户关窗给 [onDismissRequest]，无相机/打不开设备视为权限被拒给 [onPermissionDenied]。
 */
@Composable
fun QRCodeScannerDialog(
    onResult: (String) -> Unit,
    onPermissionDenied: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val result = suspendCancellableCoroutine { continuation ->
            ScanQRCode().execute(context, Unit) { continuation.resume(it) }
        }
        when (result) {
            is QRResult.QRSuccess -> onResult(result.content.rawValue.orEmpty())
            QRResult.QRUserCanceled -> onDismissRequest()
            else -> onPermissionDenied()
        }
    }
}
