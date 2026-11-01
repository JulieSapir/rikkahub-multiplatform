package androidx.compose.foundation.layout

/**
 * Android 的 foundation 有 `WindowInsets.isImeVisible` 扩展属性（读 IME inset），JetBrains 的
 * desktop 变体没有这个 API。桌面端没有软键盘，恒为 false，让依赖它的布局逻辑（比如输入面板
 * 避让）保持可编译。
 */
val WindowInsets.Companion.isImeVisible: Boolean
    get() = false
