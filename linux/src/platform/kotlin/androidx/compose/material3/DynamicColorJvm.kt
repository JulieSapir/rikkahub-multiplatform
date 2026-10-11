package androidx.compose.material3

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Material You 取的是壁纸调色板，Linux 上没有这个概念。
 *
 * 上游 Theme.kt 在 settings.dynamicColor 打开时会调它；这里退回 baseline 配色，用户仍然可以在
 * 设置里选预设主题或自定义主题色（那条路径走 me.rerere.material3 的 HCT 实现，是跨平台的）。
 */
@Composable
@ReadOnlyComposable
fun dynamicDarkColorScheme(context: Context): ColorScheme = darkColorScheme()

@Composable
@ReadOnlyComposable
fun dynamicLightColorScheme(context: Context): ColorScheme = lightColorScheme()
