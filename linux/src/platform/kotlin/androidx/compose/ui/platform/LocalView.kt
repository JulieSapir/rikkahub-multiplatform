package androidx.compose.ui.platform

import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf

/** 桌面端没有真正的 View，给一个默认实例，状态栏着色那段逻辑会因为它而跳过 */
val LocalView = staticCompositionLocalOf<View> { View() }
