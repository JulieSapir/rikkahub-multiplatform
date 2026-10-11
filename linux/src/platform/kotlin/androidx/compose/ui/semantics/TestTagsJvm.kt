package androidx.compose.ui.semantics

import androidx.compose.ui.Modifier

/**
 * Android 用 testTagsAsResourceId 把测试标签映射到 View 的 resource-id（UI 自动化用）。
 * 桌面端没有那套：属性写入被忽略，上游的 semantics 块照原样编译。
 */
var SemanticsPropertyReceiver.testTagsAsResourceId: Boolean
    get() = false
    set(_) = Unit

fun Modifier.testTagsAsResourceId(value: String): Modifier = this
