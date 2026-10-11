package androidx.core.view

import android.app.Window
import android.view.View

/**
 * 系统栏着色在桌面端没有对应能力，这里给一个只记录状态、不产生副作用的实现。
 */
object WindowCompat {
    fun getInsetsController(window: Window, view: View): WindowInsetsControllerCompat =
        WindowInsetsControllerCompat()
}

class WindowInsetsControllerCompat {
    var isAppearanceLightStatusBars: Boolean = false
    var isAppearanceLightNavigationBars: Boolean = false
}
