package androidx.compose.foundation

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.Modifier

/**
 * Android 的 `Modifier.systemGestureExclusion(block)` 把指定区域从系统手势（返回手势条）里排除，
 * 桌面端没有系统手势区，按无操作处理，签名与 foundation 一致（ImageCropper 的裁剪框手柄用它）。
 */
fun Modifier.systemGestureExclusion(block: (LayoutCoordinates) -> Rect): Modifier = this
