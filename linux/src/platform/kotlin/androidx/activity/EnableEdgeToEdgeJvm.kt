package androidx.activity

import androidx.activity.ComponentActivity

/** edge-to-edge 是 Android 的窗口 inset 概念，桌面端窗口由系统装饰，这里什么都不做。 */
fun ComponentActivity.enableEdgeToEdge(
    statusBarStyle: Any? = null,
    navigationBarStyle: Any? = null,
) = Unit
