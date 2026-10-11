package androidx.compose.ui.platform

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Android 端 UI 代码用 LocalContext.current 拿 Context，桌面端由 AppTheme 提供同一个实例。
 */
val LocalContext = staticCompositionLocalOf<Context> {
    error("LocalContext 未提供：桌面端要在 CompositionLocalProvider 里给出 Context")
}
