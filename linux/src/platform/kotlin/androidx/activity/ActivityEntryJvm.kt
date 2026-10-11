package androidx.activity.compose

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Android 的 `activity.setContent { ... }` 会创建 ComposeView 并挂到 Window 上；
 * 桌面端由 JB 的 Window 负责 composition，所以这里只做“记录”：
 * 窗口在渲染时取 [desktopContent] 调出来，RouteActivity 的启动流程因此原样可用。
 */
fun ComponentActivity.setContent(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    DesktopActivityContent.attach(this, content)
}

object DesktopActivityContent {
    private val contents = mutableMapOf<ComponentActivity, @Composable () -> Unit>()

    fun attach(activity: ComponentActivity, content: @Composable () -> Unit) {
        contents[activity] = content
    }

    fun contentOf(activity: ComponentActivity): (@Composable () -> Unit)? = contents[activity]
}
