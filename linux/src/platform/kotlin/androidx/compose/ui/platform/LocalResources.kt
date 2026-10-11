package androidx.compose.ui.platform

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 新版 androidx 提供的 LocalResources（给非 Composable 上下文取字符串资源），JetBrains 移植里还没有。
 *
 * 上游只用到 resources.getString(R.string.x)，这里给一个绑定到平台 Context 的实现。
 */
val LocalResources = staticCompositionLocalOf<Resources> { Resources(Context()) }
