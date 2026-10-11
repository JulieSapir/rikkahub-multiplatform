package me.rerere.rikkahub.ui.components.ui

import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Android 版在“应用图标风格加载指示器”打开时放一只 AVD 兔子（AnimatedVectorDrawable + AndroidView），
 * 这两样在桌面端都不存在；这里统一用 MD3 表现型的 ContainedLoadingIndicator，
 * 也即 Android 端关掉该设置时看到的同一个控件。
 */
@Composable
fun RabbitLoadingIndicator(modifier: Modifier = Modifier) {
    ContainedLoadingIndicator(modifier = modifier)
}
