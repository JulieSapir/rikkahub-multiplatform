package io.github.g00fy2.quickie

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract

/**
 * quickie 是 Android 的扫码库（CameraX + ML Kit），桌面端没有相机链路：
 * 契约保留，结果固定为 Failure，提供商页的“扫码填 Key”按钮点了会提示失败，
 * 用户仍可手工粘贴，另一条“从图片识别二维码”的入口走 ImageUtils.decodeQRCodeFromUri。
 *
 * MPTODO: add Kamera
 */
sealed class QRResult {
    data class QRSuccess(val content: QRContent) : QRResult()
    data object QRUserCanceled : QRResult()
    data object QRMissingPermission : QRResult()
    data class QRError(val exception: Exception?) : QRResult()
}

class ScanQRCode : ActivityResultContract<Unit?, QRResult> {
    override fun execute(context: Context, input: Unit?, callback: (QRResult) -> Unit) {
        callback(QRResult.QRError(IllegalStateException("桌面端没有相机扫码")))
    }
}

/** quickie 把扫码结果包在一层 QRContent 里，只用到 rawValue。 */
data class QRContent(val rawValue: String?)
