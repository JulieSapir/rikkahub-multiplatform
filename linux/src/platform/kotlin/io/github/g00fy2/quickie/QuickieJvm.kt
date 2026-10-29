package io.github.g00fy2.quickie

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import com.github.sarxos.webcam.Webcam
import com.github.sarxos.webcam.WebcamPanel
import com.github.sarxos.webcam.WebcamResolution
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.awt.BorderLayout
import java.awt.GraphicsEnvironment
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext

sealed class QRResult {
    data class QRSuccess(val content: QRContent) : QRResult()
    data object QRUserCanceled : QRResult()
    data object QRMissingPermission : QRResult()
    data class QRError(val exception: Exception?) : QRResult()
}

/** quickie 把扫码结果包在一层 QRContent 里，只用到 rawValue。 */
data class QRContent(val rawValue: String?)

/**
 * quickie 的扫码契约在桌面端用 webcam-capture 起本地相机：Swing 窗口实时预览，后台线程每
 * 300ms 取一帧喂给 zxing（与 ImageUtils.decodeQRCodeFromBitmap 同一条识别链路）。
 *
 * Wayland / 部分 v4l2 环境下 webcam-capture 可能枚举不到设备或打不开，属预期降级：所有失败
 * （headless、无设备、open 失败、未捕获异常）都折成 QRError 回调，绝不让调用方等不到结果。
 */
class ScanQRCode : ActivityResultContract<Unit?, QRResult> {

    override fun execute(context: Context, input: Unit?, callback: (QRResult) -> Unit) {
        // 解码成功 / 用户取消 / 异常三条路可能同时到达，callback 只允许调一次
        val delivered = AtomicBoolean()
        // 结果统一回到 EDT 再回调，与文件选择等其它契约的回调线程一致（Compose 主线程就是 EDT）
        fun deliver(result: QRResult) {
            if (delivered.compareAndSet(false, true)) {
                SwingUtilities.invokeLater { callback(result) }
            }
        }

        if (GraphicsEnvironment.isHeadless()) {
            deliver(QRResult.QRError(IllegalStateException("当前是无显示环境，无法打开相机扫码")))
            return
        }

        scanScope.launch(Dispatchers.IO) {
            var frame: JFrame? = null
            var panel: WebcamPanel? = null
            var webcam: Webcam? = null
            try {
                val webcams = runCatching { Webcam.getWebcams() }.getOrDefault(emptyList())
                val camera = webcams.firstOrNull()
                if (camera == null) {
                    deliver(QRResult.QRError(IllegalStateException("未找到可用的相机设备")))
                    return@launch
                }
                webcam = camera
                // 默认分辨率可能太小解不出二维码；相机不支持 VGA 时保留默认值
                runCatching { webcam.setViewSize(WebcamResolution.VGA.size) }
                webcam.open()

                val closed = AtomicBoolean(false)
                withContext(Dispatchers.Swing) {
                    val preview = WebcamPanel(webcam)
                    panel = preview
                    frame = JFrame("扫描二维码").apply {
                        defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
                        layout = BorderLayout()
                        add(preview, BorderLayout.CENTER)
                        add(
                            JButton("取消").apply {
                                addActionListener {
                                    closed.set(true)
                                    deliver(QRResult.QRUserCanceled)
                                }
                            },
                            BorderLayout.SOUTH,
                        )
                        addWindowListener(object : WindowAdapter() {
                            override fun windowClosing(e: WindowEvent) {
                                closed.set(true)
                                deliver(QRResult.QRUserCanceled)
                            }
                        })
                        pack()
                        setLocationRelativeTo(null)
                        isVisible = true
                    }
                }

                while (!closed.get()) {
                    val image = runCatching { webcam.image }.getOrNull()
                    if (image != null) {
                        decodeQr(image)?.let { text ->
                            deliver(QRResult.QRSuccess(QRContent(text)))
                            return@launch
                        }
                    }
                    Thread.sleep(SCAN_INTERVAL_MS)
                }
            } catch (t: Throwable) {
                // Throwable 而非 Exception：原生库缺失这类 Error 也要折成回调，不能让调用方干等
                deliver(QRResult.QRError(IllegalStateException("相机扫码失败: ${t.message}", t)))
            } finally {
                SwingUtilities.invokeLater {
                    runCatching { panel?.stop() }
                    frame?.dispose()
                    runCatching { webcam?.close() }
                }
            }
        }
    }

    /** 像素数组 → RGBLuminanceSource → HybridBinarizer → MultiFormatReader，与 Android 端一致 */
    private fun decodeQr(image: BufferedImage): String? = runCatching {
        val width = image.width
        val height = image.height
        if (width <= 0 || height <= 0) return null
        val pixels = IntArray(width * height)
        image.getRGB(0, 0, width, height, pixels, 0, width)

        val reader = MultiFormatReader()
        reader.setHints(mapOf(DecodeHintType.TRY_HARDER to true))
        reader.decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels)))).text
    }.getOrNull()

    companion object {
        private const val SCAN_INTERVAL_MS = 300L

        private val scanScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
